package io.github.tunlezah.watches.data

import io.github.tunlezah.watches.core.format.DisplayPrefs
import io.github.tunlezah.watches.core.spec.SpecSheet
import io.github.tunlezah.watches.data.backup.CsvExporter
import io.github.tunlezah.watches.data.backup.RestoreMode
import io.github.tunlezah.watches.data.db.toModel
import io.github.tunlezah.watches.data.photos.PhotoMetadata
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Restores a backup made outside the app the way the app does, on a new install and onto one that
 * already has watches, and checks everything came through. Runs only when WATCHES_BACKUP_CHECK names a file.
 */
class PersonalBackupCheck {

    private val phones = TestPhones()

    @AfterTest
    fun tearDown() = phones.close()

    @Test fun restores() = runBlocking<Unit> {
        val path = System.getenv("WATCHES_BACKUP_CHECK")?.takeIf { it.isNotBlank() }?.toPath() ?: return@runBlocking
        val open = { FileSystem.SYSTEM.source(path) }

        // A new install, restoring with Replace.
        val fresh = phones.new()
        val preview = fresh.backup.inspect(open)
        assertTrue(preview.warnings.isEmpty(), "inspect warnings: ${preview.warnings}")
        println("PersonalBackupCheck: manifest says ${preview.manifest.app}, ${preview.counts}, ${preview.mediaBytes} bytes of media")
        val report = fresh.backup.restore(open, preview, RestoreMode.REPLACE)
        assertTrue(report.warnings.isEmpty(), "restore warnings: ${report.warnings}")
        assertEquals(preview.counts.watches, report.watchesAdded)

        val all = fresh.everything()
        assertEquals(preview.counts.watches, all.watch.size)
        assertEquals(preview.counts.photos, all.photo.size)
        assertEquals(preview.counts.documents, all.document.size)
        for (row in all.watch) {
            val watch = row.toModel(all.watchComplication.filter { it.watchId == row.id }.map { it.key })
            val cover = assertNotNull(all.photo.firstOrNull { it.id == watch.coverPhotoId }, "${watch.title} has a cover")
            for (f in listOf(cover.originalFile, cover.displayFile, cover.thumbFile)) {
                assertTrue(fresh.files.exists(f), f)
                assertFalse(PhotoMetadata.hasLocation(fresh.files.read(f)), "$f has no location")
            }
            val provenance = all.fieldProvenance.filter { it.watchId == row.id }.associate { it.fieldKey to it.toModel() }
            val sheet = SpecSheet.build(watch, provenance, DisplayPrefs())
            val docs = all.document.filter { it.watchId == row.id }
            for (doc in docs) {
                val bytes = fresh.files.read(doc.file)
                assertEquals(doc.sizeBytes, bytes.size.toLong(), doc.file)
                if (doc.mimeType == "application/pdf") assertEquals("%PDF-", bytes.copyOfRange(0, 5).decodeToString(), doc.file)
            }
            val sourced = provenance.values.count { it.source != "manual" }
            val others = all.otherSpec.count { it.watchId == row.id }
            println("PersonalBackupCheck: ${watch.title} | ${sheet.sumOf { it.rows.size }} spec rows ($sourced sourced) + $others other | " +
                "${all.photo.count { it.watchId == row.id }} photos | ${docs.size} documents | complications ${watch.complications.sorted()}")
        }

        // The pass a restore asks for finds nothing to change in these photos.
        val pass = assertNotNull(fresh.storedPhotos.runIfDue())
        assertEquals(0, pass.failed, "photos the check couldn't read")
        println("PersonalBackupCheck: stored-photo check $pass")

        // CSV export works on the restored collection.
        val csv = phones.scratchDir() / "collection.csv"
        val rows = CsvExporter(fresh.db, phones.clock).export(FileSystem.SYSTEM.sink(csv))
        assertEquals(all.watch.size, rows)

        // The restored collection backs up again, and that backup restores too.
        val again = fresh.archive()
        val second = phones.new()
        val p2 = second.backup.inspect { FileSystem.SYSTEM.source(again) }
        assertEquals(preview.counts, p2.counts)
        second.backup.restore({ FileSystem.SYSTEM.source(again) }, p2, RestoreMode.REPLACE)

        // A phone that already has watches, restoring with Merge: everything is added and nothing it had is lost.
        val existing = phones.new()
        val seeded = existing.seed()
        val p3 = existing.backup.inspect(open)
        assertTrue(p3.warnings.isEmpty(), "merge warnings: ${p3.warnings}")
        val merged = existing.backup.restore(open, p3, RestoreMode.MERGE)
        assertEquals(preview.counts.watches, merged.watchesAdded)
        assertEquals(seeded.size + preview.counts.watches, existing.everything().watch.size)
        println("PersonalBackupCheck: merge added ${merged.watchesAdded} watches, ${merged.photosAdded} photos, ${merged.documentsAdded} documents")
    }
}
