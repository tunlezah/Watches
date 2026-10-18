package io.github.tunlezah.watches.ui.security

import androidx.compose.ui.window.DialogProperties

internal actual fun coverWindowProperties() = DialogProperties(
    dismissOnBackPress = false,
    dismissOnClickOutside = false,
    usePlatformDefaultWidth = false,
    usePlatformInsets = false,
    animateTransition = false,
)
