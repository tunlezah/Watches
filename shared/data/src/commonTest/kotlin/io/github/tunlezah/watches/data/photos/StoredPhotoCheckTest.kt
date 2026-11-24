package io.github.tunlezah.watches.data.photos

import io.github.tunlezah.watches.core.model.DocumentKind
import io.github.tunlezah.watches.core.model.NormalizedRect
import io.github.tunlezah.watches.core.model.Photo
import io.github.tunlezah.watches.core.model.Watch
import io.github.tunlezah.watches.data.TestPhones
import io.github.tunlezah.watches.data.backup.RestoreMode
import io.github.tunlezah.watches.data.db.toEntity
import io.github.tunlezah.watches.data.files.FileStore
import io.github.tunlezah.watches.data.files.ImageSize
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The pass over photos that are already stored (docs/MACOS_PLAN.md, 8.2.9). Each test puts a photo
 * into the state an older version could have left it in, by writing its file or its row directly,
 * and checks the pass puts it right.
 */
class StoredPhotoCheckTest {

    private val phones = TestPhones()

    @AfterTest
    fun tearDown() = phones.close()

    private suspend fun TestPhones.Phone.watch(): Watch {
        val at = Instant.fromEpochMilliseconds(0)
        return watches.create(Watch(id = "", brand = "Omega", createdAt = at, updatedAt = at))
    }

    /** A photo as an older version stored it: the file exactly as it arrived, location and all. */
    private suspend fun TestPhones.Phone.storedUncleaned(watchId: String, fixture: String): Photo {
        val photo = photos.add(watchId, PhotoFixtures.bytes("plain.jpg"))
        val asItArrived = photo.originalFile.substringBeforeLast('.') + "." + fixture.substringAfterLast('.')
        files.delete(photo.originalFile)
        files.write(asItArrived, PhotoFixtures.bytes(fixture))
        return photo.copy(originalFile = asItArrived).also { db.photoDao().upsert(it.toEntity()) }
    }

    private fun TestPhones.Phone.photoFiles(watchId: String): List<String> =
        FileSystem.SYSTEM.list(files.resolve(FileStore.photoDir(watchId))).map { it.name }

    @Test fun aLocationAnOlderVersionLeftInIsRemoved() = runBlocking<Unit> {
        val phone = phones.new()
        val w = phone.watch()
        val old = phone.storedUncleaned(w.id, "gps-exif-ii.jpg")
        assertTrue(PhotoMetadata.hasLocation(phone.files.read(old.originalFile)))
        assertTrue(phone.storedPhotos.isDue())

        assertEquals(StoredPhotoCheck.Report(checked = 1, changed = 1, failed = 0), phone.storedPhotos.runIfDue())

        val now = phone.photos.byId(old.id)!!
        val kept = phone.files.read(now.originalFile)
        assertFalse(PhotoMetadata.hasLocation(kept))
        assertEquals(PhotoFixtures.bytes("gps-exif-ii.jpg").size, kept.size, "the same file, with only its location gone")
        assertFalse(phone.files.exists(old.originalFile), "the file with the location is gone")
        assertEquals(3, phone.photoFiles(w.id).size, "an original and two renditions, and nothing left over: ${phone.photoFiles(w.id)}")
        assertEquals(old.id to old.position, now.id to now.position)

        // Once is enough, until something asks again.
        assertFalse(phone.storedPhotos.isDue())
        assertNull(phone.storedPhotos.runIfDue())
        phone.storedPhotos.request()
        assertEquals(StoredPhotoCheck.Report(checked = 1, changed = 0, failed = 0), phone.storedPhotos.runIfDue())
        assertEquals(now, phone.photos.byId(old.id), "a photo that meets the rules is left exactly as it is")
    }

    @Test fun aSizeRecordedForThePictureOnItsSideIsCorrected() = runBlocking<Unit> {
        val phone = phones.new()
        val w = phone.watch()
        // Stored on its side with a turning of a quarter turn: upright it is 64 by 48.
        val photo = phone.photos.add(w.id, PhotoFixtures.bytes("turned-6.jpg"))
        assertEquals(64 to 48, photo.width to photo.height)
        // Image code that ignored the turning recorded the stored size, and a crop was drawn on that.
        phone.db.photoDao().upsert(photo.copy(width = 48, height = 64, crop = NormalizedRect(0f, 0f, 0.5f, 0.5f)).toEntity())

        assertEquals(1, phone.storedPhotos.runIfDue()!!.changed)

        val now = phone.photos.byId(photo.id)!!
        assertEquals(64 to 48, now.width to now.height)
        assertNull(now.crop, "a crop drawn on a picture of another shape is dropped")
        assertEquals(photo.originalFile, now.originalFile, "the original was already clean, and stays")
        assertEquals(ImageSize(64, 48), phone.processor.size(phone.files.resolve(now.thumbFile)), "its thumbnail is made again, upright")
        assertEquals(3, phone.photoFiles(w.id).size, "the old renditions are gone: ${phone.photoFiles(w.id)}")
    }

    @Test fun anOriginalInAFormatThatIsNotStoredBecomesAJpeg() = runBlocking<Unit> {
        val phone = phones.new()
        val w = phone.watch()
        val old = phone.storedUncleaned(w.id, "other.gif")

        assertEquals(1, phone.storedPhotos.runIfDue()!!.changed)

        val now = phone.photos.byId(old.id)!!
        assertTrue(now.originalFile.endsWith(".jpg"), now.originalFile)
        assertEquals(PhotoFormat.JPEG, PhotoFormat.detect(phone.files.read(now.originalFile)))
        assertEquals(64 to 48, now.width to now.height)
        assertEquals(3, phone.photoFiles(w.id).size, "${phone.photoFiles(w.id)}")
    }

