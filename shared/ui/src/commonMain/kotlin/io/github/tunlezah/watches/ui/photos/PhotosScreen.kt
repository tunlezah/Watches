package io.github.tunlezah.watches.ui.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.tunlezah.watches.ui.components.ButtonKind
import io.github.tunlezah.watches.ui.components.ConfirmDialog
import io.github.tunlezah.watches.ui.components.EmptyState
import io.github.tunlezah.watches.ui.components.IconAction
import io.github.tunlezah.watches.ui.components.WatchButton
import io.github.tunlezah.watches.ui.components.WatchLook
import io.github.tunlezah.watches.ui.components.WatchPhoto
import io.github.tunlezah.watches.ui.theme.WatchIcons
import io.github.tunlezah.watches.ui.theme.WatchTheme
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitOpenCameraSettings
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberCameraPickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher

@Composable
fun PhotosScreen(
    state: PhotosUiState,
    onBack: () -> Unit,
    onAdd: (List<PlatformFile>) -> Unit,
    onSetCover: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onCrop: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismissMessage: () -> Unit,
) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    val haptics = LocalHapticFeedback.current
    var selected by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    val gallery = rememberFilePickerLauncher(type = FileKitType.Image, mode = FileKitMode.Multiple(maxItems = 20)) { files ->
        files?.let(onAdd)
    }
    val camera = rememberCameraPickerLauncher(FileKitOpenCameraSettings.createDefault()) { file ->
        file?.let { onAdd(listOf(it)) }
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(WatchIcons.Back, "Back", onBack)
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(t.title("Photos"), style = t.type.groupTitle, color = c.fg, modifier = Modifier.semantics { heading() })
                if (state.title.isNotEmpty()) Text(state.title, style = t.type.bodySmall, color = c.fg3, maxLines = 1)
            }
        }
        Row(Modifier.padding(horizontal = style.screenPadding, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WatchButton("Camera", { camera.launch() }, Modifier.weight(1f), icon = WatchIcons.Camera, kind = ButtonKind.Primary)
            WatchButton("Gallery", { gallery.launch() }, Modifier.weight(1f), icon = WatchIcons.Image)
        }
        if (state.importing > 0) {
            Column(Modifier.padding(horizontal = style.screenPadding, vertical = 6.dp)) {
                Text("Adding ${state.importing} ${if (state.importing == 1) "photo" else "photos"}…", style = t.type.bodySmall, color = c.fg2)
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp), color = c.accent, trackColor = c.line)
            }
        }
        state.message?.let { msg ->
            Row(Modifier.padding(horizontal = style.screenPadding, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(msg, style = t.type.bodySmall, color = c.danger, modifier = Modifier.weight(1f))
                WatchButton("OK", onDismissMessage, kind = ButtonKind.Text)
            }
        }
        if (state.loaded && state.photos.isEmpty() && state.importing == 0) {
            EmptyState("No photos yet", "Take a photo or add some from your gallery. The first one becomes the cover.", Modifier.fillMaxWidth())
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(horizontal = style.screenPadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            itemsIndexed(state.photos, key = { _, p -> p.id }) { index, photo ->
                val isSelected = photo.id == selected
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clip(style.photoShape)
                        .then(if (isSelected) Modifier.border(2.dp, c.accent, style.photoShape) else Modifier)
                        .clickable(role = Role.Checkbox, onClickLabel = if (isSelected) "Deselect" else "Select") {
                            selected = if (isSelected) null else photo.id
                        }
                        .semantics {
                            this.selected = isSelected
                            contentDescription = "Photo ${index + 1}${if (photo.isCover) ", cover" else ""}${if (photo.cropped) ", cropped" else ""}"
                        },
                ) {
                    WatchPhoto(photo.thumb, WatchLook.guess(null, null, emptySet()), "", Modifier.fillMaxSize())
                    if (photo.isCover) {
                        Box(Modifier.align(Alignment.TopStart).padding(6.dp).size(26.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                            Icon(WatchIcons.StarFilled, null, tint = c.onAccent, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
        val sel = state.photos.firstOrNull { it.id == selected }
        if (sel != null) {
            val index = state.photos.indexOf(sel)
            FlowRow(
                Modifier.fillMaxWidth().background(c.surface).padding(horizontal = style.screenPadding, vertical = 10.dp).navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconAction(WatchIcons.Star, "Set as cover", { onSetCover(sel.id); haptics.performHapticFeedback(HapticFeedbackType.LongPress) }, enabled = !sel.isCover)
                IconAction(WatchIcons.Left, "Move earlier", { onMove(sel.id, -1) }, enabled = index > 0)
                IconAction(WatchIcons.Right, "Move later", { onMove(sel.id, 1) }, enabled = index < state.photos.lastIndex)
                IconAction(WatchIcons.Crop, "Crop", { onCrop(sel.id) })
                IconAction(WatchIcons.Trash, "Delete photo", { confirmDelete = sel.id }, tint = c.danger)
            }
        }
    }

    confirmDelete?.let { id ->
        ConfirmDialog(
            title = "Delete this photo?",
            message = "The original and its edits are removed from this phone.",
            confirm = "Delete",
            destructive = true,
            onConfirm = { onDelete(id); if (selected == id) selected = null; confirmDelete = null },
            onDismiss = { confirmDelete = null },
        )
    }
}
