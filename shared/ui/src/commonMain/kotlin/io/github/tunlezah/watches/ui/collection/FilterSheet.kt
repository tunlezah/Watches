package io.github.tunlezah.watches.ui.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.tunlezah.watches.core.format.Numbers
import io.github.tunlezah.watches.core.model.Complication
import io.github.tunlezah.watches.core.model.MovementType
import io.github.tunlezah.watches.core.query.CollectionFilter
import io.github.tunlezah.watches.core.query.SortOrder
import io.github.tunlezah.watches.core.query.SortSpec
import io.github.tunlezah.watches.ui.components.ButtonKind
import io.github.tunlezah.watches.ui.components.ChipGroup
import io.github.tunlezah.watches.ui.components.FieldLabel
import io.github.tunlezah.watches.ui.components.SelectChip
import io.github.tunlezah.watches.ui.components.WatchButton
import io.github.tunlezah.watches.ui.theme.WatchIcons
import io.github.tunlezah.watches.ui.theme.WatchTheme
import kotlin.math.floor
import kotlin.math.ceil

@Composable
fun FilterSheet(
    state: CollectionUiState,
    onSort: (SortSpec) -> Unit,
    onApply: (CollectionFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    var draft by remember { mutableStateOf(state.filter) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { onApply(draft); onDismiss() }, sheetState = sheetState, containerColor = c.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(t.title("Sort and filter"), style = t.type.groupTitle, color = c.fg)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Sort by")
                ChipGroup(
                    SortOrder.entries.map { it.key to it.label },
                    setOf(state.sort.order.key),
                    { key -> onSort(state.sort.copy(order = SortOrder.entries.first { it.key == key })) },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SelectChip(
                        if (state.sort.reversed) "Reversed order" else "Normal order",
                        state.sort.reversed,
                        { onSort(state.sort.copy(reversed = !state.sort.reversed)) },
                        underline = false,
                    )
                }
            }

            if (state.brands.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel("Brand")
                    ChipGroup(state.brands.map { it to it }, draft.brands, { b -> draft = draft.copy(brands = draft.brands.toggle(b)) })
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Movement")
                ChipGroup(
                    MovementType.entries.map { it.key to it.label },
                    draft.movementTypes.mapTo(mutableSetOf()) { it.key },
                    { k -> draft = draft.copy(movementTypes = draft.movementTypes.toggle(MovementType.entries.first { it.key == k })) },
                )
            }

            if (state.complications.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel("Complications")
                    ChipGroup(
                        state.complications.map { it to Complication.labelFor(it) },
                        draft.complications,
                        { k -> draft = draft.copy(complications = draft.complications.toggle(k)) },
                    )
                }
            }

            state.sizeBounds?.let { bounds ->
                val lo = floor(bounds.start).toFloat()
                val hi = ceil(bounds.endInclusive).toFloat().coerceAtLeast(lo + 1f)
                val current = (draft.minDiameterMm?.toFloat() ?: lo)..(draft.maxDiameterMm?.toFloat() ?: hi)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row {
                        FieldLabel("Case size", Modifier.weight(1f))
                        Text("${Numbers.decimal(current.start.toDouble(), 1)}–${Numbers.decimal(current.endInclusive.toDouble(), 1)} mm", style = t.type.specValue, color = c.fg)
                    }
                    RangeSlider(
                        value = current,
                        onValueChange = { r ->
                            draft = draft.copy(
                                minDiameterMm = (Math.round(r.start * 2) / 2.0).takeIf { it > lo },
                                maxDiameterMm = (Math.round(r.endInclusive * 2) / 2.0).takeIf { it < hi },
                            )
                        },
                        valueRange = lo..hi,
                        colors = SliderDefaults.colors(activeTrackColor = c.accent, thumbColor = c.accent, inactiveTrackColor = c.line),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WatchButton("Reset", { draft = CollectionFilter(tab = draft.tab) }, Modifier.weight(1f))
                WatchButton("Show results", { onApply(draft); onDismiss() }, Modifier.weight(1f), kind = ButtonKind.Primary, icon = WatchIcons.Check)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun <T> Set<T>.toggle(v: T): Set<T> = if (v in this) this - v else this + v

private object Math {
    fun round(v: Float): Long = kotlin.math.round(v).toLong()
}
