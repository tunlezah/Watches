package io.github.tunlezah.watches.desktop

import io.github.tunlezah.watches.core.model.Watch
import io.github.tunlezah.watches.data.PhotoRepository
import io.github.tunlezah.watches.data.WatchRepository
import io.github.tunlezah.watches.data.backup.BackupRepository
import io.github.tunlezah.watches.data.backup.CsvExporter
import io.github.tunlezah.watches.data.backup.RestoreMode
import io.github.tunlezah.watches.data.files.FileStore
import io.github.tunlezah.watches.data.files.MacBridge
import io.github.tunlezah.watches.data.photos.PhotoMetadata
import io.github.tunlezah.watches.search.describeSecureConnections
import io.github.tunlezah.watches.ui.diagnostics.SampleGenerator
import io.github.tunlezah.watches.ui.platform.platformLimits
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO

/**
 * `Watches --self-test [folder]`: checks that this build works, without opening a window, and exits
 * 0 or 1. CI runs it inside the packaged app, because some faults exist only there: the bundled Java
 * runtime is trimmed to the modules the app is thought to need, and a missing one shows only when
 * the code that wants it runs. It works in a temporary folder (or the one given) and never touches
 * the real collection. It makes no network request.
 */
internal object SelfTest {

    fun run(dir: File?): Int = try {
        check(dir ?: Files.createTempDirectory("watches-self-test").toFile())
        println("self-test: passed")
        0
    } catch (e: Throwable) {
        println("self-test: FAILED")
        e.printStackTrace(System.out)
        1
    }

    private fun check(dir: File) {
        dir.mkdirs()
        step("version") {
            require(Regex("""\d+\.\d+\.\d+""").matches(BuildInfo.VERSION)) { "Not a version: ${BuildInfo.VERSION}" }
            "${BuildInfo.VERSION} (${BuildInfo.BUILD}) on ${AppPaths.platformName()}, Java ${Runtime.version()}"
        }
        // Lookup needs modern TLS, which is a module of its own in a trimmed Java runtime.
        step("secure connections") { describeSecureConnections() }

        val koin = startKoin { modules(desktopModules(dir.path)) }.koin
        try {
            val watches = koin.get<WatchRepository>()
            val backup = koin.get<BackupRepository>()
            step("collection") {
                // Sample watches go through the real photo pipeline: database, files, renditions.
                val samples = koin.get<SampleGenerator>()
                samples.generate(3)
                runBlocking {
                    delay(100)
                    samples.running.first { !it }
                }
                require(samples.status.value?.startsWith("Added") == true) { "Samples: ${samples.status.value}" }
                runBlocking { watches.ensureSearchIndex() }
                samples.status.value.orEmpty()
            }
            step("photos") {
                if (Bridge.expected()) {
                    MacBridge.problem?.let { throw IllegalStateException("The Mac bridge didn't load", it) }
                    require(MacBridge.isLoaded) { "This app has no Mac bridge among its resources" }
                    // With the bridge, the screens offer photos as they do on the phones.
                    require(platformLimits.photos == null) { "Photos are still switched off: ${platformLimits.photos}" }
                }
                val photos = koin.get<PhotoRepository>()
                val files = koin.get<FileStore>()
                val now = kotlin.time.Clock.System.now()
                val watch = runBlocking { watches.create(Watch(id = "", brand = "Self-test", createdAt = now, updatedAt = now)) }
                // A portrait photo with a location, stored on its side as a phone's camera writes it. And a HEIC
                // with a location, which only the Mac's own image code can read.
                val added = listOfNotNull("located-turned.jpg", "located.heic".takeIf { MacBridge.isLoaded }).map { name ->
                    val arrived = javaClass.getResourceAsStream("/self-test/$name")?.use { it.readBytes() } ?: error("The app is missing /self-test/$name")
                    require(PhotoMetadata.hasLocation(arrived)) { "$name should carry a location" }
                    val photo = runBlocking { photos.add(watch.id, arrived.copyOf()) }
                    val kept = runBlocking { files.read(photo.originalFile) }
                    require(!PhotoMetadata.hasLocation(kept)) { "$name was stored with its location" }
                    require(photo.width == 64 && photo.height == 48) { "$name is 64 by 48 upright, not ${photo.width} by ${photo.height}" }
                    val thumb = ImageIO.read(File(files.absolute(photo.thumbFile))) ?: error("$name: its thumbnail isn't a readable JPEG")
                    require(thumb.width == 64 && thumb.height == 48) { "$name: its thumbnail is ${thumb.width} by ${thumb.height}" }
                    // The reference picture has a red top left corner and a yellow bottom right one.
                    val (red, yellow) = thumb.getRGB(8, 8) to thumb.getRGB(56, 40)
                    require((red shr 16 and 0xFF) > 140 && (red and 0xFF) < 100) { "$name: its top left isn't red (%06x)".format(red and 0xFFFFFF) }
                    require((yellow shr 16 and 0xFF) > 150 && (yellow shr 8 and 0xFF) > 140 && (yellow and 0xFF) < 110) { "$name: its bottom right isn't yellow (%06x)".format(yellow and 0xFFFFFF) }
                    name
                }
                runBlocking { watches.delete(watch.id) }
                val codec = if (MacBridge.isLoaded) "the Mac's own image code, through the bridge" else "plain-Java image code: no Mac bridge in this build"
                "$codec; ${added.joinToString(" and ")} stored upright without a location"
            }
            step("backup and restore") {
                val archive = File(dir, "self-test-backup.zip").toOkioPath()
                val written = runBlocking { backup.export(FileSystem.SYSTEM.sink(archive)) }
                val preview = runBlocking { backup.inspect { FileSystem.SYSTEM.source(archive) } }
                require(preview.manifest.app.contains(BuildInfo.VERSION)) { "The backup doesn't name this version: ${preview.manifest.app}" }
                val report = runBlocking { backup.restore({ FileSystem.SYSTEM.source(archive) }, preview, RestoreMode.MERGE) }
                require(report.watchesAdded == 0 && report.updated.isEmpty()) { "Merging a backup into itself changed something: $report" }
                "${written.counts.watches} watches and ${written.counts.photos} photos, ${written.bytes} bytes, made by ${preview.manifest.app}"
            }
            step("spreadsheet") {
                val rows = runBlocking { koin.get<CsvExporter>().export(FileSystem.SYSTEM.sink(File(dir, "self-test.csv").toOkioPath())) }
                "$rows rows"
            }
            step("drawing") {
                val png = Offscreen.renderApp(width = 1000, height = 700)
                File(dir, "self-test.png").writeBytes(png)
                val colours = distinctColours(png)
                // A window that drew nothing, or only its background, has a handful of colours at most.
                require(colours > 64) { "The app drew only $colours colours" }
                "$colours colours, saved as self-test.png"
            }
        } finally {
            stopKoin()
        }
    }

    private fun distinctColours(png: ByteArray): Int {
        val image = ImageIO.read(ByteArrayInputStream(png)) ?: error("The drawing isn't a readable PNG")
        val seen = HashSet<Int>()
        for (y in 0 until image.height step 3) for (x in 0 until image.width step 3) seen += image.getRGB(x, y)
        return seen.size
    }

    private fun step(name: String, block: () -> String) {
        val detail = try {
            block()
        } catch (e: Throwable) {
            println("self-test: $name FAILED")
            throw e
        }
        println("self-test: $name ok ($detail)")
    }
}
