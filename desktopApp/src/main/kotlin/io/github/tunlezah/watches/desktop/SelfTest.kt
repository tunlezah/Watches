package io.github.tunlezah.watches.desktop

import io.github.tunlezah.watches.data.WatchRepository
import io.github.tunlezah.watches.data.backup.BackupRepository
import io.github.tunlezah.watches.data.backup.CsvExporter
import io.github.tunlezah.watches.data.backup.RestoreMode
import io.github.tunlezah.watches.search.describeSecureConnections
import io.github.tunlezah.watches.ui.diagnostics.SampleGenerator
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
