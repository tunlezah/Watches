package io.github.tunlezah.watches.core.spec

import io.github.tunlezah.watches.core.model.Complication
import io.github.tunlezah.watches.core.model.Condition
import io.github.tunlezah.watches.core.model.Keyed
import io.github.tunlezah.watches.core.model.Money
import io.github.tunlezah.watches.core.model.MovementType
import io.github.tunlezah.watches.core.model.Watch
import io.github.tunlezah.watches.core.model.WatchStatus
import kotlinx.datetime.LocalDate

enum class SpecGroup(val label: String) {
    IDENTITY("Identity"),
    CASE("Case"),
    DIAL("Dial"),
    MOVEMENT("Movement"),
    STRAP("Strap & bracelet"),
    OWNERSHIP("Ownership"),
    SERVICE("Service & warranty"),
}

enum class SpecUnit(val symbol: String) {
    MM("mm"),
    METRES("m"),
    BPH("bph"),
    HOURS("h"),
    MONTHS("months"),
}

sealed interface SpecKind {
    data object Text : SpecKind
    data object LongText : SpecKind
    data class Decimal(val unit: SpecUnit, val maxDecimals: Int = 2) : SpecKind
    data class Whole(val unit: SpecUnit? = null) : SpecKind
    data object Year : SpecKind
    data object Flag : SpecKind
    data class Choice(val options: List<Keyed>) : SpecKind
    data object Date : SpecKind
    data object Price : SpecKind
    data class MultiChoice(val options: List<Keyed>) : SpecKind
    data object Secret : SpecKind
}

sealed interface SpecValue {
    data class Text(val value: String) : SpecValue
    data class Number(val value: Double) : SpecValue
    data class Whole(val value: Int) : SpecValue
    data class Flag(val value: Boolean) : SpecValue
    data class Choice(val key: String) : SpecValue
    data class Date(val value: LocalDate) : SpecValue
    data class Price(val value: Money) : SpecValue
    data class Keys(val value: Set<String>) : SpecValue
}

/**
 * One spec field: its stable key (used for provenance, CSV columns, diffs and the archive format),
 * where it sits, how it's typed, and how to read and write it on a [Watch].
 *
 * [importable] is false for fields only the owner can know (ownership, service), so lookups never touch them.
 */
class SpecField(
    val key: String,
    val group: SpecGroup,
    val label: String,
    val kind: SpecKind,
    val importable: Boolean,
    val get: (Watch) -> SpecValue?,
    val set: (Watch, SpecValue?) -> Watch,
) {
    override fun toString(): String = "SpecField($key)"
}

/** The single registry every screen, export and lookup uses. Keys are permanent: never rename one. */
object SpecFields {

    private fun text(key: String, group: SpecGroup, label: String, get: (Watch) -> String?, set: (Watch, String?) -> Watch, long: Boolean = false, importable: Boolean = true) =
        SpecField(key, group, label, if (long) SpecKind.LongText else SpecKind.Text, importable,
            get = { w -> get(w)?.takeIf { it.isNotBlank() }?.let { SpecValue.Text(it) } },
            set = { w, v -> set(w, (v as? SpecValue.Text)?.value?.trim()?.ifBlank { null }) })

    private fun decimal(key: String, group: SpecGroup, label: String, unit: SpecUnit, get: (Watch) -> Double?, set: (Watch, Double?) -> Watch) =
        SpecField(key, group, label, SpecKind.Decimal(unit), true,
            get = { w -> get(w)?.let { SpecValue.Number(it) } },
            set = { w, v -> set(w, (v as? SpecValue.Number)?.value) })

    private fun whole(key: String, group: SpecGroup, label: String, unit: SpecUnit?, get: (Watch) -> Int?, set: (Watch, Int?) -> Watch, importable: Boolean = true, year: Boolean = false) =
        SpecField(key, group, label, if (year) SpecKind.Year else SpecKind.Whole(unit), importable,
            get = { w -> get(w)?.let { SpecValue.Whole(it) } },
            set = { w, v -> set(w, (v as? SpecValue.Whole)?.value) })

