package io.github.tunlezah.watches.core

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

object Ids {
    /** Random UUID text. IDs are stable across devices and backups, which is what merge-import matches on. */
    fun new(): String = Uuid.random().toString()

    /** Sample watches carry this prefix so the diagnostics tool can remove exactly what it added. */
    const val SAMPLE_PREFIX = "sample-"

    fun newSample(): String = SAMPLE_PREFIX + Uuid.random().toString()
}

/** Injectable time source so tests can pin "now". */
fun interface TimeSource {
    fun now(): Instant

    companion object {
        val System = TimeSource { Clock.System.now() }
    }
}
