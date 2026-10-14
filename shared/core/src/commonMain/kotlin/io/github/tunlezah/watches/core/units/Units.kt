package io.github.tunlezah.watches.core.units

import kotlin.math.roundToInt

/** Canonical units are mm, metres, beats per hour and hours. These convert to and from what sources print. */
object Units {
    const val MM_PER_LIGNE = 2.2558
    const val BPH_PER_HZ = 7200

    fun lignesToMm(lignes: Double): Double = lignes * MM_PER_LIGNE
    fun mmToLignes(mm: Double): Double = mm / MM_PER_LIGNE

    fun bphToHz(bph: Int): Double = bph.toDouble() / BPH_PER_HZ
    fun hzToBph(hz: Double): Int = (hz * BPH_PER_HZ).roundToInt()

    /** 1 bar ≈ 1 ATM ≈ 10 m of static water pressure, which is how watch ratings are quoted. */
    fun metresToAtm(metres: Int): Double = metres / 10.0
    fun atmToMetres(atm: Double): Int = (atm * 10).roundToInt()

    /** Feet ratings are rounded conversions of metre ratings, so snap back to the nearest 10 m. */
    fun feetToMetres(feet: Double): Int {
        val raw = feet * 0.3048
        val snapped = (raw / 10).roundToInt() * 10
        return if (kotlin.math.abs(raw - snapped) <= 3) snapped else raw.roundToInt()
    }
}

/**
 * Lenient parsers for the free text sources print, e.g. "28,800 A/h", "11½'''", "200 m / 660 ft".
 * Each returns the canonical value or null when the text has no usable number.
 */
object UnitParser {

    private val number = Regex("""(\d+(?:[.,'’   ]\d{3})*(?:[.,]\d+)?|\d*[.,]\d+)\s*([½¼¾])?""")

    /** First number in the text, understanding thousands separators, decimal commas and ½ ¼ ¾. */
    fun firstNumber(text: String): Double? {
        val m = number.find(text) ?: return null
        return toDouble(m.groupValues[1], m.groupValues[2])
    }

    private fun toDouble(raw: String, fraction: String): Double? {
        var s = raw.replace(Regex("[   '’]"), "")
        // "28,800" and "28.800" are thousands; "4,5" is a decimal comma.
        s = when {
            Regex("""^\d{1,3}([.,]\d{3})+$""").matches(s) && !(s.count { it == '.' || it == ',' } == 1 && s.startsWith("0")) ->
                s.replace(",", "").replace(".", "")
            else -> s.replace(',', '.')
        }
        val base = s.toDoubleOrNull() ?: return null
        return base + when (fraction) {
            "½" -> 0.5
            "¼" -> 0.25
            "¾" -> 0.75
            else -> 0.0
        }
    }

    /** Diameter or thickness in mm. Understands mm, cm, inches and lignes (''' ‴ or "lignes"). */
    fun lengthMm(text: String): Double? {
        val value = firstNumber(text) ?: return null
        val t = text.lowercase()
        return when {
            "‴" in t || "'''" in t || "lign" in t -> Units.lignesToMm(value)
            Regex("""\d\s*cm\b""").containsMatchIn(t) -> value * 10
            Regex("""\d\s*(in\b|inch|")""").containsMatchIn(t) -> value * 25.4
            else -> value
        }
    }

    /** Beat rate in bph. Understands bph, vph, A/h, "beats" and Hz. */
    fun frequencyBph(text: String): Int? {
        val t = text.lowercase()
        // Prefer an explicit bph/vph/A/h figure when several appear ("28,800 A/h (4 Hz)").
        val perHour = Regex("""([\d.,'’   ]+)\s*(bph|vph|a/h|a\.h\.|beats)""").find(t)
        if (perHour != null) return firstNumber(perHour.groupValues[1])?.roundToInt()
        val hz = Regex("""([\d.,]+)\s*hz""").find(t)
        if (hz != null) return firstNumber(hz.groupValues[1])?.let(Units::hzToBph)
        val value = firstNumber(t) ?: return null
        // A bare small number is a Hz figure; a bare large number is already per hour.
        return if (value < 20) Units.hzToBph(value) else value.roundToInt()
    }

    /** Water resistance in metres. Understands m, ATM, bar and feet; prefers metres when several are given. */
    fun waterResistanceM(text: String): Int? {
        val t = text.lowercase()
        Regex("""([\d.,]+)\s*(m\b|metres|meters|metre|meter)""").find(t)?.let { m ->
            return firstNumber(m.groupValues[1])?.roundToInt()
        }
        Regex("""([\d.,]+)\s*(atm|bar)""").find(t)?.let { m ->
            return firstNumber(m.groupValues[1])?.let(Units::atmToMetres)
        }
        Regex("""([\d.,]+)\s*(ft|feet)""").find(t)?.let { m ->
            return firstNumber(m.groupValues[1])?.let(Units::feetToMetres)
        }
        return firstNumber(t)?.roundToInt()
    }

    /** Power reserve in hours. Understands h, hours and days. */
    fun powerReserveH(text: String): Double? {
        val t = text.lowercase()
        Regex("""([\d.,½¼¾ ]+)\s*(days?|d\b)""").find(t)?.let { m ->
            return firstNumber(m.groupValues[1])?.times(24)
        }
        return firstNumber(t)
    }

    fun wholeNumber(text: String): Int? = firstNumber(text)?.roundToInt()
}
