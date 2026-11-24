package io.github.tunlezah.watches.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.vinceglb.filekit.PlatformFile

/** FileKit's camera launcher exists only on phones. A desktop has no camera app to hand over to. */
@Composable
actual fun rememberCameraLauncher(onResult: (PlatformFile?) -> Unit): CameraLauncher =
    remember { CameraLauncher(available = false) { } }
