package io.github.tunlezah.watches.data

import kotlin.test.assertFailsWith
import io.github.tunlezah.watches.data.photos.PhotoMetadata
import io.github.tunlezah.watches.data.photos.PhotoFormat
import io.github.tunlezah.watches.data.photos.PhotoFixtures
import io.github.tunlezah.watches.data.photos.LocationRemoval
import io.github.tunlezah.watches.data.files.ImageSize
import androidx.room.execSQL
import androidx.room.useWriterConnection
import io.github.tunlezah.watches.core.TimeSource
import io.github.tunlezah.watches.core.model.Complication
import io.github.tunlezah.watches.core.model.Money
import io.github.tunlezah.watches.core.model.MovementType
import io.github.tunlezah.watches.core.model.NormalizedRect
import io.github.tunlezah.watches.core.model.Sources
import io.github.tunlezah.watches.core.model.Watch
import io.github.tunlezah.watches.core.model.WatchStatus
import io.github.tunlezah.watches.core.query.SortOrder
import io.github.tunlezah.watches.core.settings.Accent
import io.github.tunlezah.watches.core.settings.Direction
import io.github.tunlezah.watches.data.db.AppDatabase
import io.github.tunlezah.watches.data.db.FieldProvenanceEntity
import io.github.tunlezah.watches.data.files.FileStore
import io.github.tunlezah.watches.data.files.ImageProcessor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import okio.FileSystem
import okio.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RepositoryTest {

    private lateinit var dir: Path
    private lateinit var db: AppDatabase
    private lateinit var files: FileStore
    private lateinit var watches: WatchRepository
    private lateinit var photos: PhotoRepository
    private lateinit var settings: SettingsRepository
    private val processor = testImageProcessor()

    private var now = Instant.fromEpochMilliseconds(1_760_000_000_000)
    private val clock = TimeSource { now.also { now += 1.seconds } }

    @BeforeTest
    fun setUp() {
        dir = newTestDir()
        db = openTestDatabase(dir / "test.db")
        files = FileStore(dir / "files")
        watches = WatchRepository(db, files, clock)
        photos = PhotoRepository(db, files, processor, clock)
        settings = SettingsRepository(db.settingDao())
    }

    @AfterTest
    fun tearDown() {
        db.close()
        FileSystem.SYSTEM.deleteRecursively(dir)
    }

    private fun blank() = Watch(id = "", createdAt = now, updatedAt = now)

    private val speedmaster get() = blank().copy(
        brand = "Omega", collection = "Speedmaster", modelName = "Speedmaster Professional",
        reference = "310.30.42.50.01.001", diameterMm = 42.0, movementType = MovementType.MANUAL,
        complications = setOf(Complication.CHRONOGRAPH.key, Complication.SMALL_SECONDS.key),
        acquiredOn = LocalDate(2023, 3, 14), pricePaid = Money(1_095_000, "AUD"),
        serialNumber = "87654321", privateNotes = "Bought in Genève on a rainy day",
    )

    private fun jpeg(width: Int, height: Int): ByteArray = testJpeg(width, height)

    @Test
    fun createRoundTripsAndRecordsManualProvenance() = runBlocking<Unit> {
        val created = watches.create(speedmaster)
        val loaded = watches.get(created.id)
        assertEquals(created.copy(updatedAt = loaded!!.updatedAt), loaded)

        val detail = watches.observeDetail(created.id).first()!!
        val diameter = detail.provenance.getValue("case.diameter_mm")
        assertEquals(Sources.MANUAL, diameter.source)
        assertTrue(diameter.userEdited)
        assertNull(detail.provenance["identity.year_from"], "empty fields carry no provenance")

        val summary = watches.observeSummaries().first().single()
        assertEquals("Speedmaster Professional", summary.title)
        assertEquals(setOf("chronograph", "small_seconds"), summary.complications)
    }

    @Test
    fun editsKeepSourceDetailsAndFlagUserEdited() = runBlocking<Unit> {
        val created = watches.create(speedmaster)
        // Simulate a value that came from a lookup.
        db.watchDao().upsertProvenance(listOf(
            FieldProvenanceEntity(created.id, "case.thickness_mm", "watchbase", "https://watchbase.com/omega/x", 1L, userEdited = false),
        ))
        watches.update(created.copy(thicknessMm = 13.2))
        val p = watches.observeDetail(created.id).first()!!.provenance.getValue("case.thickness_mm")
        assertEquals("watchbase", p.source)
        assertEquals("https://watchbase.com/omega/x", p.sourceUrl)
        assertTrue(p.userEdited)
    }

    @Test
    fun fullTextSearchCoversEveryField() = runBlocking<Unit> {
        val a = watches.create(speedmaster)
        val b = watches.create(blank().copy(brand = "Tudor", modelName = "Black Bay 58", reference = "M79030N-0001"))

        assertEquals(setOf(a.id), watches.search("omeg"))
        assertEquals(setOf(a.id), watches.search("Speedmaster 310.30"))
        assertEquals(setOf(a.id), watches.search("31030425001001"), "reference without punctuation")
        assertEquals(setOf(a.id), watches.search("geneve"), "diacritics are folded")
        assertEquals(setOf(a.id), watches.search("rainy"), "private notes are searchable")
        assertEquals(setOf(a.id), watches.search("chrono"), "complication labels are searchable")
        assertEquals(setOf(b.id), watches.search("m79030n"))
        assertEquals(emptySet(), watches.search("rolex"))
        assertNull(watches.search("  "))

        watches.update(watches.get(b.id)!!.copy(privateNotes = "rainy afternoon"))
        assertEquals(setOf(a.id, b.id), watches.search("rainy"))
    }

    @Test
    fun searchIndexRebuildsWhenOutOfStep() = runBlocking<Unit> {
        val a = watches.create(speedmaster)
        db.useWriterConnection { c -> c.execSQL("DELETE FROM watch_fts") }
        assertEquals(emptySet(), watches.search("omega"))
        watches.ensureSearchIndex()
        assertEquals(setOf(a.id), watches.search("omega"))
    }

    @Test
    fun duplicateCopiesSpecsButNotOwnership() = runBlocking<Unit> {
        val created = watches.create(speedmaster)
        val copy = watches.duplicate(created.id)!!
        assertNotEquals(created.id, copy.id)
        assertEquals("310.30.42.50.01.001", copy.reference)
        assertEquals(created.complications, copy.complications)
        assertNull(copy.serialNumber)
        assertNull(copy.pricePaid)
        assertNull(copy.acquiredOn)
        assertEquals(2, watches.observeSummaries().first().size)
    }

    @Test
    fun photosRenderRenditionsAndTrackCover() = runBlocking<Unit> {
        val w = watches.create(speedmaster)
        val first = photos.add(w.id, jpeg(3000, 4000))
        val second = photos.add(w.id, jpeg(1200, 800))

        assertEquals(3000, first.width)
        assertTrue(files.exists(first.originalFile))
        assertTrue(files.exists(first.displayFile))
        assertTrue(files.exists(first.thumbFile))
        val thumb = processor.size(files.resolve(first.thumbFile))
        assertEquals(640, thumb.height, "thumb long edge")
        assertEquals(480, thumb.width)
        val display = processor.size(files.resolve(first.displayFile))
        assertEquals(2560, display.height)

        assertEquals(first.id, watches.get(w.id)!!.coverPhotoId, "first photo becomes the cover")
        assertEquals(files.absolute(first.thumbFile).substringAfterLast('/'), watches.observeSummaries().first().single().coverThumb!!.substringAfterLast('/'))

        // Non-destructive crop: renditions are re-rendered, the original stays.
        photos.crop(first.id, NormalizedRect(0f, 0f, 0.5f, 0.5f))
        val cropped = photos.forWatch(w.id).first { it.id == first.id }
        assertNotEquals(first.thumbFile, cropped.thumbFile)
        assertFalse(files.exists(first.thumbFile), "old rendition removed")
        assertEquals(first.originalFile, cropped.originalFile)
        val croppedThumb = processor.size(files.resolve(cropped.thumbFile))
        assertEquals(640, croppedThumb.height)
        assertEquals(480, croppedThumb.width)

        photos.reorder(w.id, listOf(second.id, first.id))
        assertEquals(listOf(second.id, first.id), photos.forWatch(w.id).map { it.id })

        photos.delete(first.id)
        assertEquals(second.id, watches.get(w.id)!!.coverPhotoId, "cover moves to the next photo")
        assertFalse(files.exists(cropped.originalFile))
        assertEquals(0, photos.forWatch(w.id).single().position)
    }

    @Test
    fun deleteRemovesRowsAndFiles() = runBlocking<Unit> {
        val w = watches.create(speedmaster)
        val p = photos.add(w.id, jpeg(400, 400))
        watches.delete(w.id)
        assertNull(watches.get(w.id))
        assertTrue(photos.forWatch(w.id).isEmpty(), "photo rows cascade")
        assertFalse(files.exists(p.originalFile))
        assertTrue(db.watchDao().getProvenance(w.id).isEmpty(), "provenance cascades")
        assertEquals(emptySet(), watches.search("omega"))
    }

    @Test
    fun archiveHidesButKeepsHistory() = runBlocking<Unit> {
        val w = watches.create(speedmaster.copy(status = WatchStatus.SOLD))
        watches.setArchived(w.id, true)
        val summary = watches.observeSummaries().first().single()
        assertTrue(summary.archived)
        assertEquals(WatchStatus.SOLD, summary.status)
        watches.setArchived(w.id, false)
        assertFalse(watches.observeSummaries().first().single().archived)
    }

    @Test
    fun settingsPersist() = runBlocking<Unit> {
        assertEquals(Direction.ATELIER, settings.current().direction)
        settings.update { it.copy(direction = Direction.GALLERY, accent = Accent.LUME, sort = it.sort.copy(order = SortOrder.CASE_SIZE)) }
        val s = settings.current()
        assertEquals(Direction.GALLERY, s.direction)
        assertEquals(Accent.LUME, s.accent)
        assertEquals(SortOrder.CASE_SIZE, s.sort.order)
    }

    @Test
    fun brandsAreDistinctAndSorted() = runBlocking<Unit> {
        watches.create(speedmaster)
        watches.create(blank().copy(brand = "grand Seiko"))
        watches.create(blank().copy(brand = "Omega"))
        assertEquals(listOf("grand Seiko", "Omega"), watches.observeBrands().first())
        assertNotNull(watches.get(watches.observeSummaries().first().first().id))
    }

    @Test fun lookupValuesKeepTheirSource() = runBlocking<Unit> {
        val fetched = Instant.fromEpochMilliseconds(1_790_000_000_000)
        val ranfft = io.github.tunlezah.watches.core.model.Provenance("movement.jewels", "ranfft", "https://ranfft.org/caliber/1-x", fetched)
        val created = watches.create(blank().copy(brand = "Tudor", jewels = 25), sourced = mapOf("movement.jewels" to ranfft),
            otherSpecs = listOf(io.github.tunlezah.watches.core.model.OtherSpec("o1", "Lift angle", "50°", "ranfft", "https://ranfft.org/caliber/1-x", fetched)))
        val detail = watches.observeDetail(created.id).first()!!
        assertEquals("ranfft", detail.provenance.getValue("movement.jewels").source)
        assertFalse(detail.provenance.getValue("movement.jewels").userEdited)
        assertTrue(detail.provenance.getValue("identity.brand").userEdited)
        assertEquals(listOf("Lift angle"), detail.otherSpecs.map { it.label })

        // A later lookup from the same source replaces its extras; the owner's own stay.
        watches.update(created.copy(powerReserveH = 40.0),
            sourced = mapOf("movement.power_reserve_h" to ranfft.copy(fieldKey = "movement.power_reserve_h")),
            otherSpecs = listOf(io.github.tunlezah.watches.core.model.OtherSpec("o2", "Escapement", "Swiss lever", "ranfft", "u", fetched)))
        val after = watches.observeDetail(created.id).first()!!
        assertEquals(listOf("Escapement"), after.otherSpecs.map { it.label })
        assertEquals("ranfft", after.provenance.getValue("movement.power_reserve_h").source)
        assertFalse(after.provenance.getValue("movement.power_reserve_h").userEdited)
    }

    @Test fun mergeOtherSpecsBySource() {
        val o = { id: String, src: String? -> io.github.tunlezah.watches.core.model.OtherSpec(id, id, "v", src) }
        val merged = WatchRepository.mergeOtherSpecs(listOf(o("a", "grail"), o("b", null), o("c", "ranfft")), listOf(o("d", "ranfft")))
        assertEquals(listOf("a", "b", "d"), merged.map { it.id })
        assertEquals(listOf(0, 1, 2), merged.map { it.position })
    }

    @Test fun recentSearchesAndSnapshots() = runBlocking<Unit> {
        val lookups = LookupRepository(db, files, clock)
        repeat(15) { lookups.remember("query $it") }
        lookups.remember("query 3")
        val recent = lookups.observeRecent().first()
        assertEquals(LookupRepository.RECENT_LIMIT, recent.size)
        assertEquals("query 3", recent.first())
        lookups.forget("query 3")
        assertFalse("query 3" in lookups.observeRecent().first())

        val w = watches.create(blank().copy(brand = "Omega"))
        val record = io.github.tunlezah.watches.core.lookup.SourceRecord("grail", "eta-7750", "https://g/eta-7750", "ETA 7750",
            Instant.fromEpochMilliseconds(1_790_000_000_000), raw = io.github.tunlezah.watches.core.lookup.RawPayload("application/json", "[1]"))
        lookups.saveSnapshots(w.id, listOf(record))
        lookups.saveSnapshots(w.id, listOf(record))
        assertEquals(listOf("grail" to "https://g/eta-7750"), lookups.snapshotSources(w.id))
        val stored = FileSystem.SYSTEM.list(files.resolve(FileStore.snapshotDir(w.id)))
        assertEquals(1, stored.size)
        assertEquals("[1]", FileSystem.SYSTEM.read(stored.single()) { readUtf8() })
        watches.delete(w.id)
        assertFalse(FileSystem.SYSTEM.exists(files.resolve(FileStore.snapshotDir(w.id))))
    }

    @Test fun disabledSourcesPersist() = runBlocking<Unit> {
        settings.update { it.copy(disabledSources = setOf("grail")) }
        assertEquals(setOf("grail"), settings.current().disabledSources)
        settings.update { it.copy(disabledSources = emptySet()) }
        assertEquals(emptySet(), settings.current().disabledSources)
    }

    @Test
    fun aPhotosLocationIsGoneFromItsStoredOriginalAndItsTurningIsApplied() = runBlocking<Unit> {
        val w = watches.create(speedmaster)
        // A portrait photo as an iPhone writes it: stored on its side, with a location.
        val arrived = PhotoFixtures.bytes("gps-exif-mm.jpg")
        assertTrue(PhotoMetadata.hasLocation(arrived))
        val photo = photos.add(w.id, PhotoFixtures.bytes("gps-exif-mm.jpg"))

        assertTrue(photo.originalFile.endsWith("-original.jpg"))
        val kept = files.read(photo.originalFile)
        assertFalse(PhotoMetadata.hasLocation(kept), "the original is stored without its location")
        assertEquals(arrived.size, kept.size, "and is otherwise the file that arrived")
        // Its size, and its renditions, are of the upright picture.
        assertEquals(64 to 48, photo.width to photo.height)
        assertEquals(ImageSize(64, 48), processor.size(files.resolve(photo.thumbFile)))
        assertFalse(PhotoMetadata.hasLocation(files.read(photo.displayFile)))
    }

    @Test
    fun aPhotoThePlatformStillFindsALocationInIsKeptAsAJpegMadeFromIt() = runBlocking<Unit> {
        val w = watches.create(speedmaster)
        // The shared code says the location has gone. This platform's reader, asked for a second opinion, disagrees.
        val doubting = object : ImageProcessor by processor {
            override suspend fun hasLocation(file: Path) = true
        }
        val arrived = PhotoFixtures.bytes("gps-exif-ii.jpg")
        val photo = PhotoRepository(db, files, doubting, clock).add(w.id, arrived)

        assertTrue(photo.originalFile.endsWith("-original.jpg"))
        val kept = files.read(photo.originalFile)
        assertTrue(kept.size != arrived.size, "the file that arrived isn't the one kept")
        assertFalse(PhotoMetadata.hasLocation(kept))
        // A JPEG made from the picture has no metadata for a location to be in.
        assertEquals(LocationRemoval.NONE, PhotoMetadata.removeLocation(kept.copyOf()))
        assertEquals(64 to 48, photo.width to photo.height)
        val stored = FileSystem.SYSTEM.list(files.resolve(FileStore.photoDir(w.id))).map { it.name }
        assertEquals(3, stored.size, "an original and two renditions, and nothing left over: $stored")
    }

    @Test
    fun aFormatOnlySomeDevicesShowIsKeptAsAJpeg() = runBlocking<Unit> {
        val w = watches.create(speedmaster)
        // No system reads all three: Android has no TIFF decoder. What this one doesn't read it refuses.
        val all = listOf("other.gif" to PhotoFormat.GIF, "other.bmp" to PhotoFormat.BMP, "other.tiff" to PhotoFormat.TIFF)
        val (taken, refused) = all.partition { it.second in processor.readableFormats }
        for ((name, _) in taken) {
            val photo = photos.add(w.id, PhotoFixtures.bytes(name))
            assertTrue(photo.originalFile.endsWith("-original.jpg"), "$name: ${photo.originalFile}")
            assertEquals(PhotoFormat.JPEG, PhotoFormat.detect(files.read(photo.originalFile)), name)
            assertEquals(64 to 48, photo.width to photo.height, name)
        }
        for ((name, _) in refused) assertFails(name) { photos.add(w.id, PhotoFixtures.bytes(name)) }
        assertTrue(taken.size >= 2, "GIF and BMP are read everywhere: ${processor.readableFormats}")
        assertEquals(taken.size, photos.forWatch(w.id).size)
        val dir = files.resolve(FileStore.photoDir(w.id))
        val stored = if (FileSystem.SYSTEM.exists(dir)) FileSystem.SYSTEM.list(dir).map { it.name } else emptyList()
        assertEquals(3 * taken.size, stored.size, "an original and two renditions each, and nothing left over: $stored")
        assertTrue(stored.none { "incoming" in it })
    }

    @Test
    fun aPhotoWhoseMetadataCantBeFollowedIsKeptAsAJpegMadeWithoutIt() = runBlocking<Unit> {
        val w = watches.create(speedmaster)
        // The pointer to its GPS directory leads nowhere, so the location can't be found to be removed.
        val damaged = PhotoFixtures.bytes("gps-exif-ii.jpg")
        val pointer = (0..damaged.size - 8).first { i -> damaged[i] == 0x25.toByte() && damaged[i + 1] == 0x88.toByte() && damaged[i + 2] == 0x04.toByte() && damaged[i + 3] == 0.toByte() }
        for (i in 8..10) damaged[pointer + i] = 0x7F
        assertEquals(LocationRemoval.UNREADABLE, PhotoMetadata.removeLocation(damaged.copyOf()))

        val photo = photos.add(w.id, damaged)
        val kept = files.read(photo.originalFile)
        assertFalse(PhotoMetadata.hasLocation(kept))
        assertFalse(kept.contentEquals(damaged), "the damaged file itself isn't kept")
        assertEquals(64 to 48, photo.width to photo.height)
    }

    @Test
    fun somethingThatIsNotAPictureIsRefusedAndLeavesNothingBehind() = runBlocking<Unit> {
        val w = watches.create(speedmaster)
        assertFailsWith<Exception> { photos.add(w.id, "not a picture at all".encodeToByteArray()) }
        val folder = files.resolve(FileStore.photoDir(w.id))
        assertTrue(!FileSystem.SYSTEM.exists(folder) || FileSystem.SYSTEM.list(folder).isEmpty())
        assertTrue(photos.forWatch(w.id).isEmpty())
    }
}
