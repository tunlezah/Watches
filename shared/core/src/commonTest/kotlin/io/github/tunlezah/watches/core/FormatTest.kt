package io.github.tunlezah.watches.core

import io.github.tunlezah.watches.core.format.DateFormat
import io.github.tunlezah.watches.core.format.Numbers
import io.github.tunlezah.watches.core.model.Money
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormatTest {

    @Test fun numbers() {
        assertEquals("42", Numbers.decimal(42.0))
        assertEquals("13.18", Numbers.decimal(13.18))
        assertEquals("13.2", Numbers.decimal(13.18, 1))
        assertEquals("21,600", Numbers.decimal(21_600.0))
        assertEquals("1,234,567", Numbers.whole(1_234_567))
        assertEquals("999", Numbers.whole(999))
        assertEquals("-3.5", Numbers.decimal(-3.5))
        assertEquals("0", Numbers.decimal(-0.001))
    }

    @Test fun dates() {
        val d = LocalDate(2023, 3, 14)
        assertEquals("14/03/2023", DateFormat.DMY.format(d))
        assertEquals("2023-03-14", DateFormat.YMD.format(d))
        assertEquals("03/14/2023", DateFormat.MDY.format(d))
        assertEquals("03/2023", DateFormat.DMY.formatMonth(d))
        assertEquals(d, DateFormat.DMY.parse("14/03/2023"))
        assertEquals(d, DateFormat.YMD.parse("2023-03-14"))
        assertNull(DateFormat.DMY.parse("31/02/2023"))
        assertNull(DateFormat.DMY.parse("soon"))
    }

    @Test fun money() {
        assertEquals("A$10,950", Money(1_095_000, "AUD").format())
        assertEquals("A$10,950.50", Money(1_095_050, "AUD").format())
        assertEquals("¥1,200,000", Money(1_200_000, "JPY").format())
        assertEquals("CHF 950", Money(95_000, "CHF").format())
        assertEquals(Money(1_095_050, "AUD"), Money.parse("10,950.50", "AUD"))
        assertEquals(Money(1_095_000, "AUD"), Money.parse("A$10,950", "AUD"))
        assertEquals(Money(1_200_000, "JPY"), Money.parse("1,200,000", "JPY"))
        assertNull(Money.parse("a lot", "AUD"))
    }
}
