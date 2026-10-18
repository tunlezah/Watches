package io.github.tunlezah.watches.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import io.github.tunlezah.watches.ui.theme.MotionFeel
import io.github.tunlezah.watches.ui.theme.WatchTheme

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

/**
 * A pressed card or tile sinks slightly, in each design's own way: Atelier barely and slowly,
 * Instrument in a short linear step, Gallery on a soft spring. Still when motion is reduced.
 */
@Composable
fun Modifier.pressScale(interaction: InteractionSource): Modifier {
    if (!LocalAnimationsEnabled.current) return this
    val pressed by interaction.collectIsPressedAsState()
    val (depth, spec) = when (WatchTheme.style.motion) {
        MotionFeel.QUIET -> 0.985f to tween<Float>(220, easing = FastOutSlowInEasing)
        MotionFeel.PRECISE -> 0.98f to tween<Float>(90, easing = LinearEasing)
        MotionFeel.SOFT -> 0.965f to spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
    }
    val scale by animateFloatAsState(if (pressed) depth else 1f, spec, label = "press")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** A placeholder block while content loads: a soft pulse, or a still block when motion is reduced. */
@Composable
fun Modifier.skeleton(shape: Shape): Modifier {
    val base = clip(shape).background(WatchTheme.colors.surface2)
    if (!LocalAnimationsEnabled.current) return base
    val alpha by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "pulse",
    )
    return graphicsLayer { this.alpha = alpha }.then(base)
}
