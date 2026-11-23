package io.github.tunlezah.watches.data

import io.github.tunlezah.watches.data.photos.PhotoMetadata
import io.github.tunlezah.watches.data.photos.PhotoFixtures
import io.github.tunlezah.watches.data.photos.PhotoFormat
import io.github.tunlezah.watches.core.TimeSource
import io.github.tunlezah.watches.core.model.BoxItem
import io.github.tunlezah.watches.core.model.BoxItemState
import io.github.tunlezah.watches.core.model.DocumentKind
import io.github.tunlezah.watches.core.model.Money
import io.github.tunlezah.watches.core.model.ServiceEntry
import io.github.tunlezah.watches.core.model.ServiceWork
import io.github.tunlezah.watches.core.model.Watch
import io.github.tunlezah.watches.core.model.WatchStatus
import io.github.tunlezah.watches.data.db.AppDatabase
import io.github.tunlezah.watches.core.model.NormalizedRect
import io.github.tunlezah.watches.data.files.FileStore
import io.github.tunlezah.watches.data.files.ImageProcessor
import io.github.tunlezah.watches.data.files.ImageSize
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import okio.FileSystem
import okio.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class CareRepositoryTest {

    private lateinit var dir: Path
    private lateinit var db: AppDatabase
    private lateinit var files: FileStore
    private lateinit var watches: WatchRepository
    private lateinit var documents: DocumentRepository
    private lateinit var services: ServiceRepository
    private lateinit var papers: BoxPapersRepository

    private var now = Instant.fromEpochMilliseconds(1_790_000_000_000)
    private val clock = TimeSource { now.also { now += 1.seconds } }

    @BeforeTest
    fun setUp() {
        dir = newTestDir()
        db = openTestDatabase(dir / "test.db")
        files = FileStore(dir / "files")
        watches = WatchRepository(db, files, clock)
        documents = DocumentRepository(db, files, testImageProcessor(), watches, clock)
        services = ServiceRepository(db, watches, documents, files, clock)
        papers = BoxPapersRepository(db, watches)
    }

    @AfterTest
    fun tearDown() {
        db.close()
        FileSystem.SYSTEM.deleteRecursively(dir)
    }

    private fun watch(brand: String = "Omega", model: String = "Seamaster 300") =
        Watch(id = "", createdAt = now, updatedAt = now, brand = brand, modelName = model, caliber = "8400", acquiredOn = LocalDate(2021, 5, 1))

    private val pdf = "%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\ntrailer << /Root 1 0 R >>\n%%EOF\n".encodeToByteArray()

    private fun jpeg(): ByteArray = testJpeg(40, 30, 0, 0, 0)

    private fun names(folder: Path): List<String> =
        if (FileSystem.SYSTEM.exists(folder)) FileSystem.SYSTEM.list(folder).map { it.name } else emptyList()

    private fun entry(watchId: String, on: LocalDate, vararg work: ServiceWork) =
        ServiceEntry("", watchId, on, servicedBy = "Omega Service Centre Sydney", work = work.toSet(), cost = Money(85_000, "AUD"),
            warrantyMonths = 24, notes = "New mainspring and crown seal", createdAt = now, updatedAt = now)

    @Test fun serviceEntriesRoundTripAndDriveTheCareView() = runBlocking<Unit> {
        val w = watches.create(watch())
        services.setPlan(w.id, 60, reminder = true)
        val full = services.save(entry(w.id, LocalDate(2024, 2, 1), ServiceWork.FULL_SERVICE, ServiceWork.GASKETS))
        services.save(entry(w.id, LocalDate(2025, 7, 1), ServiceWork.REGULATION))

        val list = services.observe(w.id).first()
        assertEquals(listOf(LocalDate(2025, 7, 1), LocalDate(2024, 2, 1)), list.map { it.servicedOn })
        assertEquals(setOf(ServiceWork.FULL_SERVICE, ServiceWork.GASKETS), list.last().work)
        assertEquals(Money(85_000, "AUD"), list.last().cost)

        val care = services.observeCare().first().single()
        assertEquals(LocalDate(2025, 7, 1), care.lastService)
        assertEquals(LocalDate(2024, 2, 1), care.lastFullService, "only full services restart the interval")
        assertEquals(60, care.intervalMonths)
        assertTrue(care.reminder)
        assertTrue(care.needsCare)

        // Updating keeps the creation time.
        val edited = services.save(full.copy(notes = "Mainspring, seals and a new crystal"))
        assertEquals(full.createdAt, edited.createdAt)
        assertEquals(listOf("Omega Service Centre Sydney"), services.observeServicers().first())
        assertEquals(2, services.observeWithWarranty().first().size)
    }

    @Test fun deletingAnEntryCanKeepOrDropItsInvoice() = runBlocking<Unit> {
        val w = watches.create(watch())
        val a = services.save(entry(w.id, LocalDate(2024, 2, 1), ServiceWork.FULL_SERVICE))
        val b = services.save(entry(w.id, LocalDate(2025, 2, 1), ServiceWork.BATTERY))
        val invoiceA = documents.add(w.id, pdf, DocumentKind.INVOICE, "Invoice 2024", serviceEntryId = a.id)
        val invoiceB = documents.add(w.id, pdf, DocumentKind.INVOICE, "Invoice 2025", serviceEntryId = b.id)

        services.delete(a.id, withDocuments = false)
        val kept = documents.get(invoiceA.id)!!
        assertNull(kept.serviceEntryId, "the invoice stays with the watch")

        services.delete(b.id, withDocuments = true)
        assertNull(documents.get(invoiceB.id))
        assertFalse(FileSystem.SYSTEM.exists(files.resolve(invoiceB.file)))
    }

    @Test fun documentsAreTypedByContentAndSearchable() = runBlocking<Unit> {
        val w = watches.create(watch())
        val manual = documents.add(w.id, pdf, DocumentKind.MANUAL, "Calibre 8400 instructions.pdf")
        assertEquals("application/pdf", manual.mimeType)
        assertTrue(manual.file.endsWith(".pdf"))
        assertEquals(pdf.size.toLong(), manual.sizeBytes)
        val receipt = documents.add(w.id, jpeg(), DocumentKind.RECEIPT, "")
        assertEquals("image/jpeg", receipt.mimeType)
        assertEquals("Receipt", receipt.title, "a blank title falls back to the kind")

        assertFailsWith<DocumentRepository.UnsupportedDocument> { documents.add(w.id, "hello".encodeToByteArray(), DocumentKind.OTHER, "x") }

        assertEquals(setOf(w.id), watches.search("instructions"), "document titles are searchable")
        services.save(entry(w.id, LocalDate(2024, 2, 1), ServiceWork.FULL_SERVICE))
        assertEquals(setOf(w.id), watches.search("mainspring"), "service notes are searchable")
        assertEquals(setOf(w.id), watches.search("sydney"), "who serviced it is searchable")

        documents.update(manual.copy(title = "Owner's manual", kind = DocumentKind.QUICK_START))
        assertEquals(DocumentKind.QUICK_START, documents.get(manual.id)!!.kind)
        documents.delete(manual.id)
        assertTrue(watches.search("instructions").isNullOrEmpty())
    }

    @Test fun guidesCanBeSharedBetweenWatchesAndFollowDuplicates() = runBlocking<Unit> {
        val a = watches.create(watch())
        val b = watches.create(watch(model = "Seamaster 300 Bronze"))
        val manual = documents.add(a.id, pdf, DocumentKind.MANUAL, "Manual")
        documents.add(a.id, jpeg(), DocumentKind.RECEIPT, "Receipt")

        val offered = documents.observeGuidesElsewhere(b.id).first()
        assertEquals(listOf(manual.id), offered.map { it.document.id }, "only guides are offered, not receipts")
        assertEquals("Omega Seamaster 300", offered.single().watchTitle)

        val copied = documents.copyTo(manual.id, b.id)!!
        assertNotEquals(manual.file, copied.file)
        assertTrue(FileSystem.SYSTEM.read(files.resolve(copied.file)) { readByteArray() }.contentEquals(pdf))

        val dup = watches.duplicate(a.id)!!
        val dupDocs = documents.observe(dup.id).first()
        assertEquals(listOf(DocumentKind.MANUAL), dupDocs.map { it.kind }, "duplicates keep the manual, not the receipt")
    }

    @Test fun boxAndPapersChecklistAndWarranty() = runBlocking<Unit> {
        val w = watches.create(watch())
        papers.set(w.id, BoxItem.INNER_BOX, BoxItemState.PRESENT)
        papers.set(w.id, BoxItem.WARRANTY_CARD, BoxItemState.MISSING)
        papers.setNote(w.id, BoxItem.WARRANTY_CARD, "Card lost in move")
        papers.markRestNotApplicable(w.id)
        val items = papers.observe(w.id).first().associateBy { it.item }
        assertEquals(BoxItem.entries.size, items.size)
        assertEquals(BoxItemState.NOT_APPLICABLE, items.getValue(BoxItem.HANG_TAGS).state)
        assertEquals("Card lost in move", items.getValue(BoxItem.WARRANTY_CARD).note)
        papers.set(w.id, BoxItem.WARRANTY_CARD, null)
        assertNull(papers.observe(w.id).first().firstOrNull { it.item == BoxItem.WARRANTY_CARD })

        papers.setWarranty(w.id, LocalDate(2021, 5, 1), 60)
        val updated = watches.get(w.id)!!
        assertEquals(LocalDate(2021, 5, 1), updated.warrantyStart)
        assertEquals(60, updated.warrantyMonths)
    }

    @Test fun pdfsWithBytesBeforeTheHeaderAreAccepted() = runBlocking<Unit> {
        val w = watches.create(watch())
        val scanned = documents.add(w.id, "\r\n\u0000scanner preamble\n".encodeToByteArray() + pdf, DocumentKind.INVOICE, "Scan")
        assertEquals("application/pdf", scanned.mimeType)
    }

    @Test fun heicIsStoredAsJpegAndAFailedConversionLeavesNothing() = runBlocking<Unit> {
        val heic = byteArrayOf(0, 0, 0, 0x18) + "ftypheic".encodeToByteArray() + ByteArray(32)
        val w = watches.create(watch())
        val docDir = files.resolve(FileStore.documentDir(w.id))

        // A HEIC header with no picture behind it can't be decoded anywhere, like a damaged file.
        assertFailsWith<DocumentRepository.UnsupportedDocument> { documents.add(w.id, heic, DocumentKind.RECEIPT, "Receipt") }
        assertTrue(names(docDir).isEmpty(), "no stray files: ${names(docDir)}")
        assertTrue(documents.observe(w.id).first().isEmpty())

        val converting = object : ImageProcessor by testImageProcessor() {
            override suspend fun render(source: Path, crop: NormalizedRect?, maxEdge: Int, quality: Int, dest: Path): ImageSize {
                FileSystem.SYSTEM.write(dest) { write(jpeg()) }
                return ImageSize(40, 30)
            }
        }
        val doc = DocumentRepository(db, files, converting, watches, clock).add(w.id, heic, DocumentKind.RECEIPT, "Receipt")
        assertEquals("image/jpeg", doc.mimeType)
        assertTrue(doc.file.endsWith(".jpg"))
        assertEquals(listOf("${doc.id}.jpg"), names(docDir), "the HEIC original isn't kept")
    }

    @Test fun aPhotoDocumentsLocationIsGoneFromTheStoredFile() = runBlocking<Unit> {
        val w = watches.create(watch())
        val doc = documents.add(w.id, PhotoFixtures.bytes("gps.png"), DocumentKind.RECEIPT, "Receipt")
        assertEquals("image/png", doc.mimeType)
        val kept = files.read(doc.file)
        assertFalse(PhotoMetadata.hasLocation(kept))
        assertEquals(PhotoFixtures.bytes("gps.png").size, kept.size, "otherwise the file that arrived")
    }

    @Test fun aPhotoDocumentInAFormatOnlySomeDevicesShowIsStoredAsAJpeg() = runBlocking<Unit> {
        val w = watches.create(watch())
        // A document's photo comes in the way a watch's does: what this device reads is converted, the rest refused.
        val readable = testImageProcessor().readableFormats
        var kept = 0
        for ((name, format) in listOf("other.gif" to PhotoFormat.GIF, "other.bmp" to PhotoFormat.BMP, "other.tiff" to PhotoFormat.TIFF)) {
            if (format in readable) {
                val doc = documents.add(w.id, PhotoFixtures.bytes(name), DocumentKind.RECEIPT, name)
                assertEquals("image/jpeg", doc.mimeType, name)
                assertTrue(doc.file.endsWith("${doc.id}.jpg"), name)
                kept++
            } else {
                assertFailsWith<DocumentRepository.UnsupportedDocument>(name) { documents.add(w.id, PhotoFixtures.bytes(name), DocumentKind.RECEIPT, name) }
            }
        }
        assertEquals(kept, names(files.resolve(FileStore.documentDir(w.id))).size, "one file for each document kept, and nothing else")
    }

    @Test fun aPhotoDocumentThePlatformStillFindsALocationInIsStoredAsAJpegMadeFromIt() = runBlocking<Unit> {
        val w = watches.create(watch())
        val doubting = object : ImageProcessor by testImageProcessor() {
            override suspend fun hasLocation(file: Path) = true
        }
        val doc = DocumentRepository(db, files, doubting, watches, clock).add(w.id, PhotoFixtures.bytes("gps.png"), DocumentKind.RECEIPT, "Receipt")
        assertEquals("image/jpeg", doc.mimeType)
        assertEquals(listOf("${doc.id}.jpg"), names(files.resolve(FileStore.documentDir(w.id))), "only the JPEG is kept")
        assertFalse(PhotoMetadata.hasLocation(files.read(doc.file)))
    }

    @Test fun aPhotoDocumentThatCantBeCleanedIsStoredAsAJpegMadeFromIt() = runBlocking<Unit> {
        val w = watches.create(watch())
        // Its XMP is marked as compressed, so the location in it can't be overwritten where it lies.
        val png = PhotoFixtures.bytes("gps.png")
        val keyword = "XML:com.adobe.xmp".encodeToByteArray()
        val at = (0..png.size - keyword.size).first { i -> keyword.indices.all { png[i + it] == keyword[it] } }
        png[at + keyword.size + 1] = 1
        // The chunk's checksum follows its data, so the file is still a well-formed PNG.
        val length = (0 until 4).fold(0) { acc, i -> (acc shl 8) or (png[at - 8 + i].toInt() and 0xFF) }
        val crc = io.github.tunlezah.watches.data.backup.Crc32().apply { update(png, at - 4, 4 + length) }.value
        for (i in 0 until 4) png[at + length + i] = (crc shr (24 - 8 * i)).toByte()
        val doc = documents.add(w.id, png, DocumentKind.RECEIPT, "Receipt")
        assertEquals("image/jpeg", doc.mimeType)
        assertEquals(listOf("${doc.id}.jpg"), names(files.resolve(FileStore.documentDir(w.id))), "only the JPEG is kept")
        assertFalse(PhotoMetadata.hasLocation(files.read(doc.file)))
    }

    @Test fun careSkipsWatchesNotOnHand() = runBlocking<Unit> {
        watches.create(watch().copy(status = WatchStatus.WISHLIST))
        val sold = watches.create(watch().copy(status = WatchStatus.SOLD))
        val care = services.observeCare().first()
        assertEquals(2, care.size)
        assertTrue(care.none { it.needsCare })
        assertEquals(WatchStatus.SOLD, care.single { it.id == sold.id }.status)
    }
}
