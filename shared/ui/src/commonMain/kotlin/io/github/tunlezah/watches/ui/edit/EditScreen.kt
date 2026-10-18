package io.github.tunlezah.watches.ui.edit

import androidx.compose.foundation.background
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.tunlezah.watches.core.model.Complication
import io.github.tunlezah.watches.core.model.Currencies
import io.github.tunlezah.watches.core.spec.SpecField
import io.github.tunlezah.watches.core.spec.SpecFields
import io.github.tunlezah.watches.core.spec.SpecGroup
import io.github.tunlezah.watches.core.spec.SpecKind
import io.github.tunlezah.watches.core.spec.SpecValue
import io.github.tunlezah.watches.core.settings.Direction
import io.github.tunlezah.watches.ui.components.ButtonKind
import io.github.tunlezah.watches.ui.components.ChipGroup
import io.github.tunlezah.watches.ui.components.ConfirmDialog
import io.github.tunlezah.watches.ui.components.FieldLabel
import io.github.tunlezah.watches.ui.components.IconAction
import io.github.tunlezah.watches.ui.components.SelectChip
import io.github.tunlezah.watches.ui.components.WatchButton
import io.github.tunlezah.watches.ui.components.WatchTextField
import io.github.tunlezah.watches.ui.theme.WatchIcons
import io.github.tunlezah.watches.ui.theme.WatchTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Fields laid out per section; fields in one inner list share a row. */
private val layout: List<Pair<SpecGroup, List<List<SpecField>>>> = with(SpecFields) {
    listOf(
        SpecGroup.IDENTITY to listOf(listOf(Brand), listOf(Collection), listOf(ModelName), listOf(Reference), listOf(Nickname),
            listOf(YearFrom, YearTo), listOf(LimitedNumber, LimitedTotal)),
        SpecGroup.CASE to listOf(listOf(CaseMaterial), listOf(Diameter, Thickness), listOf(LugToLug, LugWidth), listOf(Bezel),
            listOf(Crystal), listOf(CaseBack), listOf(Crown), listOf(WaterResistance)),
        SpecGroup.DIAL to listOf(listOf(DialColour), listOf(DialFinish), listOf(Indices), listOf(Hands), listOf(Lume)),
        SpecGroup.MOVEMENT to listOf(listOf(Caliber), listOf(BaseCaliber), listOf(MovementTypeField), listOf(Jewels, Frequency),
            listOf(PowerReserve), listOf(Hacking, HandWinding), listOf(Complications)),
        SpecGroup.STRAP to listOf(listOf(StrapType), listOf(StrapMaterial), listOf(Clasp), listOf(FittedStrap)),
        SpecGroup.OWNERSHIP to listOf(listOf(AcquiredOn), listOf(AcquiredFrom), listOf(PricePaid), listOf(ConditionField),
            listOf(SerialNumber), listOf(PrivateNotes), listOf(Story)),
        SpecGroup.SERVICE to listOf(listOf(ServiceInterval), listOf(WarrantyStart, WarrantyLength)),
    )
}

/** One-tap answers for free-text fields collectors fill most. */
private val suggestions: Map<String, List<String>> = mapOf(
    SpecFields.CaseMaterial.key to listOf("Stainless steel", "Titanium", "Yellow gold", "Bronze", "Ceramic"),
    SpecFields.Crystal.key to listOf("Sapphire", "Hesalite", "Mineral", "Domed sapphire"),
    SpecFields.CaseBack.key to listOf("Solid", "Exhibition", "Screw-down"),
    SpecFields.Bezel.key to listOf("Fixed", "Unidirectional diving", "Bidirectional", "Tachymeter", "GMT 24-hour"),
    SpecFields.DialColour.key to listOf("Black", "White", "Blue", "Silver", "Green", "Grey"),
    SpecFields.Lume.key to listOf("Super-LumiNova", "Tritium", "None"),
    SpecFields.StrapType.key to listOf("Bracelet", "Leather", "Rubber", "NATO", "Fabric"),
    SpecFields.Clasp.key to listOf("Folding clasp", "Pin buckle", "Deployant"),
)

private val placeholders: Map<String, String> = mapOf(
    SpecFields.Brand.key to "Omega",
    SpecFields.ModelName.key to "Speedmaster Professional",
    SpecFields.Reference.key to "310.30.42.50.01.001",
    SpecFields.Caliber.key to "3861",
    SpecFields.AcquiredFrom.key to "Boutique, dealer, private sale…",
)

private val wordFields = setOf(
    SpecFields.Brand.key, SpecFields.Collection.key, SpecFields.ModelName.key, SpecFields.Nickname.key,
    SpecFields.CaseMaterial.key, SpecFields.AcquiredFrom.key, SpecFields.StrapMaterial.key,
)
private val codeFields = setOf(SpecFields.Reference.key, SpecFields.SerialNumber.key, SpecFields.Caliber.key, SpecFields.BaseCaliber.key)

