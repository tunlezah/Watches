package io.github.tunlezah.watches.data

import androidx.room.execSQL
import androidx.room.useWriterConnection
import io.github.tunlezah.watches.core.model.NormalizedRect
import io.github.tunlezah.watches.core.model.ServiceEntry
import io.github.tunlezah.watches.core.model.ServiceWork
import io.github.tunlezah.watches.core.model.Watch
import io.github.tunlezah.watches.core.settings.Accent
import io.github.tunlezah.watches.data.backup.BackupException
import io.github.tunlezah.watches.data.backup.CsvExporter
import io.github.tunlezah.watches.data.backup.RestoreMode
import io.github.tunlezah.watches.data.backup.ZipStreamReader
import io.github.tunlezah.watches.data.backup.ZipWriter
import io.github.tunlezah.watches.data.db.SearchIndex
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Backup, restore and export on the platform's own database, files and image code: the JVM and the
 * iOS simulator. ArchiveInteropTest (JVM only) checks the archives against java.util.zip as well.
 */
class BackupRepositoryTest {

    private val phones = TestPhones()
    private val now: Instant get() = phones.now

    @AfterTest
    fun tearDown() = phones.close()

    private fun jpeg(seed: Int) = phones.jpeg(seed)

    private fun source(file: Path) = FileSystem.SYSTEM.source(file)

    @Test fun exportWipeImportRestoresEverythingExactly() = runBlocking<Unit> {
        val source = phones.new()
        source.seed()
        val before = source.everything()
        val archive = source.archive()

        val target = phones.new()
        val preview = target.backup.inspect { source(archive) }
        assertEquals(2, preview.counts.watches)
        assertEquals(3, preview.counts.photos)
        assertEquals(0, preview.watchesOnPhone)
        assertTrue(preview.warnings.isEmpty(), preview.warnings.toString())
        val report = target.backup.restore({ source(archive) }, preview, RestoreMode.REPLACE)
        assertEquals(2, report.watchesAdded)

        assertEquals(before, target.everything(), "every row of every table comes back as it was")
        assertEquals(source.mediaFiles(), target.mediaFiles(), "every file comes back, byte for byte, at the same path")
        assertEquals(setOf(before.watch.first().id), target.watches.search("mainspring"), "the search index is rebuilt")
        assertEquals(Accent.BORDEAUX, target.settings.current().accent)
    }

    @Test fun restoringOntoTheSamePhoneReusesFilesAndChangesNothing() = runBlocking<Unit> {
        val phone = phones.new()
        phone.seed()
        val before = phone.everything()
        val files = phone.mediaFiles()
        val archive = phone.archive()
        val preview = phone.backup.inspect { source(archive) }
        assertEquals(2, preview.merge.unchanged)
        phone.backup.restore({ source(archive) }, preview, RestoreMode.REPLACE)
        assertEquals(before, phone.everything())
        assertEquals(files, phone.mediaFiles(), "no copies, no leftovers")
    }

    @Test fun replaceRemovesWhatTheBackupDoesNotHave() = runBlocking<Unit> {
        val phone = phones.new()
        val (seamaster) = phone.seed()
        val archive = phone.archive()
        val extra = phone.watches.create(Watch(id = "", createdAt = now, updatedAt = now, brand = "Tudor", modelName = "Black Bay"))
        phone.photos.add(extra.id, jpeg(9))
        phone.watches.delete(seamaster.id)

        val preview = phone.backup.inspect { source(archive) }
        val report = phone.backup.restore({ source(archive) }, preview, RestoreMode.REPLACE)
        assertEquals(2, report.watchesRemoved)
        val ids = phone.everything().watch.map { it.id }.toSet()
        assertTrue(seamaster.id in ids && extra.id !in ids)
        assertTrue(phone.mediaFiles().keys.none { extra.id in it }, "the removed watch's photos are cleaned up")
    }

    @Test fun mergeKeepsTheNewerSideAndCombinesHistories() = runBlocking<Unit> {
        val phone = phones.new()
        val (seamaster, speedy) = phone.seed()
        val laptop = phones.new()
        val archiveFromPhone = phone.archive()
        laptop.backup.restore({ source(archiveFromPhone) }, laptop.backup.inspect { source(archiveFromPhone) }, RestoreMode.REPLACE)

        // On the other install: edit the Speedmaster later, add a watch and a service.
        laptop.watches.update(speedy.copy(nickname = "Moonwatch", updatedAt = now))
        val tudor = laptop.watches.create(Watch(id = "", createdAt = now, updatedAt = now, brand = "Tudor", modelName = "Pelagos"))
        laptop.photos.add(tudor.id, jpeg(7))
        laptop.services.save(ServiceEntry("", seamaster.id, LocalDate(2025, 3, 1), "Local watchmaker", setOf(ServiceWork.REGULATION), null, null, null, now, now))
        val fromLaptop = laptop.archive()
        // Meanwhile on the phone: the Seamaster is edited, after the laptop's copy was made.
        phone.watches.update(phone.watches.get(seamaster.id)!!.copy(nickname = "Daily"))

        val preview = phone.backup.inspect { source(fromLaptop) }
        assertEquals(listOf("Tudor Pelagos"), preview.merge.added.map { it.title })
        assertEquals(listOf(speedy.id), preview.merge.updated.map { it.id })
        assertEquals(listOf(seamaster.id), preview.merge.kept.map { it.id })
        assertEquals(1, preview.merge.serviceEntriesAdded)

        val report = phone.backup.restore({ source(fromLaptop) }, preview, RestoreMode.MERGE)
        assertEquals(2, report.updated.size + report.kept.size, "both changed watches are reported")
        assertEquals("Moonwatch", phone.watches.get(speedy.id)!!.nickname, "the newer edit from the backup wins")
        assertEquals("Daily", phone.watches.get(seamaster.id)!!.nickname, "the newer edit on the phone wins")
        assertEquals(2, phone.services.observe(seamaster.id).first().size, "service histories are combined")
        assertEquals(1, phone.photos.forWatch(tudor.id).size)
        assertTrue(phone.mediaFiles().keys.any { tudor.id in it })
    }

