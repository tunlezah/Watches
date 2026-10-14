package io.github.tunlezah.watches.data.sample

import io.github.tunlezah.watches.core.Ids
import io.github.tunlezah.watches.core.model.Complication
import io.github.tunlezah.watches.core.model.Condition
import io.github.tunlezah.watches.core.model.Money
import io.github.tunlezah.watches.core.model.MovementType
import io.github.tunlezah.watches.core.model.Watch
import io.github.tunlezah.watches.core.model.WatchStatus
import kotlinx.datetime.LocalDate
import kotlin.random.Random
import kotlin.time.Instant

/** Colours for drawing a sample watch, as 0xAARRGGBB. */
data class SampleLook(
    val dial: Long,
    val dialHighlight: Long,
    val bezel: Long,
    val case: Long,
    val strap: Long,
    val index: Long,
    val hand: Long,
    val seconds: Long,
    val chronograph: Boolean,
)

data class SampleWatch(val watch: Watch, val look: SampleLook)

/**
 * Plausible test watches for the 500-watch performance check. Everything generated here is
 * marked with [Ids.SAMPLE_PREFIX] so the diagnostics screen can remove exactly what it added.
 */
object SampleCatalog {

    private class Dial(val name: String, val colour: Long, val highlight: Long, val index: Long, val hand: Long, val seconds: Long = hand)

    private class Template(
        val brand: String, val collection: String, val model: String, val reference: String,
        val diameter: Double, val thickness: Double, val lugToLug: Double?, val lugWidth: Double,
        val wr: Int, val material: String, val crystal: String, val bezel: String,
        val caliber: String, val type: MovementType, val jewels: Int?, val bph: Int?, val reserve: Double?,
        val complications: Set<Complication>, val strap: String, val priceAud: IntRange,
        val dials: List<Dial>, val bezelColour: Long? = null, val chronograph: Boolean = false,
    )

    private val black = Dial("Black", 0xFF15161A, 0xFF2C2E34, 0xFFECE8DE, 0xFFF1EEE6)
    private val blue = Dial("Blue", 0xFF1D3557, 0xFF35577F, 0xFFF0F0F0, 0xFFF0F0F0)
    private val white = Dial("White", 0xFFE9ECEF, 0xFFFFFFFF, 0xFF4E5560, 0xFF3A4048)
    private val silver = Dial("Silver", 0xFFD9DADC, 0xFFF4F4F5, 0xFF2A2C30, 0xFF2A2C30)
    private val green = Dial("Green", 0xFF1F4D3A, 0xFF2F6B52, 0xFFF0EDE2, 0xFFF0EDE2)
    private val grey = Dial("Grey", 0xFF3A3F46, 0xFF646B74, 0xFFF0F0F0, 0xFFF0F0F0)
    private val cream = Dial("Cream", 0xFFF1EEE7, 0xFFFFFFFF, 0xFF1E1E1E, 0xFF25418E)
    private val gilt = Dial("Black, gilt", 0xFF141414, 0xFF2A2826, 0xFFE6D3A3, 0xFFD9C48F)
    private val salmon = Dial("Salmon", 0xFFE8B4A0, 0xFFF6D3C4, 0xFF3A2A24, 0xFF3A2A24)

    private const val STEEL = 0xFFAEB3BA
    private const val TITANIUM = 0xFF9EA3A8
    private const val GOLD = 0xFFC9A45C
    private const val BLACK_CASE = 0xFF2A2B2E

