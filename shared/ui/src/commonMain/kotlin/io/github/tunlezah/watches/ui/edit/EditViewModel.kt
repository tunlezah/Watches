package io.github.tunlezah.watches.ui.edit

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tunlezah.watches.core.TimeSource
import io.github.tunlezah.watches.core.format.DisplayPrefs
import io.github.tunlezah.watches.core.format.Numbers
import io.github.tunlezah.watches.core.model.Complication
import io.github.tunlezah.watches.core.model.Currencies
import io.github.tunlezah.watches.core.model.Money
import io.github.tunlezah.watches.core.model.Watch
import io.github.tunlezah.watches.core.spec.SpecField
import io.github.tunlezah.watches.core.spec.SpecFields
import io.github.tunlezah.watches.core.spec.SpecKind
import io.github.tunlezah.watches.core.spec.SpecValue
import io.github.tunlezah.watches.core.units.Units
import io.github.tunlezah.watches.data.SettingsRepository
import io.github.tunlezah.watches.data.LookupRepository
import io.github.tunlezah.watches.data.WatchRepository
import io.github.tunlezah.watches.core.lookup.SourceRecord
import io.github.tunlezah.watches.core.model.OtherSpec
import io.github.tunlezah.watches.core.model.Provenance
import io.github.tunlezah.watches.core.model.Sources
import io.github.tunlezah.watches.ui.lookup.LookupDraft
import io.github.tunlezah.watches.ui.lookup.LookupDrafts
import io.github.tunlezah.watches.ui.lookup.LookupForm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class EditUiState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    /** Holds every non-text value (choices, flags, dates, complications). Text-like fields live in [texts]. */
    val draft: Watch? = null,
    /** Raw text for every text, number and price field, keyed by spec field key. */
    val texts: Map<String, String> = emptyMap(),
    /** The texts as first shown, so saving only rewrites fields the owner actually changed. */
    val initialTexts: Map<String, String> = emptyMap(),
    val initialCurrency: String = Currencies.DEFAULT,
    val errors: Map<String, String> = emptyMap(),
    val currency: String = Currencies.DEFAULT,
    val brands: List<String> = emptyList(),
    val prefs: DisplayPrefs = DisplayPrefs(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
    /** Values filled from lookups in this form, and their sources. They stay sourced unless changed. */
    val sourced: Map<String, Provenance> = emptyMap(),
    val sourcedValues: Map<String, SpecValue?> = emptyMap(),
    /** The text each filled field showed when filled; if it's still the same, the exact value is kept. */
    val appliedTexts: Map<String, String> = emptyMap(),
    val pendingExtras: List<OtherSpec> = emptyList(),
    val pendingRecords: List<SourceRecord> = emptyList(),
    /** The lookup draft this form was opened with no longer exists (the app was closed meanwhile). */
    val draftLost: Boolean = false,
) {
    fun text(field: SpecField) = texts[field.key].orEmpty()

    /** Display names of the sources this form was filled from. */
    val filledFrom: List<String> get() = sourced.values.map { Sources.displayName(it.source) }.distinct()
}

