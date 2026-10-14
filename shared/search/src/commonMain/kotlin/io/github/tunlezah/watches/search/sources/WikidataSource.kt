package io.github.tunlezah.watches.search.sources

import io.github.tunlezah.watches.core.lookup.Coverage
import io.github.tunlezah.watches.core.lookup.ExtraSpec
import io.github.tunlezah.watches.core.lookup.RawPayload
import io.github.tunlezah.watches.core.lookup.RecordImage
import io.github.tunlezah.watches.core.lookup.SearchHit
import io.github.tunlezah.watches.core.lookup.SourceRecord
import io.github.tunlezah.watches.core.spec.SpecFields
import io.github.tunlezah.watches.core.spec.SpecValue
import io.github.tunlezah.watches.search.SourceInfo
import io.github.tunlezah.watches.search.SpecSource
import io.github.tunlezah.watches.search.http.LookupFailure
import io.github.tunlezah.watches.search.http.PoliteHttpClient
import io.github.tunlezah.watches.search.http.RobotsPolicy
import io.github.tunlezah.watches.search.sources.Parsing.array
import io.github.tunlezah.watches.search.sources.Parsing.get
import io.github.tunlezah.watches.search.sources.Parsing.obj
import io.github.tunlezah.watches.search.sources.Parsing.str
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.time.Clock
import kotlin.time.Instant

/** A Wikimedia Commons file ready to save, with the credit its licence asks for. */
data class CommonsImage(val imageUrl: String, val pageUrl: String, val licence: String?, val artist: String?) {
    val attribution: String
        get() = listOfNotNull(artist?.let { "Photo: $it" }, licence, "via Wikimedia Commons").joinToString(", ")
}

/**
 * Wikidata for identity (brand, model line, introduction year, official site), plus the English
 * Wikipedia summary for history. Both through Wikimedia's public APIs, whose policy welcomes
 * clients identified by a User-Agent with contact details. Wikidata is CC0; Wikipedia text is
 * CC BY-SA 4.0 and is always shown and saved with its credit and link.
 */
class WikidataSource(private val http: PoliteHttpClient, private val clock: Clock = Clock.System) : SpecSource {

    override val info = SourceInfo(
        id = ID,
        name = "Wikidata & Wikipedia",
        homepage = "https://www.wikidata.org/",
        coverage = Coverage.IDENTITY,
        terms = "Public APIs under Wikimedia's API policy. Wikidata is CC0; Wikipedia text is CC BY-SA 4.0 and keeps its credit.",
    )

    override suspend fun search(query: String): List<SearchHit> =
        parseSearch(api("$WIKIDATA_API?action=wbsearchentities&search=${Parsing.query(query)}&language=en&uselang=en&type=item&limit=10&format=json"))

    override suspend fun detail(hit: SearchHit): SourceRecord {
        if (!hit.key.matches(Regex("Q\\d{1,12}"))) throw LookupFailure.NotAllowed(hit.url)
        val entity = api("$WIKIDATA_API?action=wbgetentities&ids=${hit.key}&props=labels%7Cdescriptions%7Cclaims%7Csitelinks&languages=en&sitefilter=enwiki&format=json")
        val referenced = referencedItems(entity, hit.key)
        // The extra requests add detail; if one fails the record is still useful without it.
        val labels = if (referenced.isEmpty()) null else optional {
            api("$WIKIDATA_API?action=wbgetentities&ids=${referenced.joinToString("%7C")}&props=labels&languages=en&format=json")
        }
        val summary = enwikiTitle(entity, hit.key)?.let { title ->
            optional { api("https://en.wikipedia.org/api/rest_v1/page/summary/${title.replace(' ', '_').encodeURLPathPart()}") }
        }
        return parseDetail(entity, labels, summary, hit.key, clock.now())
    }

    /** Looks up a Commons file's full-size URL and licence. Called only when the owner taps Save photo. */
    suspend fun commonsImage(pageUrl: String): CommonsImage {
        val file = pageUrl.substringAfter("/wiki/File:", "").takeIf { it.isNotEmpty() } ?: throw LookupFailure.NotAllowed(pageUrl)
        val body = api("https://commons.wikimedia.org/w/api.php?action=query&titles=File:$file&prop=imageinfo&iiprop=url%7Cextmetadata&iiurlwidth=2000&format=json&formatversion=2")
        return parseImageInfo(body) ?: throw LookupFailure.Unreadable("Commons has no file $file")
    }

    private suspend fun api(url: String): String =
        http.get(url, accept = "application/json", robots = RobotsPolicy.ApiPolicy).text

    private suspend fun <T> optional(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: LookupFailure) {
        null
    }