    @Test fun aDamagedArchiveChangesNothing() = runBlocking<Unit> {
        val phone = phones.new()
        phone.seed()
        val archive = phone.archive()
        val target = phones.new()
        target.seed()
        val before = target.everything()
        val files = target.mediaFiles()

        // Flip one byte inside the last stored file (a snapshot or photo), well past the headers.
        val bytes = FileSystem.SYSTEM.read(archive) { readByteArray() }
        val last = entries(archive).map { it.first }.last { it.startsWith("files/") }
        val at = findDataOffset(bytes, last) + 2
        bytes[at] = (bytes[at] + 1).toByte()
        val damaged = target.dir / "damaged.zip"
        FileSystem.SYSTEM.write(damaged) { write(bytes) }

        val preview = target.backup.inspect { source(damaged) }
        assertFailsWith<BackupException.Damaged> { target.backup.restore({ source(damaged) }, preview, RestoreMode.REPLACE) }
        assertEquals(before, target.everything())
        assertEquals(files, target.mediaFiles(), "no stray files are left behind")
    }

    @Test fun otherFilesAreRefusedClearly() = runBlocking<Unit> {
        val phone = phones.new()
        val notZip = phone.dir / "notes.zip"
        FileSystem.SYSTEM.write(notZip) { writeUtf8("hello") }
        assertFailsWith<BackupException.NotABackup> { phone.backup.inspect { source(notZip) } }

        val otherZip = phone.dir / "photos.zip"
        writeZip(otherZip, listOf("photo.jpg" to jpeg(1)))
        assertFailsWith<BackupException.NotABackup> { phone.backup.inspect { source(otherZip) } }

        phone.seed()
        val newer = phone.dir / "newer.zip"
        writeZip(newer, entries(phone.archive()).map { (name, data) ->
            name to if (name.endsWith("manifest.json")) data.decodeToString().replace("\"format_version\": 1", "\"format_version\": 99").encodeToByteArray() else data
        })
        assertFailsWith<BackupException.TooNew> { phone.backup.inspect { source(newer) } }
    }

    @Test fun anInterruptedRestoreIsUndoneAtNextLaunch() = runBlocking<Unit> {
        val phone = phones.new()
        phone.seed()
        val kept = phone.mediaFiles().keys.first()
        val stray = "photos/abc/stray.jpg"
        phone.files.write(stray, jpeg(5))
        phone.files.write("restore-journal.txt", "$stray\n$kept\n".encodeToByteArray())
        phone.backup.recoverInterruptedRestore()
        assertTrue(stray !in phone.mediaFiles())
        assertTrue(kept in phone.mediaFiles(), "files the database uses are never removed")
        assertTrue(!phone.files.exists("restore-journal.txt"))
    }

    @Test fun onlyFilesTheRestoreStoppedUsingAreRemoved() = runBlocking<Unit> {
        val phone = phones.new()
        val (seamaster) = phone.seed()
        val archive = phone.archive()
        // A photo being added while the restore runs: its file is written before its row.
        val importing = "photos/${seamaster.id}/arriving-original.jpg"
        phone.files.write(importing, jpeg(7))
        val replaced = phone.everything().photo.first()
        phone.photos.crop(replaced.id, NormalizedRect(0.2f, 0.2f, 0.8f, 0.8f))

        phone.backup.restore({ source(archive) }, phone.backup.inspect { source(archive) }, RestoreMode.REPLACE)
        val files = phone.mediaFiles().keys
        assertTrue(importing in files, "a file no row used before the restore isn't the restore's to remove")
        assertTrue(replaced.displayFile in files, "the backup's crop is back")
        assertTrue(phone.everything().photo.none { it.displayFile !in files || it.thumbFile !in files })
        assertEquals(phone.everything().referencedFiles + importing, files, "the crop the restore replaced is gone")
        assertTrue(!phone.files.exists("restore-journal.txt") && !phone.files.exists("restore-aftercare"))
    }

