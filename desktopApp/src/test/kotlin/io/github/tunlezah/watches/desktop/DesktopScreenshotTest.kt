package io.github.tunlezah.watches.desktop

import io.github.tunlezah.watches.core.settings.Accent
import io.github.tunlezah.watches.core.settings.Direction
import io.github.tunlezah.watches.core.settings.ThemeMode
import io.github.tunlezah.watches.data.SettingsRepository
import io.github.tunlezah.watches.ui.diagnostics.SampleGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Draws the app as the Mac shows it, in every design, light and dark, at three window widths: the
 * phone layout, the rail, and the two panes. The pictures go to build/outputs/screenshots, which CI
 * keeps for review like the Android ones. They aren't compared with stored images; the test only
 * checks that each design and mode really drew something of its own.
 */
class DesktopScreenshotTest {

    private data class Size(val name: String, val width: Int, val height: Int, val density: Float)

    private val sizes = listOf(Size("narrow", 420, 820, 2f), Size("medium", 760, 800, 1f), Size("wide", 1280, 800, 2f))

    @Test fun everyDesignLightAndDarkAtThreeWidths() {
        val out = File(System.getProperty("watches.screenshots") ?: "build/outputs/screenshots").apply { mkdirs() }
        val koin = startKoin { modules(desktopModules(Files.createTempDirectory("watches-screenshots").toString())) }.koin
        try {
            val samples = koin.get<SampleGenerator>()
            samples.generate(12)
            runBlocking {
                delay(100)
                samples.running.first { !it }
            }
            val settings = koin.get<SettingsRepository>()
            val grounds = mutableMapOf<String, Int>()
            for (direction in Direction.entries) for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                runBlocking { settings.update { it.copy(direction = direction, accent = Accent.defaultFor(direction), themeMode = mode) } }
                for (size in sizes) {
                    val png = Offscreen.renderApp(size.width, size.height, size.density, frames = 30)
                    File(out, "collection-${direction.key}-${mode.key}-${size.name}.png").writeBytes(png)
                    val image = ImageIO.read(ByteArrayInputStream(png))
                    assertEquals((size.width * size.density).toInt(), image.width)
                    // The ground colour, away from any content: bottom right of the widest picture.
                    if (size.name == "wide") grounds["${direction.key}-${mode.key}"] = image.getRGB(image.width - 8, image.height - 8)
                }
            }
            // Each design's light ground differs from its dark one, and the three dark grounds differ from each other.
            for (direction in Direction.entries) assertNotEquals(grounds["${direction.key}-light"], grounds["${direction.key}-dark"], direction.key)
            assertEquals(3, Direction.entries.map { grounds["${it.key}-dark"] }.toSet().size, "three designs, three dark grounds")
        } finally {
            stopKoin()
        }
    }
}