    private fun flag(key: String, group: SpecGroup, label: String, get: (Watch) -> Boolean?, set: (Watch, Boolean?) -> Watch) =
        SpecField(key, group, label, SpecKind.Flag, true,
            get = { w -> get(w)?.let { SpecValue.Flag(it) } },
            set = { w, v -> set(w, (v as? SpecValue.Flag)?.value) })

    private fun date(key: String, group: SpecGroup, label: String, get: (Watch) -> LocalDate?, set: (Watch, LocalDate?) -> Watch) =
        SpecField(key, group, label, SpecKind.Date, false,
            get = { w -> get(w)?.let { SpecValue.Date(it) } },
            set = { w, v -> set(w, (v as? SpecValue.Date)?.value) })

    private inline fun <reified E> choice(key: String, group: SpecGroup, label: String, crossinline get: (Watch) -> E?, crossinline set: (Watch, E?) -> Watch, importable: Boolean = true): SpecField
        where E : Enum<E>, E : Keyed =
        SpecField(key, group, label, SpecKind.Choice(enumValues<E>().toList()), importable,
            get = { w -> get(w)?.let { SpecValue.Choice(it.key) } },
            set = { w, v -> set(w, (v as? SpecValue.Choice)?.key?.let { k -> enumValues<E>().firstOrNull { it.key == k } }) })

    // Identity
    val Brand = text("identity.brand", SpecGroup.IDENTITY, "Brand", { it.brand }, { w, v -> w.copy(brand = v) })
    val Collection = text("identity.collection", SpecGroup.IDENTITY, "Collection", { it.collection }, { w, v -> w.copy(collection = v) })
    val ModelName = text("identity.model_name", SpecGroup.IDENTITY, "Model", { it.modelName }, { w, v -> w.copy(modelName = v) })
    val Reference = text("identity.reference", SpecGroup.IDENTITY, "Reference", { it.reference }, { w, v -> w.copy(reference = v) })
    val Nickname = text("identity.nickname", SpecGroup.IDENTITY, "Nickname", { it.nickname }, { w, v -> w.copy(nickname = v) }, importable = false)
    val YearFrom = whole("identity.year_from", SpecGroup.IDENTITY, "Produced from", null, { it.yearFrom }, { w, v -> w.copy(yearFrom = v) }, year = true)
    val YearTo = whole("identity.year_to", SpecGroup.IDENTITY, "Produced until", null, { it.yearTo }, { w, v -> w.copy(yearTo = v) }, year = true)
    val LimitedNumber = whole("identity.limited_number", SpecGroup.IDENTITY, "Limited edition no.", null, { it.limitedNumber }, { w, v -> w.copy(limitedNumber = v) }, importable = false)
    val LimitedTotal = whole("identity.limited_total", SpecGroup.IDENTITY, "Limited edition of", null, { it.limitedTotal }, { w, v -> w.copy(limitedTotal = v) })

    // Case
    val CaseMaterial = text("case.material", SpecGroup.CASE, "Material", { it.caseMaterial }, { w, v -> w.copy(caseMaterial = v) })
    val Diameter = decimal("case.diameter_mm", SpecGroup.CASE, "Diameter", SpecUnit.MM, { it.diameterMm }, { w, v -> w.copy(diameterMm = v) })
    val Thickness = decimal("case.thickness_mm", SpecGroup.CASE, "Thickness", SpecUnit.MM, { it.thicknessMm }, { w, v -> w.copy(thicknessMm = v) })
    val LugToLug = decimal("case.lug_to_lug_mm", SpecGroup.CASE, "Lug to lug", SpecUnit.MM, { it.lugToLugMm }, { w, v -> w.copy(lugToLugMm = v) })
    val LugWidth = decimal("case.lug_width_mm", SpecGroup.CASE, "Lug width", SpecUnit.MM, { it.lugWidthMm }, { w, v -> w.copy(lugWidthMm = v) })
    val Bezel = text("case.bezel", SpecGroup.CASE, "Bezel", { it.bezel }, { w, v -> w.copy(bezel = v) })
    val Crystal = text("case.crystal", SpecGroup.CASE, "Crystal", { it.crystal }, { w, v -> w.copy(crystal = v) })
    val CaseBack = text("case.case_back", SpecGroup.CASE, "Case back", { it.caseBack }, { w, v -> w.copy(caseBack = v) })
    val Crown = text("case.crown", SpecGroup.CASE, "Crown", { it.crown }, { w, v -> w.copy(crown = v) })
    val WaterResistance = whole("case.water_resistance_m", SpecGroup.CASE, "Water resistance", SpecUnit.METRES, { it.waterResistanceM }, { w, v -> w.copy(waterResistanceM = v) })

