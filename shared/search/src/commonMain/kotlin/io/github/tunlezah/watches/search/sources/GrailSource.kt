package io.github.tunlezah.watches.search.sources

import io.github.tunlezah.watches.core.lookup.Coverage
import io.github.tunlezah.watches.core.lookup.ExtraSpec
import io.github.tunlezah.watches.core.lookup.RawPayload
import io.github.tunlezah.watches.core.lookup.SearchHit
import io.github.tunlezah.watches.core.lookup.SourceRecord
import io.github.tunlezah.watches.core.lookup.Vocabulary
import io.github.tunlezah.watches.core.spec.SpecFields
import io.github.tunlezah.watches.core.spec.SpecValue
import io.github.tunlezah.watches.core.units.UnitParser
import io.github.tunlezah.watches.search.SourceInfo
import io.github.tunlezah.watches.search.SpecSource
import io.github.tunlezah.watches.search.http.LookupFailure
import io.github.tunlezah.watches.search.http.PoliteHttpClient
import io.github.tunlezah.watches.search.sources.Parsing.array
import io.github.tunlezah.watches.search.sources.Parsing.get
import io.github.tunlezah.watches.search.sources.Parsing.str
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Grail Watch Reference: movements, through its public WordPress REST API. Structured taxonomies,
 * so no page scraping. Its pictures are manufacturer press photos, so none are fetched or saved.
 */
class GrailSource(private val http: PoliteHttpClient, private val clock: Clock = Clock.System) : SpecSource {

    override val info = SourceInfo(
        id = ID,
        name = "Grail Watch Reference",
        homepage = "https://reference.grail-watch.com/",
        coverage = Coverage.MOVEMENT,
        terms = "Public WordPress API. robots.txt allows it. No terms page. Pictures are not used.",
    )

    override suspend fun search(query: String): List<SearchHit> =
        parseSearch(http.get("$API/movement?search=${Parsing.query(query)}&per_page=10&_fields=id,slug,title,link,class_list", accept = JSON).text)

    override suspend fun detail(hit: SearchHit): SourceRecord {
        val slug = hit.key
        if (!slug.matches(Regex("[a-z0-9-]{1,120}"))) throw LookupFailure.NotAllowed(hit.url)
        val fetched = http.get("$API/movement?slug=$slug&_embed=wp:term", accept = JSON)
        return parseDetail(fetched.text, clock.now())
    }

    companion object {
        const val ID = "grail"
        private const val API = "https://reference.grail-watch.com/wp-json/wp/v2"
        private const val JSON = "application/json"
        private val json = Json { ignoreUnknownKeys = true }

        fun parseSearch(body: String): List<SearchHit> = json.parseToJsonElement(body).array.mapNotNull { item ->
            val slug = item["slug"].str ?: return@mapNotNull null
            val link = item["link"].str ?: return@mapNotNull null
            val title = item["title"]["rendered"].str?.let(Parsing::htmlToText) ?: return@mapNotNull null
            val classes = item["class_list"].array.mapNotNull { it.str }
            val winding = classes.firstOrNull { it.startsWith("winding-") }?.removePrefix("winding-")
                ?.replace('-', ' ')?.replaceFirstChar { it.uppercase() }
            SearchHit(ID, slug, title, listOfNotNull("Movement", winding).joinToString(" · "), link)
        }

        fun parseDetail(body: String, fetchedAt: Instant): SourceRecord {
            val item = json.parseToJsonElement(body).array.firstOrNull()
                ?: throw LookupFailure.Unreadable("Grail returned no movement")
            val title = item["title"]["rendered"].str?.let(Parsing::htmlToText)
                ?: throw LookupFailure.Unreadable("Grail movement has no title")
            val link = item["link"].str ?: throw LookupFailure.Unreadable("Grail movement has no link")
            val terms = item["_embedded"]["wp:term"].array.flatMap { it.array }
                .mapNotNull { t -> t["taxonomy"].str?.let { tax -> tax to (t["name"].str?.let(Parsing::unescape) ?: return@mapNotNull null) } }
                .groupBy({ it.first }, { it.second })
            fun names(taxonomy: String) = terms[taxonomy].orEmpty()

            val values = mutableMapOf<String, SpecValue>()
            val extras = mutableListOf<ExtraSpec>()
            values[SpecFields.Caliber.key] = SpecValue.Text(title)

            names("winding").firstNotNullOfOrNull { Vocabulary.movementType(it) }
                ?.let { values[SpecFields.MovementTypeField.key] = SpecValue.Choice(it.key) }

            val jewels = names("jewels").mapNotNull { it.trim().toIntOrNull() }.distinct().sorted()
            if (jewels.isNotEmpty()) values[SpecFields.Jewels.key] = SpecValue.Whole(jewels.last())
            if (jewels.size > 1) extras += ExtraSpec("Jewel counts", jewels.joinToString(", "))

            names("frequency").firstNotNullOfOrNull { UnitParser.frequencyBph(it) }
                ?.let { values[SpecFields.Frequency.key] = SpecValue.Whole(it) }
            names("power-reserve").firstNotNullOfOrNull { UnitParser.firstNumber(it) }
                ?.let { values[SpecFields.PowerReserve.key] = SpecValue.Number(it) }

            val complicationNames = names("complication")
            Vocabulary.complications(complicationNames).takeIf { it.isNotEmpty() }
                ?.let { values[SpecFields.Complications.key] = SpecValue.Keys(it) }
            if (Vocabulary.hacking(complicationNames)) values[SpecFields.Hacking.key] = SpecValue.Flag(true)

            fun extra(label: String, taxonomy: String) {
                names(taxonomy).takeIf { it.isNotEmpty() }?.let { extras += ExtraSpec(label, it.joinToString(", ")) }
            }
            extra("Movement maker", "brand")
            extra("Winding", "winding")
            extra("Movement features", "complication")
            extra("Regulator", "regulator")
            extra("Balance mount", "balance-mount")
            extra("Shock protection", "anti-shock")
            extra("Hand layout", "hands")
            extra("Decoration", "decoration")
            extra("Accuracy", "accuracy")
            val start = names("production-start-year").firstOrNull()
            val end = names("production-end-year").firstOrNull()
            if (start != null) extras += ExtraSpec("Movement produced", listOfNotNull(start, end?.lowercase()).joinToString("–"))

            return SourceRecord(
                source = ID,
                key = item["slug"].str ?: title,
                url = link,
                title = title,
                fetchedAt = fetchedAt,
                values = values,
                extras = extras,
                summary = item["excerpt"]["rendered"].str?.let(Parsing::htmlToText)?.takeIf { it.isNotBlank() },
                summaryCredit = "Grail Watch Reference",
                raw = RawPayload("application/json", body),
            )
        }
    }
}
