package io.github.tunlezah.watches.desktop

import io.github.tunlezah.watches.data.files.MacBridge
import java.io.File

/**
 * Where this app keeps the Mac bridge: the native library that lets it use the Mac's own image code
 * (docs/MACOS_PLAN.md, 8.2.6). A packaged Mac app carries it among its resources. A build without
 * one, on Linux or run straight from the source tree, uses the plain-Java image code instead.
 */
internal object Bridge {

    private const val FILE = "libwatchesbridge.dylib"

    /** The folder of files the packaged app carries beside its code. The packaging plugin names it. */
    private fun resources(): File? = System.getProperty("compose.application.resources.dir")?.takeIf { it.isNotBlank() }?.let(::File)

    /** True in a packaged app on a Mac: that one always carries the bridge, so not finding it is a fault. */
    fun expected(): Boolean = System.getProperty("os.name").orEmpty().startsWith("Mac") && resources() != null

    /** Tells the shared code where the library is, before anything asks for the image code. A path given from outside wins. */
    fun locate() {
        if (MacBridge.path() != null) return
        val library = resources()?.resolve(FILE)?.takeIf { it.isFile } ?: return
        System.setProperty(MacBridge.PROPERTY, library.absolutePath)
    }
}