class EditViewModel(
    private val watchId: String?,
    private val draftKey: String?,
    private val watches: WatchRepository,
    private val settings: SettingsRepository,
    private val time: TimeSource,
    private val lookups: LookupRepository,
    private val drafts: LookupDrafts,
) : ViewModel() {

    private val _state = MutableStateFlow(EditUiState(isNew = watchId == null))
    val state: StateFlow<EditUiState> = _state.asStateFlow()

    /** The watch as stored (or blank for a new one) and its provenance, for working out what the form changed. */
    private var original: Watch? = null
    private var savedProvenance: Map<String, Provenance> = emptyMap()

    /** Lets a lookup started from this form read its values and fill it. */
    val formKey: String = drafts.register(object : LookupForm {
        override val title: String get() = _state.value.draft?.let { formWatch(_state.value).title } ?: "New watch"
        override fun current(): Pair<Watch, Map<String, Provenance>> = _state.value.let { s -> formWatch(s) to formProvenance(s) }
        override fun apply(draft: LookupDraft) = applyLookup(draft)
    })

    init {
        viewModelScope.launch {
            val prefs = settings.current().display
            val existing = watchId?.let { watches.get(it) }
            savedProvenance = watchId?.let { watches.observeDetail(it).first()?.provenance }.orEmpty()
            val now = time.now()
            val blank = existing ?: Watch(id = "", createdAt = now, updatedAt = now)
            original = blank
            val texts = SpecFields.all.filter { it.isTextual() }.associate { it.key to initialText(it, blank) }
            val currency = blank.pricePaid?.currency ?: prefs.defaultCurrency
            _state.value = EditUiState(
                loaded = true,
                isNew = existing == null,
                draft = blank,
                texts = texts,
                initialTexts = texts,
                currency = currency,
                initialCurrency = currency,
                prefs = prefs,
            )
            // "Add to collection" from Lookup: start from the reviewed values.
            if (draftKey != null) {
                val draft = drafts.take(draftKey)
                if (draft != null) applyLookup(draft) else _state.update { it.copy(draftLost = true) }
            }
        }
        viewModelScope.launch {
            val brands = watches.observeBrands().first()
            _state.update { it.copy(brands = brands) }
        }
    }

    fun setText(field: SpecField, text: String) = _state.update { s ->
        s.copy(texts = s.texts + (field.key to text), errors = s.errors.withCheck(field.key, validate(field, text, s.currency)), dirty = true)
    }

    fun setValue(field: SpecField, value: SpecValue?) = _state.update { s ->
        s.copy(draft = s.draft?.let { field.set(it, value) }, dirty = true)
    }

    fun setCurrency(code: String) = _state.update { s ->
        s.copy(currency = code, errors = s.errors.withCheck(SpecFields.PricePaid.key, validate(SpecFields.PricePaid, s.text(SpecFields.PricePaid), code)), dirty = true)
    }

    fun toggleComplication(key: String) = _state.update { s ->
        val d = s.draft ?: return@update s
        val next = if (key in d.complications) d.complications - key else d.complications + key
        s.copy(draft = d.copy(complications = next), dirty = true)
    }

    fun addCustomComplication(label: String) {
        val clean = label.trim()
        if (clean.isEmpty()) return
        val builtIn = Complication.entries.firstOrNull { it.label.equals(clean, ignoreCase = true) }
        val key = builtIn?.key ?: Complication.customKey(clean)
        _state.update { s -> s.draft?.let { d -> s.copy(draft = d.copy(complications = d.complications + key), dirty = true) } ?: s }
    }

    /** Validates, then saves. [onSaved] gets the watch id. */
    fun save(onSaved: (String) -> Unit) {
        val s = _state.value
        if (s.draft == null) return
        val errors = SpecFields.all.filter { it.isTextual() }.mapNotNull { f -> validate(f, s.text(f), s.currency) }.toMap()
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        val watch = formWatch(s)
        // A filled value stays sourced only if the owner didn't change it afterwards.
        val sourced = s.sourced.filterKeys { key -> SpecFields.byKey(key)?.get(watch) == s.sourcedValues[key] }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val saved = if (s.isNew) watches.create(watch, sourced = sourced, otherSpecs = s.pendingExtras)
            else watches.update(watch, sourced, s.pendingExtras)
            lookups.saveSnapshots(saved.id, s.pendingRecords)
            _state.update { it.copy(saving = false, dirty = false) }
            onSaved(saved.id)
        }
    }

    /** The form's values as a watch. Text boxes that weren't touched keep their exact stored or filled value. */
    private fun formWatch(s: EditUiState): Watch {
        var watch = s.draft ?: return original ?: Watch(id = "", createdAt = time.now(), updatedAt = time.now())
        for (field in SpecFields.all.filter { it.isTextual() }) {
            val text = s.text(field)
            val untouched = text == (s.appliedTexts[field.key] ?: s.initialTexts[field.key].orEmpty()) &&
                !(field == SpecFields.PricePaid && s.currency != s.initialCurrency)
            if (untouched) continue
            if (validate(field, text, s.currency) == null) watch = field.set(watch, parse(field, text, s.currency))
        }
        return watch
    }

    /** Where each of the form's values came from: lookups, the stored watch, or the owner. */
    private fun formProvenance(s: EditUiState): Map<String, Provenance> {
        val now = formWatch(s)
        // A filled value stays the source's only while it's unchanged; once the owner edits it, it's theirs.
        val stillSourced = s.sourced.filterKeys { key -> SpecFields.byKey(key)?.get(now) == s.sourcedValues[key] }
        val changedByOwner = SpecFields.changedKeys(original ?: now, now) - stillSourced.keys
        return savedProvenance + stillSourced + changedByOwner.associateWith { Provenance(it, Sources.MANUAL, userEdited = true) }
    }

    private fun applyLookup(draft: LookupDraft) = _state.update { s ->
        val applied = draft.sourced.keys.mapNotNull(SpecFields::byKey)
        val texts = applied.filter { it.isTextual() }.associate { it.key to initialText(it, draft.watch) }
        // Applied text fields take the draft's exact values; everything else keeps what the form shows.
        var next = s.draft ?: draft.watch
        for (field in applied) next = field.set(next, field.get(draft.watch))
        s.copy(
            draft = next,
            texts = s.texts + texts,
            appliedTexts = s.appliedTexts + texts,
            errors = s.errors - texts.keys,
            sourced = s.sourced + draft.sourced,
            sourcedValues = s.sourcedValues + draft.sourcedValues,
            pendingExtras = io.github.tunlezah.watches.data.WatchRepository.mergeOtherSpecs(s.pendingExtras, draft.extras),
            pendingRecords = s.pendingRecords.filterNot { r -> draft.records.any { it.source == r.source } } + draft.records,
            dirty = true,
        )
    }

    override fun onCleared() {
        drafts.unregister(formKey)
    }

    /** Live conversion shown under unit fields, e.g. "5 ATM" under 50 m. */
    fun hint(field: SpecField, text: String): String? {
        val value = text.replace(',', '.').toDoubleOrNull() ?: return null
        return when (field.key) {
            SpecFields.WaterResistance.key -> "${Numbers.decimal(Units.metresToAtm(value.toInt()), 1)} ATM / bar"
            SpecFields.Frequency.key -> if (value >= 3600) "${Numbers.decimal(Units.bphToHz(value.toInt()), 2)} Hz" else null
            SpecFields.PowerReserve.key -> if (value >= 72) "${Numbers.decimal(value / 24, 1)} days" else null
            SpecFields.Diameter.key -> null
            else -> null
        }
    }

    companion object {
        private fun Map<String, String>.withCheck(key: String, error: Pair<String, String>?) =
            if (error == null) this - key else this + error

        fun SpecField.isTextual() = when (kind) {
            SpecKind.Text, SpecKind.LongText, SpecKind.Secret, SpecKind.Year, SpecKind.Price -> true
            is SpecKind.Decimal, is SpecKind.Whole -> true
            else -> false
        }

        private fun initialText(field: SpecField, watch: Watch): String = when (val v = field.get(watch)) {
            null -> ""
            is SpecValue.Text -> v.value
            is SpecValue.Number -> Numbers.decimal(v.value, 2, grouping = false)
            is SpecValue.Whole -> v.value.toString()
            is SpecValue.Price -> {
                val m = v.value
                val divisor = if (m.decimals == 0) 1L else if (m.decimals == 2) 100L else 1L
                val whole = m.amountMinor / divisor
                val frac = m.amountMinor % divisor
                if (frac == 0L) whole.toString() else "$whole.${frac.toString().padStart(m.decimals, '0')}"
            }
            else -> ""
        }

        private fun numberOrNull(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

        fun validate(field: SpecField, text: String, currency: String): Pair<String, String>? {
            if (text.isBlank()) return null
            val error = when (field.kind) {
                is SpecKind.Decimal -> numberOrNull(text).let { if (it == null || it < 0 || it > 100_000) "Enter a number, like 39.5" else null }
                is SpecKind.Whole -> text.trim().toIntOrNull().let { if (it == null || it < 0) "Enter a whole number" else null }
                SpecKind.Year -> text.trim().toIntOrNull().let { if (it == null || it !in 1700..2100) "Enter a year, like 2021" else null }
                SpecKind.Price -> if (Money.parse(text, currency) == null) "Enter an amount, like 10950" else null
                else -> null
            }
            return error?.let { field.key to it }
        }

        fun parse(field: SpecField, text: String, currency: String): SpecValue? {
            if (text.isBlank()) return null
            return when (field.kind) {
                SpecKind.Text, SpecKind.LongText, SpecKind.Secret -> SpecValue.Text(text.trim())
                is SpecKind.Decimal -> numberOrNull(text)?.let { SpecValue.Number(it) }
                is SpecKind.Whole, SpecKind.Year -> text.trim().toIntOrNull()?.let { SpecValue.Whole(it) }
                SpecKind.Price -> Money.parse(text, currency)?.let { SpecValue.Price(it) }
                else -> null
            }
        }
    }
}