    @Test fun aPhotoThisDeviceCannotShowStillLosesItsLocation() = runBlocking<Unit> {
        val phone = phones.new()
        val w = phone.watch()
        // A HEIC restored onto a device with no HEIC decoder (plain Java has none) can't be shown or
        // measured there, but removing its location needs no decoder. Where it can be decoded, it is 64 by 48 as recorded.
        val old = phone.storedUncleaned(w.id, "gps.heic")

        val report = phone.storedPhotos.runIfDue()!!
        assertEquals(1 to 0, report.changed to report.failed)

        val now = phone.photos.byId(old.id)!!
        assertTrue(now.originalFile.endsWith(".heic"), now.originalFile)
        assertFalse(PhotoMetadata.hasLocation(phone.files.read(now.originalFile)))
        assertEquals(64 to 48, now.width to now.height)
        assertFalse(phone.files.exists(old.originalFile))
    }

    @Test fun aPhotographedDocumentIsCheckedTooAndAPdfIsLeftAlone() = runBlocking<Unit> {
        val phone = phones.new()
        val w = phone.watch()
        val pdf = phone.documents.add(w.id, phones.pdf, DocumentKind.MANUAL, "Manual")
        val receipt = phone.documents.add(w.id, PhotoFixtures.bytes("no-gps.jpg"), DocumentKind.RECEIPT, "Receipt")
        // As an older version stored it: the photo as it arrived.
        phone.files.write(receipt.file, PhotoFixtures.bytes("gps-exif-ii.jpg"))

        assertEquals(StoredPhotoCheck.Report(checked = 1, changed = 1, failed = 0), phone.storedPhotos.runIfDue())

        val now = phone.documents.get(receipt.id)!!
        assertFalse(PhotoMetadata.hasLocation(phone.files.read(now.file)))
        assertEquals("image/jpeg", now.mimeType)
        assertFalse(phone.files.exists(receipt.file), "the file with the location is gone")
        assertEquals(pdf, phone.documents.get(pdf.id))
        assertTrue(phone.files.read(pdf.file).contentEquals(phones.pdf))
        assertEquals(2, FileSystem.SYSTEM.list(phone.files.resolve(FileStore.documentDir(w.id))).size)
    }

    @Test fun aPassThatWasStoppedPicksUpWhereItWas() = runBlocking<Unit> {
        val phone = phones.new()
        val w = phone.watch()
        val all = (1..3).map { phone.storedUncleaned(w.id, "gps-exif-ii.jpg") }.sortedBy { it.id }
        // As the pass leaves its note after finishing the first photo, in the order it works through them.
        phone.files.write("${FileStore.CACHE_DIR}/photo-check", "1 p:${all.first().id}".encodeToByteArray())

        assertEquals(StoredPhotoCheck.Report(checked = 2, changed = 2, failed = 0), phone.storedPhotos.runIfDue())

        assertTrue(PhotoMetadata.hasLocation(phone.files.read(phone.photos.byId(all[0].id)!!.originalFile)), "already done before the stop, by its note: not looked at again")
        for (photo in all.drop(1)) assertFalse(PhotoMetadata.hasLocation(phone.files.read(phone.photos.byId(photo.id)!!.originalFile)))
        assertFalse(phone.storedPhotos.isDue())

        // A note left by a pass under older rules counts for nothing.
        phone.files.write("${FileStore.CACHE_DIR}/photo-check", "0 done".encodeToByteArray())
        assertEquals(StoredPhotoCheck.Report(checked = 3, changed = 1, failed = 0), phone.storedPhotos.runIfDue())
    }

    @Test fun aRestoreAsksForAPassOverWhatItBroughtIn() = runBlocking<Unit> {
        // A backup made by a version that left locations in.
        val old = phones.new()
        val w = old.watch()
        val photo = old.storedUncleaned(w.id, "gps-exif-mm.jpg")
        val archive = old.archive()

        val phone = phones.new()
        phone.storedPhotos.runIfDue()
        assertFalse(phone.storedPhotos.isDue())
        val preview = phone.backup.inspect { FileSystem.SYSTEM.source(archive) }
        phone.backup.restore({ FileSystem.SYSTEM.source(archive) }, preview, RestoreMode.REPLACE)
        assertTrue(PhotoMetadata.hasLocation(phone.files.read(phone.photos.byId(photo.id)!!.originalFile)), "restored as the backup had it")

        assertTrue(phone.storedPhotos.isDue(), "a restore brings in files from somewhere else")
        assertEquals(1, phone.storedPhotos.runIfDue()!!.changed)
        assertFalse(PhotoMetadata.hasLocation(phone.files.read(phone.photos.byId(photo.id)!!.originalFile)))

        // The next backup from this device carries the cleaned photo, not the one that came.
        val next = phone.archive()
        val again = phones.new()
        again.backup.restore({ FileSystem.SYSTEM.source(next) }, again.backup.inspect { FileSystem.SYSTEM.source(next) }, RestoreMode.REPLACE)
        assertFalse(PhotoMetadata.hasLocation(again.files.read(again.photos.byId(photo.id)!!.originalFile)))
    }
}
