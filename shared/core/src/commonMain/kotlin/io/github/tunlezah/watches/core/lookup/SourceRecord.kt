package io.github.tunlezah.watches.core.lookup

import io.github.tunlezah.watches.core.spec.SpecFields
import io.github.tunlezah.watches.core.spec.SpecGroup
import io.github.tunlezah.watches.core.spec.SpecValue
import kotlin.time.Instant

/** What a source can tell us about. Shown on its card and used to order conflicting values. */
enum class Coverage(val label: String) {
    WATCH("Full watch specs"),
    MOVEMENT("Movements"),
    IDENTITY("Brand, model and history"),
}

/** One row of a source's search results. [key] is the source's own id for it (slug, Q-id, calibre id). */
data class SearchHit(
    val source: String,
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val url: String,
    val thumbnailUrl: String? = null,
)

/** A picture on the source page. Only [savable] images may be copied into the collection. */
data class RecordImage(
    val url: String,
    val thumbnailUrl: String? = null,
    val credit: String? = null,
    val licence: String? = null,
    val pageUrl: String? = null,
    val savable: Boolean = false,
)

/**
 * A spec with no column of its own, kept as a label and value. [source] and [url] override the
 * record's own when the text came from elsewhere (Wikipedia text inside a Wikidata record).
 */
data class ExtraSpec(val label: String, val value: String, val source: String? = null, val url: String? = null)

/** The page as fetched, so a later parser fix can re-read it without another request. */
data class RawPayload(val contentType: String, val body: String)

/**
 * Everything one source said about one watch or movement, already normalised:
 * values are keyed by [SpecFields] key and stored in canonical units.
 */
data class SourceRecord(
    val source: String,
    val key: String,
    val url: String,
    val title: String,
    val fetchedAt: Instant,
    val values: Map<String, SpecValue> = emptyMap(),
    val extras: List<ExtraSpec> = emptyList(),
    val summary: String? = null,
    val summaryCredit: String? = null,
    val images: List<RecordImage> = emptyList(),
    val raw: RawPayload? = null,
) {
    init {
        val unknown = values.keys.filter { SpecFields.byKey(it)?.importable != true }
        require(unknown.isEmpty()) { "Not importable: $unknown" }
    }
}

/** Which source wins a tie, per group: movement databases for movements, watch databases for the rest. */
object SourcePriority {
    private val movement = listOf("ranfft", "grail", "watchbase", "wikidata")
    private val watch = listOf("watchbase", "wikidata", "ranfft", "grail")

    fun order(group: SpecGroup): List<String> = if (group == SpecGroup.MOVEMENT) movement else watch

    fun rank(group: SpecGroup, source: String): Int = order(group).indexOf(source).let { if (it < 0) Int.MAX_VALUE else it }
}
