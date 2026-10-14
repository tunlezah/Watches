package io.github.tunlezah.watches.core.spec

import io.github.tunlezah.watches.core.format.DisplayPrefs
import io.github.tunlezah.watches.core.format.Numbers
import io.github.tunlezah.watches.core.format.WaterResistanceUnit
import io.github.tunlezah.watches.core.model.Complication
import io.github.tunlezah.watches.core.model.Provenance
import io.github.tunlezah.watches.core.model.Sources
import io.github.tunlezah.watches.core.model.Watch
import io.github.tunlezah.watches.core.units.Units

/** A value split for typesetting: the figure, then a lighter unit/conversion part. */
data class FormattedValue(val value: String, val unit: String? = null) {
    val plain: String get() = if (unit.isNullOrEmpty()) value else "$value $unit"
}

data class SpecRow(
    val keys: List<String>,
    val label: String,
    val value: FormattedValue,
    /** Short source label to show beside the value, or null for manual entries. */
    val sourceLabel: String?,
    val sourceUrl: String?,
    val userEdited: Boolean,
    val secret: Boolean = false,
    val longText: Boolean = false,
)

data class SpecGroupSheet(val group: SpecGroup, val rows: List<SpecRow>)

object SpecFormatter {

    fun format(field: SpecField, value: SpecValue, prefs: DisplayPrefs): FormattedValue = when (value) {
        is SpecValue.Text -> FormattedValue(value.value)
        is SpecValue.Number -> formatNumber(field.kind, value.value, prefs)
        is SpecValue.Whole -> formatWhole(field.kind, value.value, prefs)
        is SpecValue.Flag -> FormattedValue(if (value.value) "Yes" else "No")
        is SpecValue.Choice -> FormattedValue(
            (field.kind as? SpecKind.Choice)?.options?.firstOrNull { it.key == value.key }?.label ?: value.key,
        )
        is SpecValue.Date -> FormattedValue(prefs.dateFormat.format(value.value))
        is SpecValue.Price -> FormattedValue(value.value.format())
        is SpecValue.Keys -> FormattedValue(Complication.sortKeys(value.value).joinToString(", ") { Complication.labelFor(it) })
    }

    private fun formatNumber(kind: SpecKind, v: Double, prefs: DisplayPrefs): FormattedValue {
        val unit = (kind as? SpecKind.Decimal)?.unit
        val text = Numbers.decimal(v, (kind as? SpecKind.Decimal)?.maxDecimals ?: 2)
        return when (unit) {
            SpecUnit.HOURS -> FormattedValue(text, if (v >= 72) "h · ${Numbers.decimal(v / 24, 1)} days" else "h")
            null -> FormattedValue(text)
            else -> FormattedValue(text, unit.symbol)
        }
    }

    private fun formatWhole(kind: SpecKind, v: Int, prefs: DisplayPrefs): FormattedValue {
        if (kind is SpecKind.Year) return FormattedValue(v.toString())
        val unit = (kind as? SpecKind.Whole)?.unit
        val text = Numbers.whole(v.toLong())
        return when (unit) {
            SpecUnit.METRES -> {
                val pressure = Numbers.decimal(Units.metresToAtm(v), 1)
                val label = if (prefs.waterResistanceUnit == WaterResistanceUnit.BAR) "bar" else "ATM"
                FormattedValue(text, "m · $pressure $label")
            }
            SpecUnit.BPH -> FormattedValue(text, "bph · ${Numbers.decimal(Units.bphToHz(v), 2)} Hz")
            SpecUnit.MONTHS -> FormattedValue(if (v % 12 == 0 && v >= 12) "${v / 12}" else text, if (v % 12 == 0 && v >= 12) (if (v == 12) "year" else "years") else "months")
            null -> FormattedValue(text)
            else -> FormattedValue(text, unit.symbol)
        }
    }
}

object SpecSheet {

    /** Groups shown on the watch detail screen, in data-model order. */
    val detailGroups = listOf(SpecGroup.IDENTITY, SpecGroup.CASE, SpecGroup.DIAL, SpecGroup.MOVEMENT, SpecGroup.STRAP, SpecGroup.OWNERSHIP)

