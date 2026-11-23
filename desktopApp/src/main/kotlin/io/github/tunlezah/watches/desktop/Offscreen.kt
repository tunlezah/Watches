package io.github.tunlezah.watches.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.github.tunlezah.watches.ui.WatchesApp
import org.jetbrains.skia.EncodedImageFormat
import javax.swing.SwingUtilities

/**
 * Draws the whole app without a window: what a window of the given size would show, as a PNG. The
 * self-test uses it to prove a packaged build can draw, and the tests use it for screenshots on a
 * machine with no display. Koin must already be started.
 */
internal object Offscreen {

    /** [width] and [height] are in dp; the picture is [density] times that in pixels. */
    fun renderApp(width: Int, height: Int, density: Float = 1f, frames: Int = 40): ByteArray {
        // The view models and Compose's lifecycle both live on the main thread, which on a desktop is Swing's.
        val owner = onMain { Owner().apply { registry.currentState = Lifecycle.State.RESUMED } }
        val scene = onMain {
            ImageComposeScene(width = (width * density).toInt(), height = (height * density).toInt(), density = Density(density)) {
                CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalViewModelStoreOwner provides owner) { WatchesApp() }
            }
        }
        try {
            var png: ByteArray? = null
            val start = System.nanoTime()
            // Settings, the collection and its photos arrive over the first frames, as they do in a window.
            repeat(frames) {
                png = onMain { scene.render(System.nanoTime() - start).encodeToData(EncodedImageFormat.PNG)?.bytes }
                Thread.sleep(40)
            }
            return checkNotNull(png) { "Nothing was drawn" }
        } finally {
            onMain {
                scene.close()
                owner.registry.currentState = Lifecycle.State.DESTROYED
                owner.viewModelStore.clear()
            }
        }
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private class Owner : LifecycleOwner, ViewModelStoreOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
        override val viewModelStore = ViewModelStore()
    }
}
