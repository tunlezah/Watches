package io.github.tunlezah.watches.core

import io.github.tunlezah.watches.core.units.UnitParser
import io.github.tunlezah.watches.core.units.Units
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnitsTest {

    private fun assertClose(expected: Double, actual: Double?, tolerance: Double = 0.01) {
        assertTrue(actual != null && abs(expected - actual) <= tolerance, "expected $expected, was $actual")
    }

    @Test fun lignesConvertToMillimetres() {
        assertClose(25.94, UnitParser.lengthMm("11½'''"))
        assertClose(25.94, UnitParser.lengthMm("11.5‴"))
        assertClose(27.07, UnitParser.lengthMm("12 lignes"))
        assertClose(26.0, UnitParser.lengthMm("26 mm"))
        assertClose(4.6, UnitParser.lengthMm("4.60mm"))
        assertClose(39.5, UnitParser.lengthMm("3.95 cm"))
    }

    @Test fun frequencyUnderstandsEveryNotation() {
        assertEquals(28_800, UnitParser.frequencyBph("28,800 A/h"))
        assertEquals(28_800, UnitParser.frequencyBph("28'800 vph"))
        assertEquals(28_800, UnitParser.frequencyBph("28.800 bph"))
        assertEquals(21_600, UnitParser.frequencyBph("3 Hz"))
        assertEquals(25_200, UnitParser.frequencyBph("3.5 Hz"))
        assertEquals(36_000, UnitParser.frequencyBph("36000"))
        assertEquals(28_800, UnitParser.frequencyBph("4"))
        assertEquals(28_800, UnitParser.frequencyBph("28,800 A/h (4 Hz)"))
        assertNull(UnitParser.frequencyBph("unknown"))
    }

    @Test fun waterResistancePrefersMetres() {
        assertEquals(200, UnitParser.waterResistanceM("200 m / 660 ft"))
        assertEquals(50, UnitParser.waterResistanceM("5 ATM"))
        assertEquals(100, UnitParser.waterResistanceM("10 bar"))
        assertEquals(100, UnitParser.waterResistanceM("330 ft"))
        assertEquals(300, UnitParser.waterResistanceM("300m"))
        assertEquals(30, UnitParser.waterResistanceM("3 bar (30 metres)"))
    }

    @Test fun powerReserveInHours() {
        assertClose(40.0, UnitParser.powerReserveH("40 h"))
        assertClose(42.0, UnitParser.powerReserveH("approx. 42 hours"))
        assertClose(72.0, UnitParser.powerReserveH("3 days"))
        assertClose(84.0, UnitParser.powerReserveH("3½ days"))
    }

    @Test fun conversions() {
        assertClose(4.0, Units.bphToHz(28_800))
        assertEquals(21_600, Units.hzToBph(3.0))
        assertClose(5.0, Units.metresToAtm(50))
        assertEquals(100, Units.feetToMetres(330.0))
    }
}
