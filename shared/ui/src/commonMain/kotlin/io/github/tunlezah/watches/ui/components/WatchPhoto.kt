package io.github.tunlezah.watches.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.tunlezah.watches.ui.theme.WatchTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import okio.Path.Companion.toPath

/** The studio backdrop photos and illustrations sit on. */
@Composable
fun Modifier.stage(): Modifier {
    val c = WatchTheme.colors
    return this.background(Brush.radialGradient(listOf(c.stageInner, c.stageOuter)))
}

/**
 * A watch photo from local storage, or the drawn stand-in when there's no photo.
 * Images only ever come from app-private files: this app's image loader has no network fetcher.
 */
@Composable
fun WatchPhoto(
    path: String?,
    look: WatchLook,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    illustrationPadding: Dp = 14.dp,
) {
    val shadow = WatchTheme.colors.shadow
    val model = remember(path) { path?.toPath() }
    // Plain AsyncImage (no subcomposition) keeps long grids cheap; a failed load falls back to the drawing.
    var failed by remember(path) { mutableStateOf(false) }
    Box(modifier.stage(), contentAlignment = Alignment.Center) {
        if (model == null || failed) {
            WatchIllustration(look, shadow, contentDescription, Modifier.fillMaxSize().padding(illustrationPadding))
        } else {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                onError = { failed = true },
            )
        }
    }
}
