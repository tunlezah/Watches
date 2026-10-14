package io.github.tunlezah.watches.data

import androidx.room.Room
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
import io.github.tunlezah.watches.data.db.configure
import io.github.tunlezah.watches.data.files.FileStore
import io.github.tunlezah.watches.data.files.JvmImageProcessor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import okio.Path.Companion.toOkioPath
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RepositoryTest {

    private lateinit var dir: File
    private lateinit var db: AppDatabase
    private lateinit var files: FileStore
    private lateinit var watches: WatchRepository
    private lateinit var photos: PhotoRepository
    private lateinit var settings: SettingsRepository

    private var now = Instant.fromEpochMilliseconds(1_760_000_000_000)
    private val clock = TimeSource { now.also { now += 1.seconds } }

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("watches-test").toFile()
        db = Room.databaseBuilder<AppDatabase>(name = File(dir, "test.db").absolutePath).configure()
        files = FileStore(File(dir, "files").toOkioPath())
        watches = WatchRepository(db, files, clock)
        photos = PhotoRepository(db, files, JvmImageProcessor(), clock)
        settings = SettingsRepository(db.settingDao())
    }

    @AfterTest
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    private fun blank() = Watch(id = "", createdAt = now, updatedAt = now)

    private val speedmaster get() = blank().copy(
        brand = "Omega", collection = "Speedmaster", modelName = "Speedmaster Professional",
        reference = "310.30.42.50.01.001", diameterMm = 42.0, movementType = MovementType.MANUAL,
        complications = setOf(Complication.CHRONOGRAPH.key, Complication.SMALL_SECONDS.key),
        acquiredOn = LocalDate(2023, 3, 14), pricePaid = Money(1_095_000, "AUD"),
        serialNumber = "87654321", privateNotes = "Bought in Genève on a rainy day",
    )

    private fun jpeg(width: Int, height: Int): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        img.createGraphics().apply { color = Color(30, 30, 34); fillRect(0, 0, width, height); dispose() }
        return ByteArrayOutputStream().also { ImageIO.write(img, "jpg", it) }.toByteArray()
    }

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
        val thumb = ImageIO.read(File(files.absolute(first.thumbFile)))
        assertEquals(640, thumb.height, "thumb long edge")
        assertEquals(480, thumb.width)
        val display = ImageIO.read(File(files.absolute(first.displayFile)))
        assertEquals(2560, display.height)

        assertEquals(first.id, watches.get(w.id)!!.coverPhotoId, "first photo becomes the cover")
        assertEquals(files.absolute(first.thumbFile).substringAfterLast('/'), watches.observeSummaries().first().single().coverThumb!!.substringAfterLast('/'))

        // Non-destructive crop: renditions are re-rendered, the original stays.
        photos.crop(first.id, NormalizedRect(0f, 0f, 0.5f, 0.5f))
        val cropped = photos.forWatch(w.id).first { it.id == first.id }
        assertNotEquals(first.thumbFile, cropped.thumbFile)
        assertFalse(files.exists(first.thumbFile), "old rendition removed")
        assertEquals(first.originalFile, cropped.originalFile)
        val croppedThumb = ImageIO.read(File(files.absolute(cropped.thumbFile)))
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
        val stored = File(dir, "files/${FileStore.snapshotDir(w.id)}").listFiles()!!.toList()
        assertEquals(1, stored.size)
        assertEquals("[1]", stored.single().readText())
        watches.delete(w.id)
        assertFalse(File(dir, "files/${FileStore.snapshotDir(w.id)}").exists())
    }

    @Test fun disabledSourcesPersist() = runBlocking<Unit> {
        settings.update { it.copy(disabledSources = setOf("grail")) }
        assertEquals(setOf("grail"), settings.current().disabledSources)
        settings.update { it.copy(disabledSources = emptySet()) }
        assertEquals(emptySet(), settings.current().disabledSources)
    }
}
