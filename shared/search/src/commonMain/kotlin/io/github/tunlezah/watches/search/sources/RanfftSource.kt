package io.github.tunlezah.watches.search.sources

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import io.github.tunlezah.watches.core.lookup.Coverage
import io.github.tunlezah.watches.core.lookup.ExtraSpec
import io.github.tunlezah.watches.core.lookup.RawPayload
import io.github.tunlezah.watches.core.lookup.RecordImage
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
 * Ranfft DB (ranfft.org): calibre data, used with the site owner's permission for single,
 * user-initiated lookups. Pages are server-rendered HTML. Its pictures belong to contributors,
 * so they're shown from the site but never saved.
 */
class RanfftSource(private val http: PoliteHttpClient, private val clock: Clock = Clock.System) : SpecSource {

    override val info = SourceInfo(
        id = ID,
        name = "Ranfft DB",
        homepage = "https://ranfft.org/",
        coverage = Coverage.MOVEMENT,
        terms = "Used with the site owner's permission for single lookups. robots.txt is obeyed. Pictures are shown, never saved.",
    )

    override suspend fun search(query: String): List<SearchHit> =
        parseSearch(http.get("$BASE/calibers?quick=${Parsing.query(query)}").text)

    override suspend fun detail(hit: SearchHit): SourceRecord {
        if (!DETAIL_URL.matches(hit.url)) throw LookupFailure.NotAllowed(hit.url)
        val fetched = http.get(hit.url)
        return parseDetail(fetched.text, hit.url, clock.now())
    }