    // Dial
    val DialColour = text("dial.colour", SpecGroup.DIAL, "Colour", { it.dialColour }, { w, v -> w.copy(dialColour = v) })
    val DialFinish = text("dial.finish", SpecGroup.DIAL, "Finish", { it.dialFinish }, { w, v -> w.copy(dialFinish = v) })
    val Indices = text("dial.indices", SpecGroup.DIAL, "Indices", { it.indices }, { w, v -> w.copy(indices = v) })
    val Hands = text("dial.hands", SpecGroup.DIAL, "Hands", { it.hands }, { w, v -> w.copy(hands = v) })
    val Lume = text("dial.lume", SpecGroup.DIAL, "Lume", { it.lume }, { w, v -> w.copy(lume = v) })

    // Movement
    val Caliber = text("movement.caliber", SpecGroup.MOVEMENT, "Calibre", { it.caliber }, { w, v -> w.copy(caliber = v) })
    val BaseCaliber = text("movement.base_caliber", SpecGroup.MOVEMENT, "Base calibre", { it.baseCaliber }, { w, v -> w.copy(baseCaliber = v) })
    val MovementTypeField = choice<MovementType>("movement.type", SpecGroup.MOVEMENT, "Type", { it.movementType }, { w, v -> w.copy(movementType = v) })
    val Jewels = whole("movement.jewels", SpecGroup.MOVEMENT, "Jewels", null, { it.jewels }, { w, v -> w.copy(jewels = v) })
    val Frequency = whole("movement.frequency_bph", SpecGroup.MOVEMENT, "Frequency", SpecUnit.BPH, { it.frequencyBph }, { w, v -> w.copy(frequencyBph = v) })
    val PowerReserve = decimal("movement.power_reserve_h", SpecGroup.MOVEMENT, "Power reserve", SpecUnit.HOURS, { it.powerReserveH }, { w, v -> w.copy(powerReserveH = v) })
    val Hacking = flag("movement.hacking", SpecGroup.MOVEMENT, "Hacking seconds", { it.hacking }, { w, v -> w.copy(hacking = v) })
    val HandWinding = flag("movement.hand_winding", SpecGroup.MOVEMENT, "Hand-winding", { it.handWinding }, { w, v -> w.copy(handWinding = v) })
    val Complications = SpecField("movement.complications", SpecGroup.MOVEMENT, "Complications",
        SpecKind.MultiChoice(Complication.entries), true,
        get = { w -> w.complications.takeIf { it.isNotEmpty() }?.let { SpecValue.Keys(it) } },
        set = { w, v -> w.copy(complications = (v as? SpecValue.Keys)?.value ?: emptySet()) })

    // Strap / bracelet
    val StrapType = text("strap.type", SpecGroup.STRAP, "Type", { it.strapType }, { w, v -> w.copy(strapType = v) })
    val StrapMaterial = text("strap.material", SpecGroup.STRAP, "Material", { it.strapMaterial }, { w, v -> w.copy(strapMaterial = v) })
    val Clasp = text("strap.clasp", SpecGroup.STRAP, "Clasp", { it.clasp }, { w, v -> w.copy(clasp = v) })
    val FittedStrap = text("strap.fitted", SpecGroup.STRAP, "Currently fitted", { it.fittedStrap }, { w, v -> w.copy(fittedStrap = v) }, importable = false)

