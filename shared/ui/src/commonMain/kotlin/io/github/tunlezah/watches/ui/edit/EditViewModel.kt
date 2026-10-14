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
import io.github.tunlezah.watches.data.WatchRepository
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
    val errors: Map<String, String> = emptyMap(),
    val currency: String = Currencies.DEFAULT,
    val brands: List<String> = emptyList(),
    val prefs: DisplayPrefs = DisplayPrefs(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
) {
    fun text(field: SpecField) = texts[field.key].orEmpty()
}

class EditViewModel(
    private val watchId: String?,
    private val watches: WatchRepository,
    private val settings: SettingsRepository,
    private val time: TimeSource,
) : ViewModel() {

    private val _state = MutableStateFlow(EditUiState(isNew = watchId == null))
    val state: StateFlow<EditUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = settings.current().display
            val existing = watchId?.let { watches.get(it) }
            val now = time.now()
            val watch = existing ?: Watch(id = "", createdAt = now, updatedAt = now)
            _state.value = EditUiState(
                loaded = true,
                isNew = existing == null,
                draft = watch,
                texts = SpecFields.all.filter { it.isTextual() }.associate { it.key to initialText(it, watch) },
                currency = watch.pricePaid?.currency ?: prefs.defaultCurrency,
                prefs = prefs,
            )
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
        val base = s.draft ?: return
        val errors = SpecFields.all.filter { it.isTextual() }.mapNotNull { f -> validate(f, s.text(f), s.currency) }.toMap()
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        var watch = base
        for (field in SpecFields.all.filter { it.isTextual() }) watch = field.set(watch, parse(field, s.text(field), s.currency))
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val saved = if (s.isNew) watches.create(watch) else watches.update(watch)
            _state.update { it.copy(saving = false, dirty = false) }
            onSaved(saved.id)
        }
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
