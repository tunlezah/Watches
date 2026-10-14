package io.github.tunlezah.watches.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** Set by the app shell; null in previews, tests, two-pane layouts and when animations are off. */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The navigation entry's animation scope, provided per entry by the app shell. */
val LocalEntryAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** False when the system asks for reduced motion. */
val LocalAnimationsEnabled = staticCompositionLocalOf { true }

/** Shared-element hook for a watch's lead photo: the grid tile flies into the detail hero. */
@Composable
fun Modifier.sharedWatchPhoto(watchId: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalEntryAnimatedScope.current ?: return this
    return with(shared) {
        this@sharedWatchPhoto.sharedElement(
            sharedContentState = rememberSharedContentState(key = "photo-$watchId"),
            animatedVisibilityScope = animated,
        )
    }
}
