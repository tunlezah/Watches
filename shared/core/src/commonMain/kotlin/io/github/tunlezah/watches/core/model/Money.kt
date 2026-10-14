package io.github.tunlezah.watches.core.model

import io.github.tunlezah.watches.core.format.Numbers
import kotlin.math.abs
import kotlin.math.roundToLong

/** An amount in minor units (cents) of an ISO 4217 currency. No conversion: the app is offline. */
data class Money(val amountMinor: Long, val currency: String) {

    val decimals: Int get() = Currencies.decimals(currency)

    fun format(): String {
        val divisor = pow10(decimals)
        val whole = abs(amountMinor) / divisor
        val fraction = abs(amountMinor) % divisor
        val sign = if (amountMinor < 0) "-" else ""
        val body = buildString {
            append(Numbers.group(whole.toString()))
            if (fraction != 0L) append('.').append(fraction.toString().padStart(decimals, '0'))
        }
        return sign + Currencies.symbol(currency) + body
    }

    companion object {
        /** Parses user input such as "10950", "10,950.50" or "A$10,950". Returns null if it isn't a number. */
        fun parse(input: String, currency: String): Money? {
            val cleaned = input.filter { it.isDigit() || it == '.' || it == '-' }
            if (cleaned.isEmpty() || cleaned == "-" || cleaned.count { it == '.' } > 1) return null
            val value = cleaned.toDoubleOrNull() ?: return null
            return Money((value * pow10(Currencies.decimals(currency))).roundToLong(), currency)
        }

        private fun pow10(n: Int): Long {
            var r = 1L
            repeat(n) { r *= 10 }
            return r
        }
    }
}

object Currencies {
    const val DEFAULT = "AUD"

    /** Common choices offered in the picker. Any ISO code can still be stored. */
    val common = listOf("AUD", "USD", "EUR", "GBP", "JPY", "CHF", "HKD", "SGD", "NZD", "CAD")

    fun decimals(code: String): Int = when (code) {
        "JPY", "KRW" -> 0
        else -> 2
    }

    fun symbol(code: String): String = when (code) {
        "AUD" -> "A$"
        "USD" -> "US$"
        "EUR" -> "€"
        "GBP" -> "£"
        "JPY" -> "¥"
        "HKD" -> "HK$"
        "SGD" -> "S$"
        "NZD" -> "NZ$"
        "CAD" -> "C$"
        else -> "$code "
    }
}