    private val templates = listOf(
        Template("Omega", "Speedmaster", "Speedmaster Professional", "310.30.42.50.01.001", 42.0, 13.2, 47.5, 20.0, 50,
            "Stainless steel", "Hesalite", "Aluminium tachymeter", "3861", MovementType.MANUAL, 26, 21_600, 50.0,
            setOf(Complication.CHRONOGRAPH, Complication.SMALL_SECONDS), "Steel bracelet", 9_000..12_000, listOf(black), 0xFF0D0E10, true),
        Template("Omega", "Seamaster", "Seamaster Diver 300M", "210.30.42.20.03.001", 42.0, 13.6, 50.0, 20.0, 300,
            "Stainless steel", "Sapphire", "Ceramic diving bezel", "8800", MovementType.AUTOMATIC, 35, 25_200, 55.0,
            setOf(Complication.DATE), "Steel bracelet", 7_500..9_500, listOf(blue, black, white), 0xFF1A2B44),
        Template("Rolex", "Submariner", "Submariner Date", "126610LN", 41.0, 12.5, 48.0, 21.0, 300,
            "Oystersteel", "Sapphire", "Cerachrom diving bezel", "3235", MovementType.AUTOMATIC, 31, 28_800, 70.0,
            setOf(Complication.DATE), "Oyster bracelet", 15_000..22_000, listOf(black), 0xFF101012),
        Template("Rolex", "Explorer", "Explorer", "124270", 36.0, 11.0, 43.6, 19.0, 100,
            "Oystersteel", "Sapphire", "Smooth", "3230", MovementType.AUTOMATIC, 31, 28_800, 70.0,
            emptySet(), "Oyster bracelet", 10_000..13_000, listOf(black)),
        Template("Rolex", "GMT-Master II", "GMT-Master II", "126710BLRO", 40.0, 12.0, 47.5, 20.0, 100,
            "Oystersteel", "Sapphire", "Two-tone Cerachrom 24-hour", "3285", MovementType.AUTOMATIC, 31, 28_800, 70.0,
            setOf(Complication.GMT, Complication.DATE), "Jubilee bracelet", 20_000..30_000, listOf(black), 0xFF6A1F2B),
        Template("Tudor", "Black Bay", "Black Bay 58", "M79030N-0001", 39.0, 11.9, 47.8, 20.0, 200,
            "Stainless steel", "Domed sapphire", "Aluminium diving bezel", "MT5402", MovementType.AUTOMATIC, 27, 28_800, 70.0,
            emptySet(), "Riveted bracelet", 5_000..6_500, listOf(gilt, blue), 0xFF191919),
        Template("Tudor", "Pelagos", "Pelagos 39", "M25407N-0001", 39.0, 11.8, 47.0, 20.0, 200,
            "Titanium", "Sapphire", "Ceramic diving bezel", "MT5400", MovementType.AUTOMATIC, 27, 28_800, 70.0,
            emptySet(), "Titanium bracelet", 6_000..7_500, listOf(black), 0xFF121314),
        Template("Grand Seiko", "Evolution 9", "Snowflake", "SBGA211", 41.0, 12.5, 49.0, 20.0, 100,
            "High-intensity titanium", "Sapphire", "Polished", "9R65", MovementType.SPRING_DRIVE, 30, null, 72.0,
            setOf(Complication.DATE, Complication.POWER_RESERVE), "Titanium bracelet", 8_000..10_500,
            listOf(Dial("White, snowflake", 0xFFE9ECEF, 0xFFFFFFFF, 0xFF7E8894, 0xFF5E6873, 0xFF2F6FD6))),
        Template("Grand Seiko", "Heritage", "Hi-Beat 36000", "SBGH201", 40.0, 13.0, 47.0, 19.0, 100,
            "Stainless steel", "Sapphire", "Polished", "9S85", MovementType.AUTOMATIC, 37, 36_000, 55.0,
            setOf(Complication.DATE), "Steel bracelet", 8_500..10_500, listOf(white, silver)),
        Template("Seiko", "Prospex", "Prospex 62MAS", "SPB143J1", 40.5, 13.2, 47.6, 20.0, 200,
            "Stainless steel", "Sapphire", "Aluminium diving bezel", "6R35", MovementType.AUTOMATIC, 24, 21_600, 70.0,
            setOf(Complication.DATE), "Steel bracelet", 1_400..1_900, listOf(grey, blue, green), 0xFF141517),
        Template("Seiko", "5 Sports", "5 Sports", "SRPD55K1", 42.5, 13.4, 46.0, 22.0, 100,
            "Stainless steel", "Hardlex", "Rotating", "4R36", MovementType.AUTOMATIC, 24, 21_600, 41.0,
            setOf(Complication.DAY_DATE), "Steel bracelet", 350..500, listOf(black, blue, green), 0xFF18191B),
        Template("Nomos", "Tangente", "Tangente 38", "164", 37.5, 6.8, 47.0, 18.0, 30,
            "Stainless steel", "Sapphire", "Polished", "Alpha", MovementType.MANUAL, 17, 21_600, 43.0,
            setOf(Complication.SMALL_SECONDS), "Horween leather", 3_000..3_800, listOf(cream)),
        Template("IWC", "Pilot's Watch", "Mark XX", "IW328201", 40.0, 11.0, 49.0, 20.0, 100,
            "Stainless steel", "Sapphire", "Fixed", "32111", MovementType.AUTOMATIC, 21, 28_800, 120.0,
            setOf(Complication.DATE), "Calfskin strap", 7_000..8_500, listOf(black, blue)),
        Template("Sinn", "Diving", "556 I", "556.010", 38.5, 11.0, 46.5, 20.0, 200,
            "Stainless steel", "Sapphire", "Fixed", "SW200-1", MovementType.AUTOMATIC, 26, 28_800, 38.0,
            setOf(Complication.DATE), "Steel bracelet", 1_900..2_500, listOf(black, white)),
        Template("Longines", "Spirit", "Spirit 40", "L3.810.4.53.6", 40.0, 12.3, 49.0, 20.0, 100,
            "Stainless steel", "Sapphire", "Fixed", "L888.4", MovementType.AUTOMATIC, 21, 25_200, 72.0,
            setOf(Complication.DATE), "Steel bracelet", 3_500..4_500, listOf(black, blue, silver)),
        Template("Hamilton", "Khaki Field", "Khaki Field Mechanical", "H69439931", 38.0, 9.5, 47.0, 20.0, 50,
            "Stainless steel", "Sapphire", "Fixed", "H-50", MovementType.MANUAL, 17, 21_600, 80.0,
            emptySet(), "Canvas strap", 700..900, listOf(black, green)),
        Template("Zenith", "Chronomaster", "Chronomaster Original", "03.3200.3600/69.C902", 38.0, 12.6, 45.5, 19.0, 50,
            "Stainless steel", "Sapphire", "Fixed", "El Primero 3600", MovementType.AUTOMATIC, 35, 36_000, 60.0,
            setOf(Complication.CHRONOGRAPH, Complication.DATE), "Steel bracelet", 13_000..15_500, listOf(white), chronograph = true),
        Template("Patek Philippe", "Calatrava", "Calatrava", "5196G-001", 37.0, 7.7, 44.0, 19.0, 30,
            "White gold", "Sapphire", "Polished", "215 PS", MovementType.MANUAL, 18, 28_800, 44.0,
            setOf(Complication.SMALL_SECONDS), "Alligator strap", 45_000..55_000, listOf(silver)),
        Template("Jaeger-LeCoultre", "Master Ultra Thin", "Master Ultra Thin Moon", "Q1368420", 39.0, 9.9, 46.0, 20.0, 50,
            "Stainless steel", "Sapphire", "Polished", "925", MovementType.AUTOMATIC, 30, 28_800, 70.0,
            setOf(Complication.MOON_PHASE, Complication.DATE), "Alligator strap", 14_000..17_000, listOf(silver, blue)),
        Template("Casio", "G-Shock", "CasiOak", "GA-2100-1A1", 45.4, 11.8, 48.5, 22.0, 200,
            "Resin, carbon core", "Mineral", "Resin", "5611", MovementType.QUARTZ, null, null, null,
            setOf(Complication.WORLD_TIME, Complication.ALARM), "Resin strap", 150..250, listOf(black), 0xFF1B1B1C),
        Template("Citizen", "Promaster", "Promaster Diver", "BN0150-28E", 44.0, 13.0, 47.0, 22.0, 200,
            "Stainless steel", "Mineral", "Rotating", "E168", MovementType.SOLAR, null, null, 4_320.0,
            setOf(Complication.DATE), "Polyurethane strap", 300..450, listOf(black), 0xFF141516),
        Template("Oris", "Aquis", "Aquis Date", "01 733 7766 4135", 41.5, 12.7, 47.0, 22.0, 300,
            "Stainless steel", "Sapphire", "Ceramic diving bezel", "Oris 733", MovementType.AUTOMATIC, 26, 28_800, 38.0,
            setOf(Complication.DATE), "Steel bracelet", 3_200..4_000, listOf(blue, green, grey), 0xFF16243A),
        Template("Breitling", "Navitimer", "Navitimer B01 Chronograph 43", "AB0138211B1A1", 43.0, 13.6, 50.5, 22.0, 30,
            "Stainless steel", "Sapphire", "Slide rule", "B01", MovementType.AUTOMATIC, 47, 28_800, 70.0,
            setOf(Complication.CHRONOGRAPH, Complication.DATE), "Steel bracelet", 13_000..15_000, listOf(black, blue), 0xFF9EA3A8, true),
        Template("Panerai", "Luminor", "Luminor Base Logo", "PAM01086", 44.0, 15.5, 52.0, 24.0, 100,
            "Stainless steel", "Sapphire", "Polished", "P.6000", MovementType.MANUAL, 19, 21_600, 72.0,
            emptySet(), "Leather strap", 6_500..8_000, listOf(black)),
        Template("TAG Heuer", "Carrera", "Carrera Chronograph 39", "CBS2210.FC6534", 39.0, 14.0, 46.0, 20.0, 100,
            "Stainless steel", "Sapphire", "Fixed", "Heuer 02", MovementType.AUTOMATIC, 33, 28_800, 80.0,
            setOf(Complication.CHRONOGRAPH), "Leather strap", 8_500..10_000, listOf(salmon, silver, black), chronograph = true),
        Template("Cartier", "Santos", "Santos de Cartier", "WSSA0029", 39.8, 9.1, 47.5, 21.0, 100,
            "Stainless steel", "Sapphire", "Screwed", "1847 MC", MovementType.AUTOMATIC, 23, 28_800, 42.0,
            emptySet(), "Steel bracelet", 11_000..12_500, listOf(silver)),
    )

