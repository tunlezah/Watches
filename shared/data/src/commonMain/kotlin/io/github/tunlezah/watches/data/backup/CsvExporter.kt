package io.github.tunlezah.watches.data.backup

import io.github.tunlezah.watches.core.TimeSource
import io.github.tunlezah.watches.core.care.CarePlan
import io.github.tunlezah.watches.core.model.BoxPapersItem
import io.github.tunlezah.watches.core.model.Complication
import io.github.tunlezah.watches.core.model.Money
import io.github.tunlezah.watches.core.model.Watch
import io.github.tunlezah.watches.core.model.keyedOf
import io.github.tunlezah.watches.core.spec.SpecField
import io.github.tunlezah.watches.core.spec.SpecFields
import io.github.tunlezah.watches.core.spec.SpecKind
import io.github.tunlezah.watches.core.spec.SpecValue
import io.github.tunlezah.watches.data.db.AppDatabase
import io.github.tunlezah.watches.data.db.toModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.Sink
import okio.buffer
import okio.use
import kotlin.math.abs

/**
 * The collection as a spreadsheet: one row per watch, every registry field (units in the header),
 * then a summary of its service, warranty and box & papers. UTF-8 with a byte-order mark so Excel
 * reads it as UTF-8, quoted per RFC 4180, and text that a spreadsheet would run as a formula
 * (starting with = + - @) is prefixed with an apostrophe.
 */
class CsvExporter(private val db: AppDatabase, private val time: TimeSource = TimeSource.System) {

    fun suggestedFileName(): String {
        val t = time.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return "Watches ${t.year}-${(t.month.ordinal + 1).toString().padStart(2, '0')}-${t.day.toString().padStart(2, '0')}.csv"
    }

    /** Writes the CSV to [sink] and closes it. Returns the number of watches written. */
    suspend fun export(sink: Sink): Int = withContext(Dispatchers.IO) {
        val dao = db.backupDao()
        val complications = dao.complications().groupBy({ it.watchId }, { it.key })
        val watches = dao.watches().map { it.toModel(complications[it.id].orEmpty()) }
        val services = dao.serviceEntries().map { it.toModel() }.groupBy { it.watchId }
        val papers = dao.boxPapers().mapNotNull { it.toModel()?.let { item -> it.watchId to item } }.groupBy({ it.first }, { it.second })
        val photos = dao.photos().groupingBy { it.watchId }.eachCount()
        val documents = dao.documents().groupingBy { it.watchId }.eachCount()
        val today = time.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        val fields = SpecFields.all
        val repeated = fields.groupingBy { it.label }.eachCount().filterValues { it > 1 }.keys
        val header = buildList {
            add("ID")
            for (f in fields) {
                val name = if (f.label in repeated) "${f.group.label}: ${f.label}" else f.label
                when (val kind = f.kind) {
                    SpecKind.Price -> { add(name); add("$name currency") }
                    is SpecKind.Decimal -> add("$name (${kind.unit.symbol})")
                    is SpecKind.Whole -> add(kind.unit?.let { "$name (${it.symbol})" } ?: name)
                    else -> add(name)
                }
            }
            addAll(listOf("Archived", "Last service", "Last full service", "Next service due", "Warranty ends", "Box & papers", "Service entries", "Photos", "Documents", "Added", "Last edited"))
        }

        sink.buffer().use { out ->
            out.write(BOM)
            out.writeUtf8(header.joinToString(",") { cell(it, text = true) }).writeUtf8("\r\n")
            for (w in watches) {
                val history = services[w.id].orEmpty()
                val last = history.maxByOrNull { it.servicedOn }?.servicedOn
                val lastFull = history.filter { it.isFullService }.maxByOrNull { it.servicedOn }?.servicedOn
                val due = CarePlan.service(w.serviceIntervalMonths, w.acquiredOn, last, lastFull, today).nextDue
                val warranty = CarePlan.warranty(w.warrantyStart, w.warrantyMonths, today)?.until
                val row = buildList {
                    add(cell(w.id, text = true))
                    for (f in fields) addAll(cells(f, w))
                    add(cell(if (w.isArchived) "Yes" else "", text = true))
                    add(cell(last?.toString().orEmpty()))
                    add(cell(lastFull?.toString().orEmpty()))
                    add(cell(due?.toString().orEmpty()))
                    add(cell(warranty?.toString().orEmpty()))
                    add(cell(boxPapers(papers[w.id].orEmpty()), text = true))
                    add(cell(history.size.toString()))
                    add(cell((photos[w.id] ?: 0).toString()))
                    add(cell((documents[w.id] ?: 0).toString()))
                    add(cell(w.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()))
                    add(cell(w.updatedAt.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()))
                }
                out.writeUtf8(row.joinToString(",")).writeUtf8("\r\n")
            }
        }
        watches.size
    }

