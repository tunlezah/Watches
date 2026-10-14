package io.github.tunlezah.watches.core.lookup

import io.github.tunlezah.watches.core.model.Complication
import io.github.tunlezah.watches.core.model.MovementType

/** Maps the words sources use onto the app's fixed vocabularies. Unknown words map to nothing. */
object Vocabulary {

    fun movementType(text: String): MovementType? {
        val t = text.lowercase()
        return when {
            "spring drive" in t -> MovementType.SPRING_DRIVE
            "kinetic" in t || "automatic quartz" in t -> MovementType.KINETIC
            "solar" in t || "eco-drive" in t -> MovementType.SOLAR
            "quartz" in t -> MovementType.QUARTZ
            "automatic" in t || "self-winding" in t || "self winding" in t || "rotor" in t -> MovementType.AUTOMATIC
            "manual" in t || "hand-wound" in t || "hand wound" in t || "hand-winding" in t || "stem winding" in t -> MovementType.MANUAL
            else -> null
        }
    }

    /**
     * Complication keys for a source's list of complication labels. "Day" only counts alongside a date,
     * as day-date. Features that aren't complications here (hacking, quick-set) are ignored.
     */
    fun complications(labels: Collection<String>): Set<String> {
        val lower = labels.map { it.lowercase().trim() }
        val keys = mutableSetOf<String>()
        for (l in lower) {
            complication(l)?.let { keys += it.key }
        }
        val hasDay = lower.any { it == "day" || it == "day of week" || it == "day display" || it.startsWith("day window") }
        if (hasDay && Complication.DATE.key in keys) {
            keys -= Complication.DATE.key
            keys += Complication.DAY_DATE.key
        }
        return keys
    }

    private fun complication(l: String): Complication? = when {
        "minute repeater" in l -> Complication.MINUTE_REPEATER
        "tourbillon" in l -> Complication.TOURBILLON
        "perpetual" in l -> Complication.PERPETUAL_CALENDAR
        "annual calendar" in l -> Complication.ANNUAL_CALENDAR
        "rattrapante" in l || "split-second" in l || "split second" in l -> Complication.RATTRAPANTE
        "flyback" in l || "fly-back" in l -> Complication.FLYBACK
        "chronograph" in l -> Complication.CHRONOGRAPH
        "world time" in l || "worldtime" in l || "world timer" in l -> Complication.WORLD_TIME
        "gmt" in l || "dual time" in l || "second time zone" in l || "second timezone" in l -> Complication.GMT
        "moon" in l -> Complication.MOON_PHASE
        "power reserve" in l -> Complication.POWER_RESERVE
        "alarm" in l -> Complication.ALARM
        "jump hour" in l || "jumping hour" in l -> Complication.JUMP_HOUR
        "small second" in l || "sub second" in l || "subsidiary second" in l || "small-second" in l -> Complication.SMALL_SECONDS
        "big date" in l || "large date" in l || "panorama date" in l || "outsize date" in l -> Complication.BIG_DATE
        l == "date" || l == "date window" || l == "date display" || l == "calendar" -> Complication.DATE
        else -> null
    }

    fun hacking(labels: Collection<String>): Boolean =
        labels.any { val l = it.lowercase(); "hacking" in l || "stop seconds" in l || "stop-seconds" in l || "second stop" in l }
}
