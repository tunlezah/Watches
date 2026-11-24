package io.github.tunlezah.watches.ui.platform

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.PlatformFile

/** Opens the system camera. [available] is false where there is no camera to open (a desktop), and screens then leave the button out. */
class CameraLauncher(val available: Boolean, private val open: () -> Unit) {
    fun launch() = open()
}

/** [onResult] gets the photo taken, or null if the camera was closed without one. */
@Composable
expect fun rememberCameraLauncher(onResult: (PlatformFile?) -> Unit): CameraLauncher