    @Test fun aRestoreStoppedAfterSavingIsFinishedAtNextLaunch() = runBlocking<Unit> {
        val phone = phones.new()
        val (seamaster) = phone.seed()
        // Saved, but the app stopped before the search index and renditions were redone.
        phone.db.useWriterConnection { it.execSQL("DELETE FROM ${SearchIndex.TABLE}") }
        val photo = phone.everything().photo.first()
        phone.db.backupDao().upsertPhotos(listOf(photo.copy(displayFile = photo.originalFile, thumbFile = photo.originalFile)))
        phone.files.write("restore-aftercare", ByteArray(0))

        phone.backup.recoverInterruptedRestore()
        assertEquals(setOf(seamaster.id), phone.watches.search("mainspring"))
        val redone = phone.everything().photo.first { it.id == photo.id }
        assertTrue(redone.displayFile != redone.originalFile && phone.files.exists(redone.displayFile))
        assertTrue(phone.files.exists(redone.originalFile), "the original is kept")
        assertTrue(!phone.files.exists("restore-aftercare"))
    }

    @Test fun croppingARestoredPhotoWithoutRenditionsKeepsItsOriginal() = runBlocking<Unit> {
        val phone = phones.new()
        phone.seed()
        val photo = phone.everything().photo.first()
        phone.db.backupDao().upsertPhotos(listOf(photo.copy(displayFile = photo.originalFile, thumbFile = photo.originalFile)))
        phone.photos.crop(photo.id, NormalizedRect(0.1f, 0.1f, 0.9f, 0.9f))
        assertTrue(phone.files.exists(photo.originalFile))
    }

    @Test fun csvHasOneRowPerWatchAndIsSpreadsheetSafe() = runBlocking<Unit> {
        val phone = phones.new()
        val (seamaster) = phone.seed()
        phone.watches.update(phone.watches.get(seamaster.id)!!.copy(nickname = "=HYPERLINK(\"x\")", privateNotes = "Line one, with a comma\nLine \"two\""))
        val out = phone.dir / "watches.csv"
        val rows = CsvExporter(phone.db, phones.clock).export(FileSystem.SYSTEM.sink(out))
        assertEquals(2, rows)
        val bytes = FileSystem.SYSTEM.read(out) { readByteArray() }
        assertEquals(listOf(0xEF, 0xBB, 0xBF), bytes.take(3).map { it.toInt() and 0xFF }, "UTF-8 byte-order mark")
        val records = parseCsv(bytes.copyOfRange(3, bytes.size).decodeToString())
        assertEquals(3, records.size)
        val header = records[0]
        assertEquals(header.size, header.toSet().size, "column names are unique")
        assertTrue(records.all { it.size == header.size }, "every row has every column")
        val row = records.first { it[0] == seamaster.id }.let { r -> header.zip(r).toMap() }
        assertEquals("41", row["Diameter (mm)"])
        assertEquals("10900.00", row["Price paid"])
        assertEquals("AUD", row["Price paid currency"])
        assertEquals("Date; Helium valve", row["Complications"])
        assertEquals("'=HYPERLINK(\"x\")", row["Nickname"], "formulas are neutralised")
        assertEquals("Line one, with a comma\nLine \"two\"", row["Private notes"])
        assertEquals("2024-02-01", row["Last full service"])
        assertEquals("2029-02-01", row["Next service due"])
        assertEquals("1 present, 1 missing", row["Box & papers"])
        assertTrue("Case: Material" in header && "Strap & bracelet: Material" in header)
    }

    /** Every entry of an archive, in order, read with the app's own reader. */
    private fun entries(archive: Path): List<Pair<String, ByteArray>> {
        val reader = ZipStreamReader(source(archive))
        try {
            return generateSequence { reader.next() }.map { it.name to reader.readBytes(it) }.toList()
        } finally {
            reader.close()
        }
    }

    private fun writeZip(to: Path, entries: List<Pair<String, ByteArray>>) {
        FileSystem.SYSTEM.sink(to).buffer().use { sink ->
            val zip = ZipWriter(sink)
            for ((name, data) in entries) zip.add(name, data)
            zip.finish()
        }
    }

    /** A strict RFC 4180 reader, so the test checks the quoting rather than trusting it. */
    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var i = 0
        var quoted = false
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                quoted && c == '"' -> quoted = false
                quoted -> cell.append(c)
                c == '"' -> quoted = true
                c == ',' -> { row += cell.toString(); cell.clear() }
                c == '\r' && i + 1 < text.length && text[i + 1] == '\n' -> { row += cell.toString(); cell.clear(); rows += row; row = mutableListOf(); i++ }
                else -> cell.append(c)
            }
            i++
        }
        return rows
    }

    private fun findDataOffset(bytes: ByteArray, name: String): Int {
        val needle = name.encodeToByteArray()
        outer@ for (i in 30 until bytes.size - needle.size) {
            for (j in needle.indices) if (bytes[i + j] != needle[j]) continue@outer
            // Local header: name starts 30 bytes in; the extra field length is at offset 28.
            val extra = (bytes[i - 2].toInt() and 0xFF) or ((bytes[i - 1].toInt() and 0xFF) shl 8)
            return i + needle.size + extra
        }
        error("$name not found")
    }
}