@Composable
fun EditScreen(
    state: EditUiState,
    vm: EditViewModel,
    onClose: () -> Unit,
    onSaved: (String) -> Unit,
    onLookUp: (String) -> Unit,
) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    var confirmDiscard by remember { mutableStateOf(false) }
    val close = { if (state.dirty) confirmDiscard = true else onClose() }
    // System and predictive back ask too, rather than dropping the changes.
    BackHandler(enabled = state.dirty && !confirmDiscard) { confirmDiscard = true }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(WatchIcons.Close, "Close", close)
            Text(
                t.title(if (state.isNew) "Add watch" else "Edit watch"),
                style = t.type.headerTitle, color = c.fg,
                modifier = Modifier.weight(1f).padding(start = 4.dp).semantics { heading() },
            )
            WatchButton(
                if (state.saving) "Saving…" else "Save",
                { vm.save(onSaved) },
                kind = ButtonKind.Primary,
                enabled = state.loaded && !state.saving,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        if (!state.loaded) return@Column
        val draft = state.draft ?: return@Column
        val listState = rememberLazyListState()
        val haptics = LocalHapticFeedback.current
        // A Save with mistakes scrolls to the first one and says how many there are.
        LaunchedEffect(state.errors) {
            if (state.errors.isEmpty()) return@LaunchedEffect
            haptics.performHapticFeedback(HapticFeedbackType.Reject)
            var index = 2
            for ((_, rows) in layout) {
                index++
                val at = rows.indexOfFirst { row -> row.any { it.key in state.errors } }
                if (at >= 0) {
                    listState.animateScrollToItem(index + at)
                    break
                }
                index += rows.size
            }
        }
        LazyColumn(
            Modifier.fillMaxSize().imePadding(),
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = style.screenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item("status") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel("Status")
                    ChipGroup(
                        SpecFields.Status.options().map { it.key to it.label },
                        setOf(draft.status.key),
                        { vm.setValue(SpecFields.Status, SpecValue.Choice(it)) },
                    )
                }
            }
            item("lookup") {
                val query = listOf(state.text(SpecFields.Brand), state.text(SpecFields.ModelName), state.text(SpecFields.Reference))
                    .filter { it.isNotBlank() }.joinToString(" ")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    WatchButton("Look up specs", { onLookUp(query) }, Modifier.fillMaxWidth(), kind = ButtonKind.Tonal, icon = WatchIcons.Lookup)
                    if (state.draftLost) {
                        Text("The lookup results were cleared when the app closed. Look up again to fill the form.", style = t.type.bodySmall, color = c.fg2)
                    }
                    if (state.filledFrom.isNotEmpty()) {
                        Text(
                            "Filled from ${state.filledFrom.joinToString(" and ")}. Values keep their source unless you change them. Nothing is saved until you tap Save.",
                            style = t.type.bodySmall, color = c.fg2,
                        )
                    }
                }
            }
            for ((group, rows) in layout) {
                item("h-${group.name}") {
                    Text(t.title(group.label), style = t.type.groupTitle, color = c.fg,
                        modifier = Modifier.padding(top = 18.dp).semantics { heading() })
                }
                items(rows, key = { row -> row.joinToString { it.key } }) { row ->
                    if (row.size == 1) {
                        FieldEditor(row[0], state, vm)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            row.forEach { f -> Box(Modifier.weight(1f)) { FieldEditor(f, state, vm) } }
                        }
                    }
                }
            }
            item("bottom") { Spacer(Modifier.height(80.dp).navigationBarsPadding()) }
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard changes?",
            message = "Your edits to this watch haven't been saved.",
            confirm = "Discard",
            destructive = true,
            onConfirm = { confirmDiscard = false; onClose() },
            onDismiss = { confirmDiscard = false },
        )
    }
}

private fun SpecField.options() = (kind as? SpecKind.Choice)?.options.orEmpty()

@Composable
private fun FieldEditor(field: SpecField, state: EditUiState, vm: EditViewModel) {
    val draft = state.draft ?: return
    when (val kind = field.kind) {
        is SpecKind.Choice -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(field.label)
            val current = (field.get(draft) as? SpecValue.Choice)?.key
            ChipGroup(kind.options.map { it.key to it.label }, setOfNotNull(current), { key ->
                vm.setValue(field, if (key == current) null else SpecValue.Choice(key))
            })
        }
        SpecKind.Flag -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(field.label)
            val current = (field.get(draft) as? SpecValue.Flag)?.value
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectChip("Yes", current == true, { vm.setValue(field, if (current == true) null else SpecValue.Flag(true)) }, underline = false, role = Role.RadioButton)
                SelectChip("No", current == false, { vm.setValue(field, if (current == false) null else SpecValue.Flag(false)) }, underline = false, role = Role.RadioButton)
            }
        }
        SpecKind.Date -> DateField(field, (field.get(draft) as? SpecValue.Date)?.value, state) { vm.setValue(field, it?.let { d -> SpecValue.Date(d) }) }
        is SpecKind.MultiChoice -> ComplicationsEditor(draft.complications, vm)
        SpecKind.Price -> PriceField(field, state, vm)
        else -> TextFieldEditor(field, state, vm)
    }
}

