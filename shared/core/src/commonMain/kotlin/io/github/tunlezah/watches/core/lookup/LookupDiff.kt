package io.github.tunlezah.watches.core.lookup

import io.github.tunlezah.watches.core.model.Provenance
import io.github.tunlezah.watches.core.model.Watch
import io.github.tunlezah.watches.core.spec.SpecField
import io.github.tunlezah.watches.core.spec.SpecFields
import io.github.tunlezah.watches.core.spec.SpecValue
import kotlin.math.abs
import kotlin.time.Instant

/** One value a source offers for a field. [agreeing] lists other sources that gave the same value. */
data class FieldOption(
    val source: String,
    val value: SpecValue,
    val url: String,
    val fetchedAt: Instant,
    val agreeing: List<String> = emptyList(),
)

/**
 * One field in the apply screen: what the watch has now and what the sources offer.
 *
 * A value the owner typed or edited is [protected]: its row starts unticked, so a lookup can only
 * replace it after an explicit tick. A row whose options include the current value needs no change.
 */
data class DiffRow(
    val field: SpecField,
    val current: SpecValue?,
    val provenance: Provenance?,
    val options: List<FieldOption>,
) {
    val protected: Boolean get() = current != null && (provenance == null || provenance.userEdited)
    val matchesCurrent: Boolean get() = current != null && options.any { LookupDiff.same(it.value, current) }
    val conflict: Boolean get() = options.size > 1

    /** Which option is ticked when the screen opens, or null for none. */
    val defaultChoice: Int?
        get() = when {
            options.isEmpty() -> null
            current == null -> 0
            matchesCurrent -> null
            protected -> null
            else -> 0
        }
}

object LookupDiff {

    /** Rows for every importable field at least one record has a value for, in registry order. */
    fun rows(current: Watch, provenance: Map<String, Provenance>, records: List<SourceRecord>): List<DiffRow> =
        SpecFields.all.filter { it.importable }.mapNotNull { field ->
            val now = field.get(current)
            val offered = records
                .sortedBy { SourcePriority.rank(field.group, it.source) }
                .mapNotNull { record -> record.values[field.key]?.let { v -> record to proposed(field, now, v) } }
            if (offered.isEmpty()) return@mapNotNull null
            val options = mutableListOf<FieldOption>()
            for ((record, value) in offered) {
                val i = options.indexOfFirst { same(it.value, value) }
                if (i >= 0) options[i] = options[i].copy(agreeing = options[i].agreeing + record.source)
                else options += FieldOption(record.source, value, record.url, record.fetchedAt)
            }
            DiffRow(field, now, provenance[field.key], options)
        }

    /** The watch after applying [choices] (field key to option index), plus the provenance to record. */
    fun apply(current: Watch, rows: List<DiffRow>, choices: Map<String, Int>): Pair<Watch, List<Provenance>> {
        var watch = current
        val provenance = mutableListOf<Provenance>()
        for (row in rows) {
            val option = choices[row.field.key]?.let { row.options.getOrNull(it) } ?: continue
            watch = row.field.set(watch, option.value)
            provenance += Provenance(row.field.key, option.source, option.url, option.fetchedAt, userEdited = false)
        }
        return watch to provenance
    }

    /** A new watch built from the records, taking the preferred source's value for every field. */
    fun merged(blank: Watch, records: List<SourceRecord>): Pair<Watch, List<Provenance>> {
        val rows = rows(blank, emptyMap(), records)
        return apply(blank, rows, rows.associate { it.field.key to 0 })
    }

    /**
     * Complications are added, never removed: a source listing "date" shouldn't erase a moon phase
     * the owner recorded. So the proposed value is the union.
     */
    private fun proposed(field: SpecField, current: SpecValue?, offered: SpecValue): SpecValue =
        if (field == SpecFields.Complications && current is SpecValue.Keys && offered is SpecValue.Keys) {
            SpecValue.Keys(current.value + offered.value)
        } else {
            offered
        }

    /** Equal for the owner's purposes: case and spacing in text don't count, nor sub-0.01 differences. */
    fun same(a: SpecValue, b: SpecValue): Boolean = when {
        a is SpecValue.Text && b is SpecValue.Text -> normalise(a.value) == normalise(b.value)
        a is SpecValue.Number && b is SpecValue.Number -> abs(a.value - b.value) < 0.005
        else -> a == b
    }

    private fun normalise(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ")
}