    companion object {
        const val ID = "wikidata"
        private const val WIKIDATA_API = "https://www.wikidata.org/w/api.php"
        private val json = Json { ignoreUnknownKeys = true }

        private const val MANUFACTURER = "P176"
        private const val BRAND = "P1716"
        private const val INCEPTION = "P571"
        private const val WEBSITE = "P856"
        private const val IMAGE = "P18"
        private val IMAGE_HOSTS = listOf("https://upload.wikimedia.org/", "https://thumb.wikimedia.org/")

        fun parseSearch(body: String): List<SearchHit> =
            json.parseToJsonElement(body)["search"].array.mapNotNull { item ->
                val id = item["id"].str?.takeIf { it.matches(Regex("Q\\d+")) } ?: return@mapNotNull null
                val label = item["label"].str ?: item["display"]["label"]["value"].str ?: return@mapNotNull null
                SearchHit(ID, id, label, item["description"].str ?: item["display"]["description"]["value"].str, "https://www.wikidata.org/wiki/$id")
            }

        private fun entityOf(body: String, id: String): JsonElement? = json.parseToJsonElement(body)["entities"][id]

        private fun claimItemIds(entity: JsonElement?, property: String): List<String> =
            entity["claims"][property].array.mapNotNull { it["mainsnak"]["datavalue"]["value"]["id"].str }

        private fun claimString(entity: JsonElement?, property: String): String? =
            entity["claims"][property].array.firstNotNullOfOrNull { it["mainsnak"]["datavalue"]["value"].str }

        fun referencedItems(body: String, id: String): List<String> {
            val entity = entityOf(body, id)
            return (claimItemIds(entity, BRAND).take(1) + claimItemIds(entity, MANUFACTURER).take(1)).distinct()
        }

        fun enwikiTitle(body: String, id: String): String? = entityOf(body, id)["sitelinks"]["enwiki"]["title"].str

        fun parseDetail(entityBody: String, labelsBody: String?, summaryBody: String?, id: String, fetchedAt: Instant): SourceRecord {
            val entity = entityOf(entityBody, id) ?: throw LookupFailure.Unreadable("Wikidata returned no $id")
            if (entity["missing"] != null) throw LookupFailure.Unreadable("Wikidata has no $id")
            val label = entity["labels"]["en"]["value"].str ?: id
            val labels = labelsBody?.let { json.parseToJsonElement(it)["entities"].obj }
                ?.mapValues { (_, e) -> e["labels"]["en"]["value"].str }.orEmpty()
            val url = "https://www.wikidata.org/wiki/$id"

            val values = mutableMapOf<String, SpecValue>()
            val extras = mutableListOf<ExtraSpec>()

            // The brand item when it has an English label, else the manufacturer's.
            val brand = listOfNotNull(claimItemIds(entity, BRAND).firstOrNull(), claimItemIds(entity, MANUFACTURER).firstOrNull())
                .firstNotNullOfOrNull { labels[it] }?.let(::shortBrand)
            if (brand != null) {
                // "Omega Speedmaster" with brand "OMEGA" names the collection "Speedmaster", and its
                // spelling of the brand wins over a shouty label.
                val prefixed = label.startsWith("$brand ", ignoreCase = true)
                values[SpecFields.Brand.key] = SpecValue.Text(if (prefixed) label.substring(0, brand.length) else brand)
                if (prefixed) values[SpecFields.Collection.key] = SpecValue.Text(label.substring(brand.length + 1).trim())
            }
            claimItemIds(entity, MANUFACTURER).firstOrNull()?.let { labels[it] }?.let { extras += ExtraSpec("Manufacturer", it) }
            entity["claims"][INCEPTION].array.firstNotNullOfOrNull { it["mainsnak"]["datavalue"]["value"]["time"].str }
                ?.let { Regex("^[+]?(\\d{3,4})-").find(it)?.groupValues?.get(1) }
                ?.let { extras += ExtraSpec("Introduced", it) }
            claimString(entity, WEBSITE)?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                ?.let { extras += ExtraSpec("Official website", it) }

            val summary = summaryBody?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }
            val extract = summary["extract"].str?.trim()?.takeIf { it.isNotEmpty() }
            val wikiUrl = summary["content_urls"]["desktop"]["page"].str
            if (extract != null) extras += ExtraSpec("About", extract, source = "wikipedia", url = wikiUrl)

            val images = buildList {
                val file = claimString(entity, IMAGE)
                val preview = summary["thumbnail"]["source"].str?.takeIf { url -> IMAGE_HOSTS.any { url.startsWith(it) } }
                if (file != null && preview != null) {
                    add(RecordImage(
                        url = preview,
                        credit = "Wikimedia Commons",
                        pageUrl = "https://commons.wikimedia.org/wiki/File:${file.replace(' ', '_').encodeURLPathPart()}",
                        savable = true,
                    ))
                }
            }

            return SourceRecord(
                source = ID,
                key = id,
                url = url,
                title = label,
                fetchedAt = fetchedAt,
                values = values,
                extras = extras,
                summary = extract ?: entity["descriptions"]["en"]["value"].str,
                summaryCredit = if (extract != null) "From Wikipedia, CC BY-SA 4.0" else "Wikidata",
                images = images,
                raw = RawPayload("application/json", entityBody),
            )
        }

        fun parseImageInfo(body: String): CommonsImage? {
            val page = json.parseToJsonElement(body)["query"]["pages"].array.firstOrNull() ?: return null
            val info = page["imageinfo"].array.firstOrNull() ?: return null
            val image = (info["thumburl"].str ?: info["url"].str)?.takeIf { url -> IMAGE_HOSTS.any { url.startsWith(it) } } ?: return null
            val meta = info["extmetadata"]
            return CommonsImage(
                imageUrl = image,
                pageUrl = info["descriptionurl"].str ?: "https://commons.wikimedia.org/",
                licence = meta["LicenseShortName"]["value"].str?.let(Parsing::htmlToText),
                artist = meta["Artist"]["value"].str?.let(Parsing::htmlToText)?.takeIf { it.isNotBlank() },
            )
        }

        private val corporate = Regex("""[\s,]+(S\.?A\.?|AG|GmbH|Ltd\.?|Limited|Inc\.?|Corp\.?|Corporation|Co\.,? Ltd\.?|K\.K\.|Holding|Group|SA de CV)$""", RegexOption.IGNORE_CASE)

        /** "Omega SA" → "Omega", "Seiko Group Corporation" → "Seiko". */
        fun shortBrand(name: String): String {
            var s = name.trim()
            while (true) {
                val next = s.replace(corporate, "").trim()
                if (next == s || next.isEmpty()) return s
                s = next
            }
        }
    }
}
