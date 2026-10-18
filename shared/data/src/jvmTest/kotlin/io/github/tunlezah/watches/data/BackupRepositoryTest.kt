package io.github.tunlezah.watches.data

import androidx.room.Room
import io.github.tunlezah.watches.core.TimeSource
import io.github.tunlezah.watches.core.lookup.RawPayload
import io.github.tunlezah.watches.core.lookup.SourceRecord
import io.github.tunlezah.watches.core.model.BoxItem
import io.github.tunlezah.watches.core.model.BoxItemState
import io.github.tunlezah.watches.core.model.DocumentKind
import io.github.tunlezah.watches.core.model.Money
import io.github.tunlezah.watches.core.model.NormalizedRect
import io.github.tunlezah.watches.core.model.OtherSpec
import io.github.tunlezah.watches.core.model.Provenance
import io.github.tunlezah.watches.core.model.ServiceEntry
import io.github.tunlezah.watches.core.model.ServiceWork
import io.github.tunlezah.watches.core.model.Watch
import io.github.tunlezah.watches.core.settings.Accent
import io.github.tunlezah.watches.data.backup.AppInfo
import io.github.tunlezah.watches.data.backup.BackupException
import io.github.tunlezah.watches.data.backup.BackupRepository
import io.github.tunlezah.watches.data.backup.CollectionData
import io.github.tunlezah.watches.data.backup.RestoreMode
import io.github.tunlezah.watches.data.db.AppDatabase
import io.github.tunlezah.watches.data.db.configure
import io.github.tunlezah.watches.data.files.FileStore
import io.github.tunlezah.watches.data.files.JvmImageProcessor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.Path.Companion.toOkioPath
import okio.buffer
import okio.sink
import okio.source
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class BackupRepositoryTest {

    private val dirs = mutableListOf<File>()
    private val phones = mutableListOf<Phone>()
    private var now = Instant.fromEpochMilliseconds(1_790_000_000_000)
    private val clock = TimeSource { now.also { now += 1.seconds } }

    /** One installation: its own database and files folder. */
    private inner class Phone {
        val dir: File = Files.createTempDirectory("watches-backup").toFile().also { dirs += it }
        val db = Room.databaseBuilder<AppDatabase>(name = File(dir, "test.db").absolutePath).configure()
        val files = FileStore(File(dir, "files").toOkioPath())
        val processor = JvmImageProcessor()
        val watches = WatchRepository(db, files, clock)
        val photos = PhotoRepository(db, files, processor, clock)
        val documents = DocumentRepository(db, files, processor, watches, clock)
        val services = ServiceRepository(db, watches, documents, files, clock)
        val papers = BoxPapersRepository(db, watches)
        val lookup = LookupRepository(db, files, clock)
        val settings = SettingsRepository(db.settingDao())
        val backup = BackupRepository(db, files, watches, photos, AppInfo("test", "JVM"), clock)

        init {
            phones += this
        }

        suspend fun everything(): CollectionData {
            val d = db.backupDao()
            return CollectionData(
                d.watches(), d.complications(), d.provenance(), d.otherSpecs(), d.photos(), d.serviceEntries(),
                d.boxPapers(), d.documents(), d.snapshots(), d.settings(),
            )
        }

        fun mediaFiles(): Map<String, String> {
            val root = File(dir, "files")
            return root.walkTopDown().filter { it.isFile && it.relativeTo(root).path.substringBefore('/') in setOf("photos", "documents", "snapshots") }
                .associate { it.relativeTo(root).path to it.readBytes().contentHashCode().toString() }
        }

        fun archive(): File {
            val out = File(dir, "backup.zip")
            runBlocking { backup.export(out.sink()) }
            return out
        }
    }

    @AfterTest
    fun tearDown() {
        phones.forEach { it.db.close() }
        dirs.forEach { it.deleteRecursively() }
    }

    private fun jpeg(seed: Int, w: Int = 64, h: Int = 48): ByteArray {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color(seed * 40 % 255, 90, 160)
        g.fillRect(0, 0, w, h)
        g.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(img, "jpg", it) }.toByteArray()
    }

    private val pdf = "%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\ntrailer << /Root 1 0 R >>\n%%EOF\n".encodeToByteArray()

    /** A collection that touches every table and every kind of file. */
    private suspend fun Phone.seed(): List<Watch> {
        val seamaster = watches.create(
            Watch(id = "", createdAt = now, updatedAt = now, brand = "Omega", modelName = "Seamaster 300", reference = "234.30.41.21.01.001",
                caliber = "8912", diameterMm = 41.0, powerReserveH = 60.0, acquiredOn = LocalDate(2021, 5, 1), pricePaid = Money(1_090_000, "AUD"),
                serialNumber = "87654321", privateNotes = "Bought in Sydney", complications = setOf("date", "custom:Helium valve"),
                serviceIntervalMonths = 60, serviceReminder = true, warrantyStart = LocalDate(2021, 5, 1), warrantyMonths = 60),
            sourced = mapOf("movement.caliber" to Provenance("movement.caliber", "grail", "https://example.org/8912", now)),
            otherSpecs = listOf(OtherSpec("", "Certification", "METAS", "wikidata", "https://example.org/q", now)),
        )
        val speedy = watches.create(Watch(id = "", createdAt = now, updatedAt = now, brand = "Omega", modelName = "Speedmaster Professional", diameterMm = 42.0))
        photos.add(seamaster.id, jpeg(1))
        val second = photos.add(seamaster.id, jpeg(2, 80, 80))
        photos.crop(second.id, NormalizedRect(0.1f, 0.1f, 0.9f, 0.9f))
        photos.add(speedy.id, jpeg(3))
        val entry = services.save(ServiceEntry("", seamaster.id, LocalDate(2024, 2, 1), "Omega Service Centre", setOf(ServiceWork.FULL_SERVICE),
            Money(85_000, "AUD"), 24, "New mainspring", now, now))
        documents.add(seamaster.id, pdf, DocumentKind.INVOICE, "Service invoice", serviceEntryId = entry.id)
        documents.add(seamaster.id, pdf, DocumentKind.MANUAL, "Calibre 8912 manual")
        papers.set(seamaster.id, BoxItem.INNER_BOX, BoxItemState.PRESENT)
        papers.set(seamaster.id, BoxItem.WARRANTY_CARD, BoxItemState.MISSING)
        lookup.saveSnapshots(seamaster.id, listOf(SourceRecord("grail", "8912", "https://example.org/8912", "8912", now, raw = RawPayload("application/json", """{"id":8912}"""))))
        settings.update { it.copy(accent = Accent.BORDEAUX) }
        return listOf(seamaster, speedy)
    }

    @Test fun exportWipeImportRestoresEverythingExactly() = runBlocking<Unit> {
        val source = Phone()
        source.seed()
        val before = source.everything()
        val archive = source.archive()

        val target = Phone()
        val preview = target.backup.inspect { archive.source() }
        assertEquals(2, preview.counts.watches)
        assertEquals(3, preview.counts.photos)
        assertEquals(0, preview.watchesOnPhone)
        assertTrue(preview.warnings.isEmpty(), preview.warnings.toString())
        val report = target.backup.restore({ archive.source() }, preview, RestoreMode.REPLACE)
        assertEquals(2, report.watchesAdded)

        assertEquals(before, target.everything(), "every row of every table comes back as it was")
        assertEquals(source.mediaFiles(), target.mediaFiles(), "every file comes back, byte for byte, at the same path")
        assertEquals(setOf(before.watch.first().id), target.watches.search("mainspring"), "the search index is rebuilt")
        assertEquals(Accent.BORDEAUX, target.settings.current().accent)
    }

    @Test fun archivesAreOrdinaryZipFilesWithTheManifestFirst() = runBlocking<Unit> {
        val phone = Phone()
        phone.seed()
        val archive = phone.archive()
        ZipFile(archive).use { zip ->
            val names = zip.entries().toList().map { it.name }
            assertEquals(listOf("manifest.json", "collection.json"), names.take(2))
            assertTrue(names.drop(2).all { it.startsWith("files/photos/") || it.startsWith("files/documents/") || it.startsWith("files/snapshots/") })
            val manifest = Json.parseToJsonElement(zip.getInputStream(zip.getEntry("manifest.json")).readBytes().decodeToString()).jsonObject
            assertEquals("io.github.tunlezah.watches.backup", manifest["format"]!!.jsonPrimitive.content)
            assertEquals(names.size - 1, manifest["files"]!!.jsonArray.size)
        }
    }

    @Test fun jsonKeysAreTheDatabaseColumnNames() = runBlocking<Unit> {
        val phone = Phone()
        phone.seed()
        val schema = Json.parseToJsonElement(File("schemas/io.github.tunlezah.watches.data.db.AppDatabase/1.json").readText())
            .jsonObject["database"]!!.jsonObject["entities"]!!.jsonArray
            .associate { e -> e.jsonObject["tableName"]!!.jsonPrimitive.content to e.jsonObject["fields"]!!.jsonArray.map { it.jsonObject["columnName"]!!.jsonPrimitive.content }.toSet() }
        val collection = ZipFile(phone.archive()).use { it.getInputStream(it.getEntry("collection.json")).readBytes().decodeToString() }
        val tables = Json.parseToJsonElement(collection).jsonObject
        for ((table, rows) in tables) {
            val columns = schema[table] ?: error("$table isn't a table")
            for (row in rows.jsonArray) assertTrue(columns.containsAll(row.jsonObject.keys), "$table: ${row.jsonObject.keys - columns}")
        }
        assertTrue(tables["watch"]!!.jsonArray.first().jsonObject.keys.containsAll(listOf("model_name", "diameter_mm", "price_minor", "service_interval_months")))
    }

    @Test fun restoringOntoTheSamePhoneReusesFilesAndChangesNothing() = runBlocking<Unit> {
        val phone = Phone()
        phone.seed()
        val before = phone.everything()
        val files = phone.mediaFiles()
        val archive = phone.archive()
        val preview = phone.backup.inspect { archive.source() }
        assertEquals(2, preview.merge.unchanged)
        phone.backup.restore({ archive.source() }, preview, RestoreMode.REPLACE)
        assertEquals(before, phone.everything())
        assertEquals(files, phone.mediaFiles(), "no copies, no leftovers")
    }

    @Test fun replaceRemovesWhatTheBackupDoesNotHave() = runBlocking<Unit> {
        val phone = Phone()
        val (seamaster) = phone.seed()
        val archive = phone.archive()
        val extra = phone.watches.create(Watch(id = "", createdAt = now, updatedAt = now, brand = "Tudor", modelName = "Black Bay"))
        phone.photos.add(extra.id, jpeg(9))
        phone.watches.delete(seamaster.id)

        val preview = phone.backup.inspect { archive.source() }
        val report = phone.backup.restore({ archive.source() }, preview, RestoreMode.REPLACE)
        assertEquals(2, report.watchesRemoved)
        val ids = phone.everything().watch.map { it.id }.toSet()
        assertTrue(seamaster.id in ids && extra.id !in ids)
        assertTrue(phone.mediaFiles().keys.none { extra.id in it }, "the removed watch's photos are cleaned up")
    }

    @Test fun mergeKeepsTheNewerSideAndCombinesHistories() = runBlocking<Unit> {
        val phone = Phone()
        val (seamaster, speedy) = phone.seed()
        val laptop = Phone()
        val archiveFromPhone = phone.archive()
        laptop.backup.restore({ archiveFromPhone.source() }, laptop.backup.inspect { archiveFromPhone.source() }, RestoreMode.REPLACE)

        // On the other install: edit the Speedmaster later, add a watch and a service.
        laptop.watches.update(speedy.copy(nickname = "Moonwatch", updatedAt = now))
        val tudor = laptop.watches.create(Watch(id = "", createdAt = now, updatedAt = now, brand = "Tudor", modelName = "Pelagos"))
        laptop.photos.add(tudor.id, jpeg(7))
        laptop.services.save(ServiceEntry("", seamaster.id, LocalDate(2025, 3, 1), "Local watchmaker", setOf(ServiceWork.REGULATION), null, null, null, now, now))
        val fromLaptop = laptop.archive()
        // Meanwhile on the phone: the Seamaster is edited, after the laptop's copy was made.
        phone.watches.update(phone.watches.get(seamaster.id)!!.copy(nickname = "Daily"))

        val preview = phone.backup.inspect { fromLaptop.source() }
        assertEquals(listOf("Tudor Pelagos"), preview.merge.added.map { it.title })
        assertEquals(listOf(speedy.id), preview.merge.updated.map { it.id })
        assertEquals(listOf(seamaster.id), preview.merge.kept.map { it.id })
        assertEquals(1, preview.merge.serviceEntriesAdded)

        val report = phone.backup.restore({ fromLaptop.source() }, preview, RestoreMode.MERGE)
        assertEquals(2, report.updated.size + report.kept.size, "both changed watches are reported")
        assertEquals("Moonwatch", phone.watches.get(speedy.id)!!.nickname, "the newer edit from the backup wins")
        assertEquals("Daily", phone.watches.get(seamaster.id)!!.nickname, "the newer edit on the phone wins")
        assertEquals(2, phone.services.observe(seamaster.id).first().size, "service histories are combined")
        assertEquals(1, phone.photos.forWatch(tudor.id).size)
        assertTrue(phone.mediaFiles().keys.any { tudor.id in it })
    }

    @Test fun aDamagedArchiveChangesNothing() = runBlocking<Unit> {
        val phone = Phone()
        phone.seed()
        val archive = phone.archive()
        val target = Phone()
        target.seed()
        val before = target.everything()
        val files = target.mediaFiles()

        // Flip one byte inside the last stored file (a snapshot or photo), well past the headers.
        val bytes = archive.readBytes()
        val damaged = File(target.dir, "damaged.zip")
        val at = ZipFile(archive).use { zip -> zip.entries().toList().last { it.name.startsWith("files/") }.let { e -> findDataOffset(bytes, e.name) + 2 } }
        bytes[at] = (bytes[at] + 1).toByte()
        damaged.writeBytes(bytes)

        val preview = target.backup.inspect { damaged.source() }
        assertFailsWith<BackupException.Damaged> { target.backup.restore({ damaged.source() }, preview, RestoreMode.REPLACE) }
        assertEquals(before, target.everything())
        assertEquals(files, target.mediaFiles(), "no stray files are left behind")
    }

    @Test fun otherFilesAreRefusedClearly() = runBlocking<Unit> {
        val phone = Phone()
        val notZip = File(phone.dir, "notes.zip").apply { writeText("hello") }
        assertFailsWith<BackupException.NotABackup> { phone.backup.inspect { notZip.source() } }

        val otherZip = File(phone.dir, "photos.zip")
        ZipOutputStream(otherZip.outputStream()).use { it.putNextEntry(ZipEntry("photo.jpg")); it.write(jpeg(1)) }
        assertFailsWith<BackupException.NotABackup> { phone.backup.inspect { otherZip.source() } }

        phone.seed()
        val newer = rewrite(phone.archive(), File(phone.dir, "newer.zip")) { name, data ->
            if (name.endsWith("manifest.json")) data.decodeToString().replace("\"format_version\": 1", "\"format_version\": 99").encodeToByteArray() else data
        }
        assertFailsWith<BackupException.TooNew> { phone.backup.inspect { newer.source() } }
    }

    @Test fun archivesRezippedOnAComputerStillRestore() = runBlocking<Unit> {
        val source = Phone()
        source.seed()
        val before = source.everything()
        // Like macOS's Archive Utility: deflated, data descriptors, a top folder and resource forks.
        val rezipped = File(source.dir, "rezipped.zip")
        ZipInputStream(source.archive().inputStream()).use { input ->
            ZipOutputStream(rezipped.outputStream()).use { out ->
                out.putNextEntry(ZipEntry("__MACOSX/Watches backup/._manifest.json")); out.write(byteArrayOf(1, 2, 3))
                val entries = generateSequence { input.nextEntry }.map { it.name to input.readBytes() }.toList()
                for ((name, data) in entries.reversed()) {
                    out.putNextEntry(ZipEntry("Watches backup/$name"))
                    out.write(data)
                }
            }
        }
        val target = Phone()
        target.backup.restore({ rezipped.source() }, target.backup.inspect { rezipped.source() }, RestoreMode.REPLACE)
        assertEquals(before, target.everything())
    }

    @Test fun anInterruptedRestoreIsUndoneAtNextLaunch() = runBlocking<Unit> {
        val phone = Phone()
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

    @Test fun zip64KicksInPastSixtyFiveThousandEntries() {
        val out = File(Files.createTempDirectory("zip64").toFile().also { dirs += it }, "many.zip")
        out.sink().buffer().use { sink ->
            val zip = io.github.tunlezah.watches.data.backup.ZipWriter(sink)
            repeat(70_000) { zip.add("f$it.txt", "x$it".encodeToByteArray()) }
            zip.finish()
        }
        ZipFile(out).use { assertEquals(70_000, it.size()) }
        val reader = io.github.tunlezah.watches.data.backup.ZipStreamReader(out.source())
        var count = 0
        while (reader.next()?.also { reader.skip(it) } != null) count++
        assertEquals(70_000, count)
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

    private fun rewrite(from: File, to: File, transform: (String, ByteArray) -> ByteArray): File {
        val entries = ZipFile(from).use { zip -> zip.entries().toList().map { it.name to zip.getInputStream(it).readBytes() } }
        val sink = to.sink().buffer()
        val zip = io.github.tunlezah.watches.data.backup.ZipWriter(sink)
        for ((name, data) in entries) zip.add(name, transform(name, data))
        zip.finish()
        zip.close()
        return to
    }
}
