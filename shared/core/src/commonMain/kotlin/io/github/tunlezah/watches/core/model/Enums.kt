package io.github.tunlezah.watches.core.model

/** An enum value with a stable storage key (never renamed) and a display label. */
interface Keyed {
    val key: String
    val label: String
}

inline fun <reified E> keyedOf(key: String?): E? where E : Enum<E>, E : Keyed =
    if (key == null) null else enumValues<E>().firstOrNull { it.key == key }

enum class WatchStatus(override val key: String, override val label: String) : Keyed {
    OWNED("owned", "Owned"),
    WISHLIST("wishlist", "Wishlist"),
    INCOMING("incoming", "Incoming"),
    IN_SERVICE("in_service", "In service"),
    SOLD("sold", "Sold / gone"),
}

enum class MovementType(override val key: String, override val label: String) : Keyed {
    AUTOMATIC("automatic", "Automatic"),
    MANUAL("manual", "Manual winding"),
    QUARTZ("quartz", "Quartz"),
    SOLAR("solar", "Solar"),
    SPRING_DRIVE("spring_drive", "Spring Drive"),
    KINETIC("kinetic", "Kinetic"),
    OTHER("other", "Other"),
}

enum class Condition(override val key: String, override val label: String) : Keyed {
    UNWORN("unworn", "Unworn"),
    MINT("mint", "Mint"),
    EXCELLENT("excellent", "Excellent"),
    VERY_GOOD("very_good", "Very good"),
    GOOD("good", "Good"),
    FAIR("fair", "Fair"),
    POOR("poor", "Poor"),
}

/**
 * Built-in complications. A watch can also carry custom ones, stored as `custom:<label>`.
 */
enum class Complication(override val key: String, override val label: String) : Keyed {
    DATE("date", "Date"),
    DAY_DATE("day_date", "Day-date"),
    BIG_DATE("big_date", "Big date"),
    GMT("gmt", "GMT / dual time"),
    WORLD_TIME("world_time", "World time"),
    CHRONOGRAPH("chronograph", "Chronograph"),
    FLYBACK("flyback", "Flyback"),
    RATTRAPANTE("rattrapante", "Rattrapante"),
    MOON_PHASE("moon_phase", "Moon phase"),
    POWER_RESERVE("power_reserve", "Power-reserve indicator"),
    ALARM("alarm", "Alarm"),
    ANNUAL_CALENDAR("annual_calendar", "Annual calendar"),
    PERPETUAL_CALENDAR("perpetual_calendar", "Perpetual calendar"),
    SMALL_SECONDS("small_seconds", "Small seconds"),
    JUMP_HOUR("jump_hour", "Jump hour"),
    TOURBILLON("tourbillon", "Tourbillon"),
    MINUTE_REPEATER("minute_repeater", "Minute repeater");

    companion object {
        const val CUSTOM_PREFIX = "custom:"

        /** "|" is the list separator in the collection query, so it can't appear in a key. */
        fun customKey(label: String): String = CUSTOM_PREFIX + label.replace('|', '/').trim()

        fun labelFor(key: String): String =
            entries.firstOrNull { it.key == key }?.label ?: key.removePrefix(CUSTOM_PREFIX)

        /** Built-ins first in declaration order, then custom ones alphabetically. */
        fun sortKeys(keys: Collection<String>): List<String> {
            val order = entries.withIndex().associate { (i, c) -> c.key to i }
            return keys.sortedWith(compareBy<String>({ order[it] ?: Int.MAX_VALUE }, { labelFor(it).lowercase() }))
        }
    }
}

enum class BoxItem(override val key: String, override val label: String) : Keyed {
    INNER_BOX("inner_box", "Inner box"),
    OUTER_BOX("outer_box", "Outer box"),
    WARRANTY_CARD("warranty_card", "Warranty card / papers"),
    MANUAL("manual", "Manual"),
    HANG_TAGS("hang_tags", "Hang tags"),
    EXTRA_LINKS("extra_links", "Extra links"),
    RECEIPT("receipt", "Receipt"),
    OTHER("other", "Other"),
}

enum class BoxItemState(override val key: String, override val label: String) : Keyed {
    PRESENT("present", "Present"),
    MISSING("missing", "Missing"),
    NOT_APPLICABLE("not_applicable", "Not applicable"),
}

enum class ServiceWork(override val key: String, override val label: String) : Keyed {
    FULL_SERVICE("full_service", "Full service"),
    REGULATION("regulation", "Regulation"),
    CRYSTAL("crystal", "Crystal"),
    GASKETS("gaskets", "Gaskets"),
    BATTERY("battery", "Battery"),
    POLISH("polish", "Polish"),
    OTHER("other", "Other"),
}

enum class DocumentKind(override val key: String, override val label: String) : Keyed {
    RECEIPT("receipt", "Receipt"),
    WARRANTY("warranty", "Warranty"),
    INVOICE("invoice", "Invoice"),
    CERTIFICATE("certificate", "Certificate"),
    MANUAL("manual", "Manual"),
    OTHER("other", "Other"),
}

enum class PhotoKind(override val key: String, override val label: String) : Keyed {
    USER("user", "Your photo"),
    REFERENCE("reference", "Reference image"),
}