@Composable
private fun TextFieldEditor(field: SpecField, state: EditUiState, vm: EditViewModel) {
    val text = state.text(field)
    val unit = when (val k = field.kind) {
        is SpecKind.Decimal -> k.unit.symbol
        is SpecKind.Whole -> k.unit?.symbol
        else -> null
    }
    val keyboard = when (field.kind) {
        is SpecKind.Decimal -> KeyboardType.Decimal
        is SpecKind.Whole, SpecKind.Year -> KeyboardType.Number
        else -> KeyboardType.Text
    }
    var revealed by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WatchTextField(
            label = field.label,
            value = text,
            onValueChange = { vm.setText(field, it) },
            capitalization = when (field.key) {
                SpecFields.Reference.key, SpecFields.SerialNumber.key -> KeyboardCapitalization.Characters
                in wordFields -> KeyboardCapitalization.Words
                else -> KeyboardCapitalization.Sentences
            },
            // References, serials and calibres aren't words: autocorrect would only mangle them.
            autoCorrect = field.key !in codeFields,
            placeholder = placeholders[field.key],
            suffix = unit,
            keyboardType = keyboard,
            singleLine = field.kind != SpecKind.LongText,
            minLines = if (field.kind == SpecKind.LongText) 3 else 1,
            error = state.errors[field.key],
            supporting = vm.hint(field, text),
            secret = field.kind == SpecKind.Secret && !revealed,
            trailing = if (field.kind == SpecKind.Secret) ({
                IconAction(if (revealed) WatchIcons.EyeOff else WatchIcons.Eye, if (revealed) "Hide serial number" else "Show serial number", { revealed = !revealed })
            }) else null,
        )
        if (field.key == SpecFields.Brand.key) {
            val matches = state.brands.filter { it.startsWith(text.trim(), ignoreCase = true) && !it.equals(text.trim(), ignoreCase = true) }.take(6)
            if (matches.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    matches.forEach { b -> SelectChip(b, false, { vm.setText(field, b) }, underline = false, role = Role.Button) }
                }
            }
        }
        val quick = suggestions[field.key]
        if (quick != null && text.isBlank()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                quick.forEach { s -> SelectChip(s, false, { vm.setText(field, s) }, underline = false, role = Role.Button) }
            }
        }
    }
}

@Composable
private fun PriceField(field: SpecField, state: EditUiState, vm: EditViewModel) {
    var menu by remember { mutableStateOf(false) }
    val t = WatchTheme.values
    val c = WatchTheme.colors
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box {
            Column {
                FieldLabel("Currency")
                Spacer(Modifier.height(6.dp))
                SelectChip(state.currency, true, { menu = true }, underline = false, role = Role.Button)
            }
            DropdownMenu(menu, { menu = false }, containerColor = c.surface) {
                Currencies.common.forEach { code ->
                    DropdownMenuItem(text = { Text("$code  ${Currencies.symbol(code).trim()}", style = t.type.body, color = c.fg) }, onClick = { vm.setCurrency(code); menu = false })
                }
            }
        }
        Box(Modifier.weight(1f)) {
            WatchTextField(
                label = field.label,
                value = state.text(field),
                onValueChange = { vm.setText(field, it) },
                keyboardType = KeyboardType.Decimal,
                error = state.errors[field.key],
                placeholder = "0",
            )
        }
    }
}

@Composable
private fun DateField(field: SpecField, value: LocalDate?, state: EditUiState, onPick: (LocalDate?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val c = WatchTheme.colors
    val t = WatchTheme.values
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(field.label)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectChip(value?.let { state.prefs.dateFormat.format(it) } ?: "Choose date", value != null, { open = true }, underline = false, role = Role.Button)
            if (value != null) IconAction(WatchIcons.Close, "Clear ${field.label.lowercase()}", { onPick(null) })
        }
    }
    if (open) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = value?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton({
                    onPick(picker.selectedDateMillis?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date })
                    open = false
                }) { Text("OK", style = t.type.button, color = c.accent) }
            },
            dismissButton = { TextButton({ open = false }) { Text("Cancel", style = t.type.button, color = c.fg2) } },
        ) { DatePicker(picker) }
    }
}

@Composable
private fun ComplicationsEditor(selected: Set<String>, vm: EditViewModel) {
    var custom by remember { mutableStateOf("") }
    val style = WatchTheme.style
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FieldLabel(SpecFields.Complications.label)
        val options = Complication.entries.map { it.key to it.label } +
            selected.filter { it.startsWith(Complication.CUSTOM_PREFIX) }.map { it to Complication.labelFor(it) }
        ChipGroup(options, selected, vm::toggleComplication, multi = true)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WatchTextField("Add another", custom, { custom = it }, Modifier.weight(1f), placeholder = "e.g. Regatta timer")
            WatchButton("Add", { vm.addCustomComplication(custom); custom = "" }, enabled = custom.isNotBlank(),
                kind = if (style.direction == Direction.GALLERY) ButtonKind.Tonal else ButtonKind.Secondary)
        }
        Spacer(Modifier.width(1.dp))
    }
}