    private val sellers = listOf("Authorised dealer", "Boutique", "Private sale", "Online pre-owned dealer", "Auction", "Gift")
    private val nicknames = listOf("The daily", "Weekend diver", "Anniversary", "The travel watch", "Birthday gift", "Grail")

    fun generate(count: Int, seed: Int = 42, now: Instant): List<SampleWatch> {
        val random = Random(seed)
        return List(count) { i ->
            val t = templates[random.nextInt(templates.size)]
            val dial = t.dials[random.nextInt(t.dials.size)]
            val status = when (random.nextInt(100)) {
                in 0..69 -> WatchStatus.OWNED
                in 70..84 -> WatchStatus.WISHLIST
                in 85..89 -> WatchStatus.INCOMING
                in 90..94 -> WatchStatus.IN_SERVICE
                else -> WatchStatus.SOLD
            }
            val owned = status != WatchStatus.WISHLIST && status != WatchStatus.INCOMING
            val acquired = if (owned) LocalDate(2008 + random.nextInt(18), 1 + random.nextInt(12), 1 + random.nextInt(28)) else null
            val created = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - (count - i) * 60_000L)
            val watch = Watch(
                id = Ids.newSample(),
                status = status,
                createdAt = created,
                updatedAt = created,
                brand = t.brand,
                collection = t.collection,
                modelName = t.model,
                reference = t.reference,
                nickname = if (random.nextInt(10) == 0) nicknames[random.nextInt(nicknames.size)] else null,
                caseMaterial = t.material,
                diameterMm = t.diameter,
                thicknessMm = t.thickness,
                lugToLugMm = t.lugToLug,
                lugWidthMm = t.lugWidth,
                bezel = t.bezel,
                crystal = t.crystal,
                waterResistanceM = t.wr,
                dialColour = dial.name,
                caliber = t.caliber,
                movementType = t.type,
                jewels = t.jewels,
                frequencyBph = t.bph,
                powerReserveH = t.reserve,
                hacking = if (t.type == MovementType.QUARTZ || t.type == MovementType.SOLAR) null else random.nextBoolean(),
                handWinding = if (t.type == MovementType.MANUAL) true else null,
                complications = t.complications.mapTo(mutableSetOf()) { it.key },
                strapType = t.strap,
                acquiredOn = acquired,
                acquiredFrom = if (owned) sellers[random.nextInt(sellers.size)] else null,
                pricePaid = if (owned) Money(t.priceAud.random(random) * 100L, "AUD") else null,
                condition = if (owned) Condition.entries[random.nextInt(2, 6)] else null,
                serialNumber = if (owned) List(8) { random.nextInt(10) }.joinToString("") else null,
                privateNotes = if (random.nextInt(4) == 0) "Sample watch $i for performance testing." else null,
                serviceIntervalMonths = if (t.type == MovementType.QUARTZ) null else 60,
            )
            val look = SampleLook(
                dial = dial.colour,
                dialHighlight = dial.highlight,
                bezel = t.bezelColour ?: caseColour(t.material),
                case = caseColour(t.material),
                strap = strapColour(t.strap, random),
                index = dial.index,
                hand = dial.hand,
                seconds = dial.seconds,
                chronograph = t.chronograph,
            )
            SampleWatch(watch, look)
        }
    }

    private fun caseColour(material: String): Long = when {
        "gold" in material.lowercase() && "white" !in material.lowercase() -> GOLD
        "titanium" in material.lowercase() -> TITANIUM
        "resin" in material.lowercase() -> BLACK_CASE
        else -> STEEL
    }

    private fun strapColour(strap: String, random: Random): Long = when {
        "bracelet" in strap.lowercase() -> STEEL
        "leather" in strap.lowercase() || "calf" in strap.lowercase() || "alligator" in strap.lowercase() ->
            listOf(0xFF5B3B29, 0xFF3A2618, 0xFF1E1A17)[random.nextInt(3)]
        "canvas" in strap.lowercase() -> 0xFF4A5238
        else -> 0xFF1B1C1E
    }
}
