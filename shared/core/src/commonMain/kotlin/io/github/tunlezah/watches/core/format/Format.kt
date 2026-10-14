package io.github.tunlezah.watches.core.format

import io.github.tunlezah.watches.core.model.Keyed
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

object Numbers {
    /** Inserts thousands separators into a string of digits: "21600" -> "21,600". */
    fun group(digits: String): String {
        if (digits.length <= 3) return digits
        val sb = StringBuilder()
        val first = digits.length % 3
        if (first > 0) sb.append(digits, 0, first)
        var i = first
        while (i < digits.length) {
            if (sb.isNotEmpty()) sb.append(',')
            sb.append(digits, i, i + 3)
            i += 3
        }
        return sb.toString()
    }

    /**
     * Formats with at most [maxDecimals] places, trailing zeros trimmed, and thousands separators.
     * 42.0 -> "42", 13.18 -> "13.18", 21600.0 -> "21,600".
     */
    fun decimal(value: Double, maxDecimals: Int = 2, grouping: Boolean = true): String {
        val factor = 10.0.pow(maxDecimals)
        val scaled = (abs(value) * factor).roundToLong()
        val whole = scaled / factor.toLong()
        var fraction = (scaled % factor.toLong()).toString().padStart(maxDecimals, '0').trimEnd('0')
        if (maxDecimals == 0) fraction = ""
        val sign = if (value < 0 && scaled != 0L) "-" else ""
        val wholeText = if (grouping) group(whole.toString()) else whole.toString()
        return sign + wholeText + if (fraction.isNotEmpty()) ".$fraction" else ""
    }

    fun whole(value: Long, grouping: Boolean = true): String {
        val text = abs(value).toString()
        return (if (value < 0) "-" else "") + if (grouping) group(text) else text
    }
}

enum class DateFormat(override val key: String, override val label: String) : Keyed {
    DMY("dmy", "DD/MM/YYYY"),
    YMD("ymd", "YYYY-MM-DD"),
    MDY("mdy", "MM/DD/YYYY");

    fun format(date: LocalDate): String {
        val d = date.day.toString().padStart(2, '0')
        val m = date.month.number.toString().padStart(2, '0')
        val y = date.year.toString().padStart(4, '0')
        return when (this) {
            DMY -> "$d/$m/$y"
            YMD -> "$y-$m-$d"
            MDY -> "$m/$d/$y"
        }
    }

    /** "03/2028" or "2028-03": for due dates where the day adds noise. */
    fun formatMonth(date: LocalDate): String {
        val m = date.month.number.toString().padStart(2, '0')
        return if (this == YMD) "${date.year}-$m" else "$m/${date.year}"
    }

    /** Parses this format's text back into a date, or null. */
    fun parse(text: String): LocalDate? {
        val parts = text.trim().split('/', '-', '.').map { it.trim() }
        if (parts.size != 3) return null
        val nums = parts.map { it.toIntOrNull() ?: return null }
        val (y, m, d) = when (this) {
            DMY -> Triple(nums[2], nums[1], nums[0])
            YMD -> Triple(nums[0], nums[1], nums[2])
            MDY -> Triple(nums[2], nums[0], nums[1])
        }
        return runCatching { LocalDate(y, m, d) }.getOrNull()
    }
}

enum class WaterResistanceUnit(override val key: String, override val label: String) : Keyed {
    ATM("atm", "ATM"),
    BAR("bar", "bar"),
}

/** How values are shown. Stored values are always canonical (mm, m, bph, hours). */
data class DisplayPrefs(
    val dateFormat: DateFormat = DateFormat.DMY,
    val waterResistanceUnit: WaterResistanceUnit = WaterResistanceUnit.ATM,
    val defaultCurrency: String = "AUD",
)