    companion object {
        const val ID = "ranfft"
        private const val BASE = "https://ranfft.org"
        private val DETAIL_URL = Regex("https://ranfft\\.org/caliber/\\d+-[A-Za-z0-9._%-]+")
        private val json = Json { ignoreUnknownKeys = true }

        fun parseSearch(body: String): List<SearchHit> {
            val doc = Ksoup.parse(body, BASE)
            val rows = doc.select("tr").filter { it.attr("wire:key").startsWith("caliber-") }
            return rows.mapNotNull { row ->
                val id = row.attr("wire:key").removePrefix("caliber-")
                val cells = row.children().filter { it.tagName() == "td" }
                val link = cells.firstOrNull()?.selectFirst("a[href*=/caliber/]") ?: return@mapNotNull null
                val url = link.absUrl("href").takeIf { DETAIL_URL.matches(it) } ?: return@mapNotNull null
                val details = cells.drop(1).mapNotNull { Parsing.present(Parsing.visibleText(it)) }
                SearchHit(
                    source = ID,
                    key = id,
                    title = Parsing.visibleText(link),
                    subtitle = details.joinToString(" · ").ifBlank { null },
                    url = url,
                    thumbnailUrl = cells.first().selectFirst("img")?.absUrl("src")?.takeIf { it.startsWith("$BASE/") },
                )
            }
        }

        fun parseDetail(body: String, url: String, fetchedAt: Instant): SourceRecord {
            val doc = Ksoup.parse(body, BASE)
            val main = doc.selectFirst("main") ?: doc
            val h1 = main.selectFirst("h1") ?: throw LookupFailure.Unreadable("Ranfft page has no title")
            val title = Parsing.visibleText(h1)
            val maker = h1.selectFirst("span.text-muted")?.let(Parsing::visibleText)
            val headerLines = h1.parent()?.children()?.filter { it.tagName() == "p" }?.map(Parsing::visibleText).orEmpty()

            val tiles = tiles(doc)
            val timegrapher = timegrapher(doc)
            val rows = tableRows(doc)

            val values = mutableMapOf<String, SpecValue>()
            val extras = mutableListOf<ExtraSpec>()
            values[SpecFields.Caliber.key] = SpecValue.Text(title)

            Parsing.present(rows["Base caliber"])?.let { values[SpecFields.BaseCaliber.key] = SpecValue.Text(it) }
            Parsing.present(rows["Movement type"])?.let(Vocabulary::movementType)
                ?.let { values[SpecFields.MovementTypeField.key] = SpecValue.Choice(it.key) }
            Parsing.present(tiles["Jewels"])?.let(UnitParser::wholeNumber)
                ?.let { values[SpecFields.Jewels.key] = SpecValue.Whole(it) }
            Parsing.present(tiles["Power reserve"])?.let(UnitParser::powerReserveH)
                ?.let { values[SpecFields.PowerReserve.key] = SpecValue.Number(it) }
            (Parsing.present(timegrapher["Beat rate"]) ?: Parsing.present(timegrapher["Frequency"]))
                ?.let(UnitParser::frequencyBph)
                ?.let { values[SpecFields.Frequency.key] = SpecValue.Whole(it) }

            val complicationItems = complicationItems(doc)
            Vocabulary.complications(complicationItems).takeIf { it.isNotEmpty() }
                ?.let { values[SpecFields.Complications.key] = SpecValue.Keys(it) }
            if (Vocabulary.hacking(complicationItems)) values[SpecFields.Hacking.key] = SpecValue.Flag(true)

            maker?.let { extras += ExtraSpec("Movement maker", it) }
            headerLines.firstOrNull { it.startsWith("Also known as", ignoreCase = true) }
                ?.substringAfter(':')?.let(Parsing::present)?.let { extras += ExtraSpec("Also known as", it) }
            val diameter = Parsing.present(tiles["Dimensions"])
            val lignes = Parsing.present(rows["Lignes"])
            if (diameter != null || lignes != null) {
                extras += ExtraSpec("Movement size", listOfNotNull(diameter, lignes?.let { "($it)" }).joinToString(" "))
            }
            Parsing.present(tiles["Height"])?.let { extras += ExtraSpec("Movement height", it) }
            Parsing.present(timegrapher["Lift angle"])?.let { extras += ExtraSpec("Lift angle", it) }
            for ((label, key) in listOf(
                "Winding" to "Winding type",
                "Escapement" to "Escapement type",
                "Regulator" to "Regulator type",
                "Rotor" to "Rotor type",
                "Shock protection" to "Shock protection",
                "Movement origin" to "Country",
                "Movement launched" to "Launch year",
                "COSC certified" to "COSC certified",
            )) {
                Parsing.present(rows[key])?.let { extras += ExtraSpec(label, it) }
            }
            if (complicationItems.isNotEmpty()) extras += ExtraSpec("Movement features", complicationItems.joinToString(", "))

            return SourceRecord(
                source = ID,
                key = Regex("/caliber/(\\d+)-").find(url)?.groupValues?.get(1) ?: url,
                url = url,
                title = title,
                fetchedAt = fetchedAt,
                values = values,
                extras = extras,
                summary = headerLines.firstOrNull { !it.startsWith("Also known as", ignoreCase = true) },
                summaryCredit = "Ranfft DB",
                images = images(doc),
                raw = RawPayload("text/html", body),
            )
        }

        /** The four stat tiles: Dimensions, Height, Jewels, Power reserve. */
        private fun tiles(doc: Document): Map<String, String> =
            doc.select("div.rounded-lg.bg-raised.px-3").mapNotNull { tile ->
                val kids = tile.children()
                if (kids.size < 2) return@mapNotNull null
                Parsing.visibleText(kids[0]) to figure(kids[1])
            }.toMap()

        /** "4.60<span>mm</span>" reads as "4.60 mm". */
        private fun figure(element: Element): String {
            val unit = element.selectFirst("span")?.let(Parsing::visibleText)
            return listOfNotNull(Parsing.squash(element.ownText()).ifEmpty { null }, unit).joinToString(" ")
        }

        /** Beat rate, Frequency and Lift angle, shown as large figures with a small unit. */
        private fun timegrapher(doc: Document): Map<String, String> =
            doc.select("div.text-xs.uppercase").mapNotNull { label ->
                val value = label.parent()?.children()?.firstOrNull { it !== label && it.hasClass("rd-mono") } ?: return@mapNotNull null
                Parsing.visibleText(label) to figure(value)
            }.toMap()

        /** Every spec table row: `th[scope=row]` label and `td` value. */
        private fun tableRows(doc: Document): Map<String, String> =
            doc.select("tr").mapNotNull { tr ->
                val th = tr.selectFirst("th[scope=row]") ?: return@mapNotNull null
                val td = tr.selectFirst("td") ?: return@mapNotNull null
                Parsing.visibleText(th) to Parsing.visibleText(td)
            }.toMap()

        private fun complicationItems(doc: Document): List<String> {
            val td = doc.select("tr").firstOrNull { tr -> tr.selectFirst("th[scope=row]")?.let(Parsing::visibleText) == "Complications" }
                ?.selectFirst("td") ?: return emptyList()
            val items = td.select("span.inline-flex").map(Parsing::visibleText).filter { it.isNotBlank() }
            return items.ifEmpty { Parsing.visibleText(td).split(',').mapNotNull(Parsing::present) }
        }

        /** The carousel keeps its pictures as JSON inside an Alpine.js `x-data` attribute. */
        private fun images(doc: Document): List<RecordImage> {
            val data = doc.select("[x-data]").map { it.attr("x-data") }.firstOrNull { "images" in it && "JSON.parse(" in it } ?: return emptyList()
            val literal = Regex("JSON\\.parse\\('((?:[^'\\\\]|\\\\.)*)'\\)").find(data)?.groupValues?.get(1) ?: return emptyList()
            val parsed = runCatching { json.parseToJsonElement(Parsing.jsUnescape(literal)) }.getOrNull() ?: return emptyList()
            return parsed.array.mapNotNull { img ->
                val display = img["display"].str?.takeIf { it.startsWith("$BASE/") } ?: return@mapNotNull null
                RecordImage(
                    url = display,
                    thumbnailUrl = img["thumb"].str?.takeIf { it.startsWith("$BASE/") },
                    credit = "Ranfft DB contributors",
                    savable = false,
                )
            }
        }
    }
}
