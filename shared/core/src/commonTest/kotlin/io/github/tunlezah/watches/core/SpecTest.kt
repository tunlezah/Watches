package io.github.tunlezah.watches.core

import io.github.tunlezah.watches.core.format.DisplayPrefs
import io.github.tunlezah.watches.core.format.WaterResistanceUnit
import io.github.tunlezah.watches.core.model.Complication
import io.github.tunlezah.watches.core.model.Money
import io.github.tunlezah.watches.core.model.MovementType
import io.github.tunlezah.watches.core.model.Provenance
import io.github.tunlezah.watches.core.model.Sources
import io.github.tunlezah.watches.core.model.Watch
import io.github.tunlezah.watches.core.spec.SpecFields
import io.github.tunlezah.watches.core.spec.SpecGroup
import io.github.tunlezah.watches.core.spec.SpecSheet
import io.github.tunlezah.watches.core.spec.SpecValue
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class SpecTest {

    private val t0 = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private val speedmaster = Watch(
        id = "w1", createdAt = t0, updatedAt = t0,
        brand = "Omega", collection = "Speedmaster", modelName = "Speedmaster Professional",
        reference = "310.30.42.50.01.001", yearFrom = 2021,
        caseMaterial = "Stainless steel", diameterMm = 42.0, thicknessMm = 13.18, lugToLugMm = 47.5, lugWidthMm = 20.0,
        waterResistanceM = 50, caliber = "3861", baseCaliber = "1861", movementType = MovementType.MANUAL,
        jewels = 26, frequencyBph = 21_600, powerReserveH = 50.0,
        complications = setOf(Complication.SMALL_SECONDS.key, Complication.CHRONOGRAPH.key),
        acquiredOn = LocalDate(2023, 3, 14), pricePaid = Money(1_095_000, "AUD"), serialNumber = "12345678",
    )

    @Test fun keysAreUnique() {
        val keys = SpecFields.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test fun everyFieldRoundTrips() {
        for (field in SpecFields.all) {
            val value = field.get(speedmaster) ?: continue
            val cleared = field.set(speedmaster, null)
            val restored = field.set(cleared, value)
            assertEquals(value, field.get(restored), "round trip of ${field.key}")
        }
    }

    @Test fun blankTextBecomesNull() {
        val w = SpecFields.Bezel.set(speedmaster, SpecValue.Text("   "))
        assertNull(w.bezel)
    }

    @Test fun ownershipIsNeverImportable() {
        assertTrue(SpecFields.inGroup(SpecGroup.OWNERSHIP).none { it.importable })
        assertTrue(SpecFields.Diameter.importable)
    }

    @Test fun changedKeysFindsEdits() {
        val edited = speedmaster.copy(diameterMm = 42.5, bezel = "Ceramic")
        assertEquals(setOf("case.diameter_mm", "case.bezel"), SpecFields.changedKeys(speedmaster, edited))
    }

    @Test fun sheetFormatsUnitsAndCombinesRows() {
        val prov = mapOf(
            "case.diameter_mm" to Provenance("case.diameter_mm", "watchbase", "https://watchbase.com/x", t0),
            "case.lug_to_lug_mm" to Provenance("case.lug_to_lug_mm", "watchbase", null, t0, userEdited = true),
            "case.material" to Provenance("case.material", Sources.MANUAL, userEdited = true),
        )
        val sheet = SpecSheet.build(speedmaster, prov, DisplayPrefs())
        assertEquals(listOf(SpecGroup.IDENTITY, SpecGroup.CASE, SpecGroup.MOVEMENT, SpecGroup.OWNERSHIP), sheet.map { it.group })

        val case = sheet.first { it.group == SpecGroup.CASE }.rows.associateBy { it.label }
        assertEquals("42", case.getValue("Diameter").value.value)
        assertEquals("mm", case.getValue("Diameter").value.unit)
        assertEquals("WatchBase", case.getValue("Diameter").sourceLabel)
        assertFalse(case.getValue("Diameter").userEdited)
        assertTrue(case.getValue("Lug to lug").userEdited)
        assertNull(case.getValue("Material").sourceLabel, "manual values carry no source badge")
        assertEquals("m · 5 ATM", case.getValue("Water resistance").value.unit)

        val identity = sheet.first { it.group == SpecGroup.IDENTITY }.rows
        assertEquals("Reference", identity.first().label)
        assertEquals("2021–present", identity.first { it.label == "Production" }.value.value)

        val movement = sheet.first { it.group == SpecGroup.MOVEMENT }.rows.associateBy { it.label }
        assertEquals("21,600", movement.getValue("Frequency").value.value)
        assertEquals("bph · 3 Hz", movement.getValue("Frequency").value.unit)
        assertEquals("Chronograph, Small seconds", movement.getValue("Complications").value.value)

        val ownership = sheet.first { it.group == SpecGroup.OWNERSHIP }.rows.associateBy { it.label }
        assertEquals("14/03/2023", ownership.getValue("Date acquired").value.value)
        assertEquals("A$10,950", ownership.getValue("Price paid").value.value)
        assertTrue(ownership.getValue("Serial number").secret)
    }

    @Test fun barPreference() {
        val sheet = SpecSheet.build(speedmaster, emptyMap(), DisplayPrefs(waterResistanceUnit = WaterResistanceUnit.BAR))
        val wr = sheet.first { it.group == SpecGroup.CASE }.rows.first { it.label == "Water resistance" }
        assertEquals("m · 5 bar", wr.value.unit)
    }

    @Test fun longPowerReserveShowsDays() {
        val sheet = SpecSheet.build(speedmaster.copy(powerReserveH = 80.0), emptyMap(), DisplayPrefs())
        val pr = sheet.first { it.group == SpecGroup.MOVEMENT }.rows.first { it.label == "Power reserve" }
        assertEquals("h · 3.3 days", pr.value.unit)
    }

    @Test fun limitedEditionRow() {
        val sheet = SpecSheet.build(speedmaster.copy(limitedNumber = 12, limitedTotal = 1_957), emptyMap(), DisplayPrefs())
        val row = sheet.first { it.group == SpecGroup.IDENTITY }.rows.first { it.label == "Limited edition" }
        assertEquals("No. 12", row.value.value)
        assertEquals("of 1,957", row.value.unit)
    }
}