    // Ownership: never filled by a lookup.
    val Status = SpecField("ownership.status", SpecGroup.OWNERSHIP, "Status", SpecKind.Choice(WatchStatus.entries), false,
        get = { w -> SpecValue.Choice(w.status.key) },
        set = { w, v -> w.copy(status = WatchStatus.entries.firstOrNull { it.key == (v as? SpecValue.Choice)?.key } ?: WatchStatus.OWNED) })
    val AcquiredOn = date("ownership.acquired_on", SpecGroup.OWNERSHIP, "Date acquired", { it.acquiredOn }, { w, v -> w.copy(acquiredOn = v) })
    val AcquiredFrom = text("ownership.acquired_from", SpecGroup.OWNERSHIP, "Acquired from", { it.acquiredFrom }, { w, v -> w.copy(acquiredFrom = v) }, importable = false)
    val PricePaid = SpecField("ownership.price_paid", SpecGroup.OWNERSHIP, "Price paid", SpecKind.Price, false,
        get = { w -> w.pricePaid?.let { SpecValue.Price(it) } },
        set = { w, v -> w.copy(pricePaid = (v as? SpecValue.Price)?.value) })
    val ConditionField = choice<Condition>("ownership.condition", SpecGroup.OWNERSHIP, "Condition", { it.condition }, { w, v -> w.copy(condition = v) }, importable = false)
    val SerialNumber = SpecField("ownership.serial_number", SpecGroup.OWNERSHIP, "Serial number", SpecKind.Secret, false,
        get = { w -> w.serialNumber?.takeIf { it.isNotBlank() }?.let { SpecValue.Text(it) } },
        set = { w, v -> w.copy(serialNumber = (v as? SpecValue.Text)?.value?.trim()?.ifBlank { null }) })
    val PrivateNotes = text("ownership.private_notes", SpecGroup.OWNERSHIP, "Private notes", { it.privateNotes }, { w, v -> w.copy(privateNotes = v) }, long = true, importable = false)
    val Story = text("ownership.story", SpecGroup.OWNERSHIP, "The watch's story", { it.story }, { w, v -> w.copy(story = v) }, long = true, importable = false)

    // Service & warranty settings
    val ServiceInterval = whole("service.interval_months", SpecGroup.SERVICE, "Service interval", SpecUnit.MONTHS, { it.serviceIntervalMonths }, { w, v -> w.copy(serviceIntervalMonths = v) }, importable = false)
    val WarrantyStart = date("service.warranty_start", SpecGroup.SERVICE, "Warranty start", { it.warrantyStart }, { w, v -> w.copy(warrantyStart = v) })
    val WarrantyLength = whole("service.warranty_months", SpecGroup.SERVICE, "Warranty length", SpecUnit.MONTHS, { it.warrantyMonths }, { w, v -> w.copy(warrantyMonths = v) }, importable = false)

    val all: List<SpecField> = listOf(
        Brand, Collection, ModelName, Reference, Nickname, YearFrom, YearTo, LimitedNumber, LimitedTotal,
        CaseMaterial, Diameter, Thickness, LugToLug, LugWidth, Bezel, Crystal, CaseBack, Crown, WaterResistance,
        DialColour, DialFinish, Indices, Hands, Lume,
        Caliber, BaseCaliber, MovementTypeField, Jewels, Frequency, PowerReserve, Hacking, HandWinding, Complications,
        StrapType, StrapMaterial, Clasp, FittedStrap,
        Status, AcquiredOn, AcquiredFrom, PricePaid, ConditionField, SerialNumber, PrivateNotes, Story,
        ServiceInterval, WarrantyStart, WarrantyLength,
    )

    private val byKey: Map<String, SpecField> = all.associateBy { it.key }

    fun byKey(key: String): SpecField? = byKey[key]

    fun inGroup(group: SpecGroup): List<SpecField> = all.filter { it.group == group }

    /** Keys whose values differ between two versions of a watch. */
    fun changedKeys(before: Watch, after: Watch): Set<String> =
        all.filter { it.get(before) != it.get(after) }.mapTo(mutableSetOf()) { it.key }
}