    /**
     * Builds the detail spec sheet. Empty groups are dropped. Production years and limited editions
     * are combined into one row each, because that's how collectors read them.
     */
    fun build(watch: Watch, provenance: Map<String, Provenance>, prefs: DisplayPrefs): List<SpecGroupSheet> =
        detailGroups.mapNotNull { group ->
            val rows = rowsFor(group, watch, provenance, prefs)
            if (rows.isEmpty()) null else SpecGroupSheet(group, rows)
        }

    private val skipInDetail = setOf(
        SpecFields.Brand.key, SpecFields.ModelName.key, SpecFields.Nickname.key, // shown in the title block
        SpecFields.YearFrom.key, SpecFields.YearTo.key, SpecFields.LimitedNumber.key, SpecFields.LimitedTotal.key, // combined rows
        SpecFields.Status.key, // shown as a badge
    )

    private fun rowsFor(group: SpecGroup, watch: Watch, provenance: Map<String, Provenance>, prefs: DisplayPrefs): List<SpecRow> {
        val rows = mutableListOf<SpecRow>()
        if (group == SpecGroup.IDENTITY) {
            productionRow(watch, provenance)?.let(rows::add)
            limitedRow(watch, provenance)?.let(rows::add)
        }
        for (field in SpecFields.inGroup(group)) {
            if (field.key in skipInDetail) continue
            val value = field.get(watch) ?: continue
            rows += row(listOf(field.key), field.label, SpecFormatter.format(field, value, prefs), provenance,
                secret = field.kind == SpecKind.Secret, longText = field.kind == SpecKind.LongText)
        }
        // Keep the reference and collection first in identity, after the combined rows read better.
        if (group == SpecGroup.IDENTITY) rows.sortBy { identityOrder.indexOf(it.keys.first()).let { i -> if (i < 0) 99 else i } }
        return rows
    }

    private val identityOrder = listOf(
        SpecFields.Reference.key, SpecFields.Collection.key, SpecFields.YearFrom.key, SpecFields.LimitedTotal.key,
    )

    private fun productionRow(watch: Watch, provenance: Map<String, Provenance>): SpecRow? {
        val from = watch.yearFrom
        val to = watch.yearTo
        val text = when {
            from != null && to != null && from == to -> "$from"
            from != null && to != null -> "$from–$to"
            from != null -> "$from–present"
            to != null -> "until $to"
            else -> return null
        }
        return row(listOf(SpecFields.YearFrom.key, SpecFields.YearTo.key), "Production", FormattedValue(text), provenance)
    }

    private fun limitedRow(watch: Watch, provenance: Map<String, Provenance>): SpecRow? {
        val n = watch.limitedNumber
        val total = watch.limitedTotal
        val value = when {
            n != null && total != null -> FormattedValue("No. ${Numbers.whole(n.toLong())}", "of ${Numbers.whole(total.toLong())}")
            total != null -> FormattedValue("Limited to ${Numbers.whole(total.toLong())}")
            n != null -> FormattedValue("No. ${Numbers.whole(n.toLong())}")
            else -> return null
        }
        return row(listOf(SpecFields.LimitedNumber.key, SpecFields.LimitedTotal.key), "Limited edition", value, provenance)
    }

    private fun row(
        keys: List<String>,
        label: String,
        value: FormattedValue,
        provenance: Map<String, Provenance>,
        secret: Boolean = false,
        longText: Boolean = false,
    ): SpecRow {
        val p = keys.firstNotNullOfOrNull { provenance[it] }
        val imported = p != null && p.source != Sources.MANUAL
        return SpecRow(
            keys = keys,
            label = label,
            value = value,
            sourceLabel = if (imported) Sources.shortName(p!!.source) else null,
            sourceUrl = if (imported) p!!.sourceUrl else null,
            userEdited = p?.userEdited == true && imported,
            secret = secret,
            longText = longText,
        )
    }
}
