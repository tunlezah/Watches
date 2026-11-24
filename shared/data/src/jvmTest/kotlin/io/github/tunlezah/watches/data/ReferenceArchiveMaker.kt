package io.github.tunlezah.watches.data

import io.github.tunlezah.watches.core.model.DocumentKind
import io.github.tunlezah.watches.core.model.NormalizedRect
import io.github.tunlezah.watches.core.model.Watch
import io.github.tunlezah.watches.data.backup.AppInfo
import io.github.tunlezah.watches.data.backup.BackupRepository
import io.github.tunlezah.watches.data.files.MacBridge
import io.github.tunlezah.watches.data.photos.PhotoFixtures
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.Test

/**
 * Makes the backup that every platform's tests restore (ReferenceArchiveTest, and
 * docs/MACOS_PLAN.md, 9.5): a collection whose photos were taken in, cropped and rendered by the
 * Mac's own image code, through the bridge, exactly as the Mac app does it.
 *
 * It only runs when asked: the macOS workflow sets WATCHES_REFERENCE_ARCHIVE to the file to write,
 * and keeps the result. The one in `src/commonTest/fixtures/archives` came from there. To renew
 * it, download that run's `reference-archive` and commit it.
 */
class ReferenceArchiveMaker {

    @Test fun make() = runBlocking<Unit> {
        val out = System.getenv("WATCHES_REFERENCE_ARCHIVE")?.takeIf { it.isNotBlank() } ?: return@runBlocking
        check(MacBridge.isLoaded) { "The reference archive is made by the Mac's own image code: give the tests the bridge (WATCHES_BRIDGE)" }
        val phones = TestPhones()
        try {
            val mac = phones.new()
            val watch = mac.watches.create(Watch(id = "", brand = "Omega", modelName = "Reference archive", createdAt = phones.now, updatedAt = phones.now))

            // A HEIC as it comes from an iPhone's camera roll in every way that matters here: the picture
            // stored on its side, its turning in the container. Kept as a HEIC.
            mac.photos.add(watch.id, PhotoFixtures.bytes("large-turned-6.heic"))
            // A JPEG stored on its side, with a location, cropped to its bottom right quarter.
            val cropped = mac.photos.add(watch.id, PhotoFixtures.bytes("gps-exif-mm.jpg"))
            mac.photos.crop(cropped.id, NormalizedRect(0.5f, 0.5f, 1f, 1f))
            // A PNG that is transparent on its right half.
            mac.photos.add(watch.id, PhotoFixtures.bytes("alpha.png"))
            // A TIFF, which no phone stores: the Mac keeps a JPEG made from it.
            mac.photos.add(watch.id, PhotoFixtures.bytes("other.tiff"))

            mac.documents.add(watch.id, phones.pdf, DocumentKind.MANUAL, "Manual")
            // A photographed receipt, as a HEIC with a location: kept as a JPEG without one.
            mac.documents.add(watch.id, PhotoFixtures.bytes("gps.heic"), DocumentKind.RECEIPT, "Receipt")

            val file = out.toPath()
            file.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
            val backup = BackupRepository(mac.db, mac.files, mac.watches, mac.photos, AppInfo("reference archive", "macOS ${System.getProperty("os.version")}"), phones.clock)
            val written = backup.export(FileSystem.SYSTEM.sink(file))
            println("ReferenceArchiveMaker: wrote $out, ${written.bytes} bytes: ${written.counts}")
        } finally {
            phones.close()
        }
    }
}