    private fun cells(field: SpecField, watch: Watch): List<String> {
        val value = field.get(watch)
        if (field.kind == SpecKind.Price) {
            val money = (value as? SpecValue.Price)?.value
            return listOf(cell(money?.let(::amount).orEmpty()), cell(money?.currency.orEmpty(), text = true))
        }
        return listOf(
            when (value) {
                null -> ""
                is SpecValue.Text -> cell(value.value, text = true)
                is SpecValue.Number -> cell(number(value.value))
                is SpecValue.Whole -> cell(value.value.toString())
                is SpecValue.Flag -> cell(if (value.value) "Yes" else "No", text = true)
                is SpecValue.Choice -> cell(choiceLabel(field, value.key), text = true)
                is SpecValue.Date -> cell(value.value.toString())
                is SpecValue.Price -> cell(amount(value.value))
                is SpecValue.Keys -> cell(value.value.map(::complicationLabel).sorted().joinToString("; "), text = true)
            },
        )
    }

    private fun choiceLabel(field: SpecField, key: String): String =
        (field.kind as? SpecKind.Choice)?.options?.firstOrNull { it.key == key }?.label ?: key

    private fun complicationLabel(key: String): String =
        key.removePrefix("custom:").takeIf { key.startsWith("custom:") } ?: keyedOf<Complication>(key)?.label ?: key

    private fun boxPapers(items: List<BoxPapersItem>): String {
        if (items.isEmpty()) return ""
        val s = CarePlan.boxPapers(items)
        return if (s.fullSet) "Full set" else listOfNotNull(
            s.present.takeIf { it > 0 }?.let { "$it present" },
            s.missing.takeIf { it > 0 }?.let { "$it missing" },
        ).joinToString(", ")
    }

    private companion object {
        val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val FORMULA = setOf('=', '+', '-', '@', '\t', '\r')

        /** A CSV cell. Text that a spreadsheet would treat as a formula gets a leading apostrophe. */
        fun cell(value: String, text: Boolean = false): String {
            val safe = if (text && value.isNotEmpty() && value[0] in FORMULA) "'$value" else value
            val quote = safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' } || safe != safe.trim()
            return if (quote) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
        }

        /** Plain decimal, no grouping, no trailing zeros: 41, 40.5. */
        fun number(v: Double): String {
            val rounded = kotlin.math.round(v * 1000) / 1000
            val whole = rounded.toLong()
            if (rounded == whole.toDouble()) return whole.toString()
            return rounded.toString().trimEnd('0').trimEnd('.')
        }

        /** The amount in major units with the currency's decimals: 10900.00, 150000 (JPY). */
        fun amount(m: Money): String {
            val divisor = (1..m.decimals).fold(1L) { acc, _ -> acc * 10 }
            val sign = if (m.amountMinor < 0) "-" else ""
            val whole = abs(m.amountMinor) / divisor
            if (m.decimals == 0) return "$sign$whole"
            return "$sign$whole.${(abs(m.amountMinor) % divisor).toString().padStart(m.decimals, '0')}"
        }
    }
}
