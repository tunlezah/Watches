package io.github.tunlezah.watches.data

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
import io.github.tunlezah.watches.data.backup.BackupRepository
import io.github.tunlezah.watches.data.backup.CollectionData
import io.github.tunlezah.watches.data.files.FileStore
import kotlinx.datetime.LocalDate
import okio.FileSystem
import okio.Path
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Installs of the app for the backup tests, each with its own database and files folder, on one clock. */
class TestPhones {
    var now = Instant.fromEpochMilliseconds(1_790_000_000_000)
    val clock = TimeSource { now.also { now += 1.seconds } }
    private val made = mutableListOf<Phone>()
    private val scratch = mutableListOf<Path>()

    fun new(): Phone = Phone().also { made += it }

    /** A folder of its own, removed with the phones. */
    fun scratchDir(): Path = newTestDir().also { scratch += it }

    fun close() {
        made.forEach { it.db.close() }
        (made.map { it.dir } + scratch).forEach { FileSystem.SYSTEM.deleteRecursively(it) }
    }

    fun jpeg(seed: Int, w: Int = 64, h: Int = 48): ByteArray = testJpeg(w, h, seed * 40 % 255, 90, 160)

    val pdf = "%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\ntrailer << /Root 1 0 R >>\n%%EOF\n".encodeToByteArray()

    /** One installation: its own database and files folder. */
    inner class Phone {
        val dir: Path = newTestDir()
        val db = openTestDatabase(dir / "test.db")
        val files = FileStore(dir / "files")
        val processor = testImageProcessor()
        val watches = WatchRepository(db, files, clock)
        val photos = PhotoRepository(db, files, processor, clock)
        val documents = DocumentRepository(db, files, processor, watches, clock)
        val services = ServiceRepository(db, watches, documents, files, clock)
        val papers = BoxPapersRepository(db, watches)
        val lookup = LookupRepository(db, files, clock)
        val settings = SettingsRepository(db.settingDao())
        val backup = BackupRepository(db, files, watches, photos, AppInfo("test", "tests"), clock)

        suspend fun everything(): CollectionData {
            val d = db.backupDao()
            return CollectionData(
                d.watches(), d.complications(), d.provenance(), d.otherSpecs(), d.photos(), d.serviceEntries(),
                d.boxPapers(), d.documents(), d.snapshots(), d.settings(),
            )
        }

        /** Every photo, document and snapshot file, by its path under the files folder, with a hash of its bytes. */
        fun mediaFiles(): Map<String, String> {
            val root = dir / "files"
            if (!FileSystem.SYSTEM.exists(root)) return emptyMap()
            return FileSystem.SYSTEM.listRecursively(root)
                .filter { FileSystem.SYSTEM.metadata(it).isRegularFile }
                .map { it.relativeTo(root) }
                .filter { it.segments.first() in setOf("photos", "documents", "snapshots") }
                .associate { it.toString() to FileSystem.SYSTEM.read(root / it) { readByteString() }.sha256().hex() }
        }

        suspend fun archive(): Path {
            val out = dir / "backup.zip"
            backup.export(FileSystem.SYSTEM.sink(out))
            return out
        }

        /** A collection that touches every table and every kind of file. */
        suspend fun seed(): List<Watch> {
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
    }
}
