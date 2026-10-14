package io.github.tunlezah.watches.core

import io.github.tunlezah.watches.core.lookup.LookupDiff
import io.github.tunlezah.watches.core.lookup.SourceRecord
import io.github.tunlezah.watches.core.lookup.Vocabulary
import io.github.tunlezah.watches.core.model.Complication
import io.github.tunlezah.watches.core.model.MovementType
import io.github.tunlezah.watches.core.model.Provenance
import io.github.tunlezah.watches.core.model.Sources
import io.github.tunlezah.watches.core.model.Watch
import io.github.tunlezah.watches.core.spec.SpecFields
import io.github.tunlezah.watches.core.spec.SpecValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class LookupDiffTest {

    private val t0 = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val t1 = Instant.fromEpochMilliseconds(1_800_000_000_000)

    private val watch = Watch(
        id = "w1", createdAt = t0, updatedAt = t0,
        brand = "Tudor", caliber = "ETA 2824-2", jewels = 21, powerReserveH = 38.0,
        complications = setOf(Complication.MOON_PHASE.key),
    )

    private val provenance = mapOf(
        SpecFields.Brand.key to Provenance(SpecFields.Brand.key, Sources.MANUAL, userEdited = true),
        SpecFields.Caliber.key to Provenance(SpecFields.Caliber.key, "grail", "https://g/1", t0),
        SpecFields.Jewels.key to Provenance(SpecFields.Jewels.key, "grail", "https://g/1", t0, userEdited = true),
        SpecFields.PowerReserve.key to Provenance(SpecFields.PowerReserve.key, "grail", "https://g/1", t0),
    )

    private val ranfft = SourceRecord(
        source = "ranfft", key = "4187", url = "https://ranfft.org/caliber/4187", title = "ETA 2824-2", fetchedAt = t1,
        values = mapOf(
            SpecFields.Caliber.key to SpecValue.Text("eta  2824-2"),
            SpecFields.Jewels.key to SpecValue.Whole(25),
            SpecFields.PowerReserve.key to SpecValue.Number(40.0),
            SpecFields.Frequency.key to SpecValue.Whole(28_800),
            SpecFields.Complications.key to SpecValue.Keys(setOf(Complication.DATE.key)),
        ),
    )

    private val grail = SourceRecord(
        source = "grail", key = "eta-2824", url = "https://g/2", title = "ETA 2824", fetchedAt = t1,
        values = mapOf(
            SpecFields.PowerReserve.key to SpecValue.Number(42.0),
            SpecFields.Frequency.key to SpecValue.Whole(28_800),
            SpecFields.Brand.key to SpecValue.Text("ETA"),
        ),
    )

    private fun rows() = LookupDiff.rows(watch, provenance, listOf(grail, ranfft)).associateBy { it.field.key }

    @Test fun userEditedValuesStartUnticked() {
        val r = rows()
        val brand = r.getValue(SpecFields.Brand.key)
        assertTrue(brand.protected)
        assertNull(brand.defaultChoice)
        val jewels = r.getValue(SpecFields.Jewels.key)
        assertTrue(jewels.protected)
        assertNull(jewels.defaultChoice)
    }

    @Test fun sourcedValuesAndEmptyFieldsStartTicked() {
        val r = rows()
        assertEquals(0, r.getValue(SpecFields.PowerReserve.key).defaultChoice)
        assertEquals(0, r.getValue(SpecFields.Frequency.key).defaultChoice)
    }

    @Test fun matchingValueNeedsNoChange() {
        val caliber = rows().getValue(SpecFields.Caliber.key)
        assertTrue(caliber.matchesCurrent)
        assertNull(caliber.defaultChoice)
    }

    @Test fun conflictsKeepBothOptionsInPriorityOrder() {
        val power = rows().getValue(SpecFields.PowerReserve.key)
        assertTrue(power.conflict)
        // Movement fields prefer Ranfft over Grail.
        assertEquals(listOf("ranfft", "grail"), power.options.map { it.source })
    }

    @Test fun aSourceAgreeingWithTheWatchKeepsItButShowsTheOther() {
        val watch2 = watch.copy(powerReserveH = 42.0)
        val power = LookupDiff.rows(watch2, provenance, listOf(grail, ranfft)).single { it.field.key == SpecFields.PowerReserve.key }
        assertFalse(power.matchesCurrent)
        assertTrue(power.isCurrent(1))
        assertNull(power.defaultChoice)
    }

    @Test fun agreeingSourcesCollapseIntoOneOption() {
        val freq = rows().getValue(SpecFields.Frequency.key)
        assertEquals(1, freq.options.size)
        assertEquals(listOf("grail"), freq.options.single().agreeing)
    }

    @Test fun complicationsAreAddedNotReplaced() {
        val c = rows().getValue(SpecFields.Complications.key)
        assertEquals(SpecValue.Keys(setOf(Complication.MOON_PHASE.key, Complication.DATE.key)), c.options.single().value)
    }

    @Test fun applyWritesValuesAndSourceProvenance() {
        val rows = LookupDiff.rows(watch, provenance, listOf(grail, ranfft))
        val choices = rows.mapNotNull { r -> r.defaultChoice?.let { r.field.key to it } }.toMap()
        val (updated, prov) = LookupDiff.apply(watch, rows, choices)
        assertEquals("Tudor", updated.brand) // protected, not ticked
        assertEquals(21, updated.jewels)
        assertEquals(40.0, updated.powerReserveH)
        assertEquals(28_800, updated.frequencyBph)
        val p = prov.associateBy { it.fieldKey }
        assertEquals("ranfft", p.getValue(SpecFields.PowerReserve.key).source)
        assertEquals(t1, p.getValue(SpecFields.PowerReserve.key).fetchedAt)
        assertFalse(p.getValue(SpecFields.PowerReserve.key).userEdited)
        assertFalse(SpecFields.Brand.key in p)
    }

    @Test fun explicitTickOverridesAnEdit() {
        val rows = LookupDiff.rows(watch, provenance, listOf(ranfft))
        val (updated, _) = LookupDiff.apply(watch, rows, mapOf(SpecFields.Jewels.key to 0))
        assertEquals(25, updated.jewels)
    }

    @Test fun mergedTakesPreferredValues() {
        val blank = Watch(id = "", createdAt = t0, updatedAt = t0)
        val (merged, prov) = LookupDiff.merged(blank, listOf(grail, ranfft))
        assertEquals(40.0, merged.powerReserveH)
        assertEquals("ETA", merged.brand)
        assertEquals(prov.size, prov.map { it.fieldKey }.toSet().size)
    }

    @Test fun recordsRejectOwnerOnlyFields() {
        assertFailsWith<IllegalArgumentException> {
            SourceRecord("x", "k", "u", "t", t0, values = mapOf(SpecFields.SerialNumber.key to SpecValue.Text("1")))
        }
    }

    @Test fun vocabulary() {
        assertEquals(MovementType.AUTOMATIC, Vocabulary.movementType("Automatic (Rotor-Driven)"))
        assertEquals(MovementType.MANUAL, Vocabulary.movementType("Manual"))
        assertEquals(MovementType.KINETIC, Vocabulary.movementType("Automatic Quartz"))
        assertNull(Vocabulary.movementType("Pocket"))
        assertEquals(
            setOf(Complication.DAY_DATE.key, Complication.CHRONOGRAPH.key),
            Vocabulary.complications(listOf("2-Button", "Chronograph", "Date", "Day", "Hacking Seconds")),
        )
        assertEquals(
            setOf(Complication.SMALL_SECONDS.key, Complication.CHRONOGRAPH.key),
            Vocabulary.complications(listOf("Sub Second (Small Seconds)", "Chronograph Stopwatch", "Chronograph Minute")),
        )
        assertTrue(Vocabulary.hacking(listOf("Date", "Hacking Seconds")))
    }
}
