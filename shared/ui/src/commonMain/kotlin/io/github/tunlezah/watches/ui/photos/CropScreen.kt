package io.github.tunlezah.watches.ui.photos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.tunlezah.watches.core.model.NormalizedRect
import io.github.tunlezah.watches.ui.components.ButtonKind
import io.github.tunlezah.watches.ui.components.IconAction
import io.github.tunlezah.watches.ui.components.SelectChip
import io.github.tunlezah.watches.ui.components.WatchButton
import io.github.tunlezah.watches.ui.theme.WatchIcons
import io.github.tunlezah.watches.ui.theme.WatchTheme
import okio.Path.Companion.toPath
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private enum class Aspect(val label: String, val ratio: Float?) { FREE("Free", null), SQUARE("1:1", 1f), PORTRAIT("4:5", 0.8f), CLASSIC("3:4", 0.75f), WIDE("4:3", 4f / 3f) }

private const val MIN_SIZE = 0.1f

/** Fits the largest rect of pixel aspect [ratio] inside the image, centred on [around]. */
private fun fitAspect(ratio: Float, imgW: Int, imgH: Int, around: NormalizedRect): NormalizedRect {
    val imageAspect = imgW.toFloat() / imgH
    // In normalised units, width/height = ratio / imageAspect.
    val k = ratio / imageAspect
    var w = 1f
    var h = w / k
    if (h > 1f) { h = 1f; w = k }
    val cx = (around.left + around.right) / 2
    val cy = (around.top + around.bottom) / 2
    val l = (cx - w / 2).coerceIn(0f, 1f - w)
    val t = (cy - h / 2).coerceIn(0f, 1f - h)
    return NormalizedRect(l, t, l + w, t + h)
}

@Composable
fun CropScreen(state: CropUiState, onBack: () -> Unit, onSave: (NormalizedRect) -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    var rect by remember { mutableStateOf(state.crop) }
    var aspect by remember { mutableStateOf(Aspect.FREE) }
    LaunchedEffect(state.loaded) { if (state.loaded) rect = state.crop }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(WatchIcons.Close, "Cancel", onBack, tint = Color.White)
            Text(t.title("Crop"), style = t.type.groupTitle, color = Color.White, modifier = Modifier.weight(1f).semantics { heading() })
            WatchButton(if (state.saving) "Saving…" else "Done", { onSave(rect) }, kind = ButtonKind.Primary, enabled = state.loaded && !state.saving)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
            if (!state.loaded || state.original == null) return@BoxWithConstraints
            val imageAspect = state.width.toFloat() / state.height
            Box(Modifier.aspectRatio(imageAspect, matchHeightConstraintsFirst = maxWidth / maxHeight > imageAspect)) {
                val model = remember(state.original) { state.original.toPath() }
                AsyncImage(model, contentDescription = "Photo being cropped", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                val handle = with(LocalDensity.current) { 28.dp.toPx() }
                val accent = c.accent
                Canvas(
                    Modifier.fillMaxSize()
                        .semantics { contentDescription = "Crop area. Drag corners to resize, drag inside to move." }
                        .pointerInput(aspect, state.width, state.height) {
                            var mode = 0 // 0 move, 1 TL, 2 TR, 3 BL, 4 BR
                            detectDragGestures(
                                onDragStart = { p ->
                                    val r = rect
                                    val corners = listOf(
                                        Offset(r.left * size.width, r.top * size.height), Offset(r.right * size.width, r.top * size.height),
                                        Offset(r.left * size.width, r.bottom * size.height), Offset(r.right * size.width, r.bottom * size.height),
                                    )
                                    val nearest = corners.withIndex().minBy { (_, o) -> (o - p).getDistance() }
                                    mode = if ((nearest.value - p).getDistance() < handle * 1.5f) nearest.index + 1 else 0
                                },
                                onDrag = { change, drag ->
                                    change.consume()
                                    val dx = drag.x / size.width
                                    val dy = drag.y / size.height
                                    rect = dragRect(rect, mode, dx, dy, aspect.ratio, state.width, state.height)
                                },
                            )
                        },
                ) {
                    val r = Rect(rect.left * size.width, rect.top * size.height, rect.right * size.width, rect.bottom * size.height)
                    val hole = Path().apply { addRect(r) }
                    clipPath(hole, ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.6f)) }
                    drawRect(Color.White, r.topLeft, r.size, style = Stroke(1.dp.toPx()))
                    // Rule-of-thirds guides.
                    for (i in 1..2) {
                        val x = r.left + r.width * i / 3
                        val y = r.top + r.height * i / 3
                        drawLine(Color.White.copy(alpha = 0.35f), Offset(x, r.top), Offset(x, r.bottom), 1f)
                        drawLine(Color.White.copy(alpha = 0.35f), Offset(r.left, y), Offset(r.right, y), 1f)
                    }
                    val hs = 10.dp.toPx()
                    for (corner in listOf(r.topLeft, r.topRight, r.bottomLeft, r.bottomRight)) {
                        drawRect(accent, Offset(corner.x - hs / 2, corner.y - hs / 2), Size(hs, hs))
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp).navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (a in Aspect.entries) {
                SelectChip(a.label, aspect == a, {
                    aspect = a
                    a.ratio?.let { ratio -> rect = fitAspect(ratio, state.width, state.height, rect) }
                }, underline = false)
            }
            WatchButton("Reset", { rect = NormalizedRect.Full; aspect = Aspect.FREE }, kind = ButtonKind.Text)
        }
    }
}

private fun dragRect(r: NormalizedRect, mode: Int, dx: Float, dy: Float, ratio: Float?, imgW: Int, imgH: Int): NormalizedRect {
    if (mode == 0) {
        val l = (r.left + dx).coerceIn(0f, 1f - r.width)
        val t = (r.top + dy).coerceIn(0f, 1f - r.height)
        return NormalizedRect(l, t, l + r.width, t + r.height)
    }
    var l = r.left; var t = r.top; var rt = r.right; var b = r.bottom
    when (mode) {
        1 -> { l += dx; t += dy }
        2 -> { rt += dx; t += dy }
        3 -> { l += dx; b += dy }
        4 -> { rt += dx; b += dy }
    }
    l = l.coerceIn(0f, rt - MIN_SIZE); rt = rt.coerceIn(l + MIN_SIZE, 1f)
    t = t.coerceIn(0f, b - MIN_SIZE); b = b.coerceIn(t + MIN_SIZE, 1f)
    if (ratio != null) {
        // Keep the aspect by deriving height from width, anchored on the opposite corner.
        val k = ratio / (imgW.toFloat() / imgH)
        var w = rt - l
        var h = w / k
        val maxH = if (mode <= 2) b else 1f - t
        if (h > maxH) { h = maxH; w = h * k }
        val maxW = if (mode == 1 || mode == 3) rt else 1f - l
        if (w > maxW) { w = maxW; h = w / k }
        when (mode) {
            1 -> { l = rt - w; t = b - h }
            2 -> { rt = l + w; t = b - h }
            3 -> { l = rt - w; b = t + h }
            4 -> { rt = l + w; b = t + h }
        }
    }
    return runCatching { NormalizedRect(max(0f, l), max(0f, t), min(1f, rt), min(1f, b)) }.getOrDefault(r)
        .takeIf { abs(it.width) >= MIN_SIZE * 0.99f && abs(it.height) >= MIN_SIZE * 0.5f } ?: r
}
