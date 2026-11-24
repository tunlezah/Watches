package io.github.tunlezah.watches.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitOpenCameraSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberCameraPickerLauncher

@Composable
actual fun rememberCameraLauncher(onResult: (PlatformFile?) -> Unit): CameraLauncher {
    val launcher = rememberCameraPickerLauncher(FileKitOpenCameraSettings.createDefault(), onResult)
    return remember(launcher) { CameraLauncher(available = true) { launcher.launch() } }
}
