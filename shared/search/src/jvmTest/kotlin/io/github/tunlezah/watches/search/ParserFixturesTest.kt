package io.github.tunlezah.watches.search

import io.github.tunlezah.watches.core.model.Complication
import io.github.tunlezah.watches.core.model.MovementType
import io.github.tunlezah.watches.core.spec.SpecFields
import io.github.tunlezah.watches.core.spec.SpecValue
import io.github.tunlezah.watches.search.sources.GrailSource
import io.github.tunlezah.watches.search.sources.RanfftSource
import io.github.tunlezah.watches.search.sources.WikidataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * One test per source and page type, against saved pages in `src/jvmTest/resources/fixtures`.
 * When a site changes its layout, the matching test here fails before anyone sees wrong specs.
 */
class ParserFixturesTest {

    private val at = Instant.fromEpochMilliseconds(1_791_000_000_000)

    private fun fixture(path: String): String =
        requireNotNull(javaClass.getResource("/fixtures/$path")) { "Missing fixture $path" }.readText()

    private fun extras(record: io.github.tunlezah.watches.core.lookup.SourceRecord) = record.extras.associate { it.label to it.value }

    // Grail Watch Reference

    @Test fun grailSearch() {
        val hits = GrailSource.parseSearch(fixture("grail/search-7750.json"))
        assertEquals(4, hits.size)
        val first = hits.first()
        assertEquals("lemania-5012", first.key)
        assertEquals("Lemania 5012", first.title)
        assertEquals("Movement · Automatic", first.subtitle)
        assertEquals("https://reference.grail-watch.com/movement/lemania-5012/", first.url)
        // HTML entities in titles are decoded.
        assertEquals("Dubois-Dépraz 2070", hits[1].title)
    }

    @Test fun grailDetail() {
        val r = GrailSource.parseDetail(fixture("grail/detail-eta-7750.json"), at)
        assertEquals("grail", r.source)
        assertEquals("ETA 7750", r.title)
        assertEquals("https://reference.grail-watch.com/movement/eta-7750/", r.url)
        assertEquals(SpecValue.Text("ETA 7750"), r.values[SpecFields.Caliber.key])
        assertEquals(SpecValue.Choice(MovementType.AUTOMATIC.key), r.values[SpecFields.MovementTypeField.key])
        assertEquals(SpecValue.Whole(25), r.values[SpecFields.Jewels.key])
        assertEquals(SpecValue.Whole(28_800), r.values[SpecFields.Frequency.key])
        assertEquals(SpecValue.Number(54.0), r.values[SpecFields.PowerReserve.key])
        assertEquals(SpecValue.Flag(true), r.values[SpecFields.Hacking.key])
        assertEquals(SpecValue.Keys(setOf(Complication.CHRONOGRAPH.key, Complication.DAY_DATE.key)), r.values[SpecFields.Complications.key])
        // The movement maker is not the watch's brand.
        assertNull(r.values[SpecFields.Brand.key])
        val x = extras(r)
        assertEquals("ETA, Valjoux", x["Movement maker"])
        assertEquals("17, 24, 25", x["Jewel counts"])
        assertEquals("Incabloc Double-Cone", x["Shock protection"])
        assertEquals("1973–current", x["Movement produced"])
        assertTrue(r.summary!!.startsWith("Cam-operated"))
        assertTrue(r.images.isEmpty())
    }

    // Ranfft DB

    @Test fun ranfftSearch() {
        val hits = RanfftSource.parseSearch(fixture("ranfft/search-eta-2824.html"))
        assertEquals(listOf("4186", "4184", "4187"), hits.map { it.key })
        val h = hits.last()
        assertEquals("ETA 2824-2", h.title)
        assertEquals("https://ranfft.org/caliber/4187-ETA-2824-2", h.url)
        assertEquals("ETA · Automatic · 26 mm · 1982", h.subtitle)
        assertTrue(h.thumbnailUrl!!.startsWith("https://ranfft.org/storage/"))
    }

    @Test fun ranfftDetailFullSpec() {
        val url = "https://ranfft.org/caliber/4187-ETA-2824-2"
        val r = RanfftSource.parseDetail(fixture("ranfft/detail-eta-2824-2.html"), url, at)
        assertEquals("ETA 2824-2", r.title)
        assertEquals("4187", r.key)
        assertEquals(SpecValue.Text("ETA 2824-2"), r.values[SpecFields.Caliber.key])
        assertEquals(SpecValue.Text("ETA 2824-1"), r.values[SpecFields.BaseCaliber.key])
        assertEquals(SpecValue.Choice(MovementType.AUTOMATIC.key), r.values[SpecFields.MovementTypeField.key])
        assertEquals(SpecValue.Whole(25), r.values[SpecFields.Jewels.key])
        assertEquals(SpecValue.Number(40.0), r.values[SpecFields.PowerReserve.key])
        assertEquals(SpecValue.Whole(28_800), r.values[SpecFields.Frequency.key])
        assertEquals(SpecValue.Flag(true), r.values[SpecFields.Hacking.key])
        assertEquals(SpecValue.Keys(setOf(Complication.DATE.key)), r.values[SpecFields.Complications.key])
        // Movement size is not case size.
        assertNull(r.values[SpecFields.Diameter.key])
        val x = extras(r)
        assertEquals("ETA", x["Movement maker"])
        assertEquals("Sellita SW200-1", x["Also known as"])
        assertEquals("26 mm (11.5‴)", x["Movement size"])
        assertEquals("4.60 mm", x["Movement height"])
        assertEquals("50 °", x["Lift angle"])
        // Glossary pop-overs are not part of values.
        assertEquals("Incabloc", x["Shock protection"])
        assertEquals("Swiss Lever", x["Escapement"])
        assertEquals("1982", x["Movement launched"])
        assertEquals("Date, Hacking Seconds, Quick Set", x["Movement features"])
        assertEquals("Automatic · 11.5‴ · Switzerland · 1982", r.summary)
        // The carousel literal is the real, full-length one (it once overflowed a regex).
        assertEquals(3, r.images.size)
        assertTrue(r.images.first().url.startsWith("https://ranfft.org/img/9880?fit=down&v="))
        assertTrue(r.images.first().thumbnailUrl!!.startsWith("https://ranfft.org/storage/"))
        assertTrue(r.images.none { it.savable })
    }

    @Test fun ranfftDetailWithGaps() {
        val r = RanfftSource.parseDetail(fixture("ranfft/detail-omega-1861.html"), "https://ranfft.org/caliber/12314-Omega-1861", at)
        assertEquals("Omega 1861", r.title)
        assertEquals(SpecValue.Choice(MovementType.MANUAL.key), r.values[SpecFields.MovementTypeField.key])
        assertEquals(SpecValue.Whole(18), r.values[SpecFields.Jewels.key])
        assertEquals(SpecValue.Whole(21_600), r.values[SpecFields.Frequency.key])
        assertEquals(SpecValue.Number(48.0), r.values[SpecFields.PowerReserve.key])
        assertEquals(SpecValue.Keys(setOf(Complication.SMALL_SECONDS.key, Complication.CHRONOGRAPH.key)), r.values[SpecFields.Complications.key])
        assertNull(r.values[SpecFields.BaseCaliber.key])
        assertNull(r.values[SpecFields.Hacking.key])
        // "—" is an empty value, not a height.
        assertNull(extras(r)["Movement height"])
        assertEquals("27.07 mm (12‴)", extras(r)["Movement size"])
    }

    // Wikidata and Wikipedia

    @Test fun wikidataSearch() {
        val hits = WikidataSource.parseSearch(fixture("wikidata/search-omega-speedmaster.json"))
        assertEquals(3, hits.size)
        assertEquals("Q1414495", hits[0].key)
        assertEquals("Omega Speedmaster", hits[0].title)
        assertEquals("chronograph wristwatches produced by Omega SA", hits[0].subtitle)
        assertEquals("https://www.wikidata.org/wiki/Q1414495", hits[0].url)
    }

    @Test fun wikidataDetail() {
        val entity = fixture("wikidata/entity-Q1414495.json")
        assertEquals(listOf("Q125129697", "Q659224"), WikidataSource.referencedItems(entity, "Q1414495"))
        assertEquals("Omega Speedmaster", WikidataSource.enwikiTitle(entity, "Q1414495"))
        val r = WikidataSource.parseDetail(entity, fixture("wikidata/labels.json"), fixture("wikidata/summary-Omega_Speedmaster.json"), "Q1414495", at)
        assertEquals("Omega Speedmaster", r.title)
        assertEquals(SpecValue.Text("Omega"), r.values[SpecFields.Brand.key])
        assertEquals(SpecValue.Text("Speedmaster"), r.values[SpecFields.Collection.key])
        val about = r.extras.single { it.label == "About" }
        assertEquals("wikipedia", about.source)
        assertEquals("https://en.wikipedia.org/wiki/Omega_Speedmaster", about.url)
        assertTrue(about.value.startsWith("Omega Speedmaster is a line of luxury chronograph wristwatches"))
        assertEquals("https://www.omegawatches.com/watches/speedmaster", extras(r)["Official website"])
        assertEquals("From Wikipedia, CC BY-SA 4.0", r.summaryCredit)
        val image = r.images.single()
        assertTrue(image.savable)
        assertEquals("https://commons.wikimedia.org/wiki/File:Vintage_Omega_Speedmaster_%22Pre-moon%22.jpg", image.pageUrl)
    }

    @Test fun wikidataDetailWithoutExtras() {
        // Labels and the summary are optional requests; the record still works without them.
        val r = WikidataSource.parseDetail(fixture("wikidata/entity-Q1414495.json"), null, null, "Q1414495", at)
        assertNull(r.values[SpecFields.Brand.key])
        assertEquals("chronograph wristwatches produced by Omega SA", r.summary)
        assertTrue(r.images.isEmpty())
    }

    @Test fun commonsImageInfo() {
        val image = WikidataSource.parseImageInfo(fixture("wikidata/commons-imageinfo.json"))!!
        assertTrue(image.imageUrl.startsWith("https://upload.wikimedia.org/wikipedia/commons/"))
        assertEquals("CC BY-SA 2.0", image.licence)
        assertEquals("Shane Lin", image.artist)
        assertEquals("Photo: Shane Lin, CC BY-SA 2.0, via Wikimedia Commons", image.attribution)
    }

    @Test fun previewMustBeTheSavedFile() {
        assertTrue(WikidataSource.sameFile("https://thumb.wikimedia.org/a/b/X.jpg/330px-Vintage_Omega_Speedmaster_%22Pre-moon%22.jpg?utm=1", "Vintage Omega Speedmaster \"Pre-moon\".jpg"))
        assertTrue(!WikidataSource.sameFile("https://upload.wikimedia.org/a/b/Other.jpg", "Vintage Omega Speedmaster.jpg"))
    }

    @Test fun shortBrandNames() {
        assertEquals("Omega", WikidataSource.shortBrand("Omega SA"))
        assertEquals("Seiko", WikidataSource.shortBrand("Seiko Group Corporation"))
        assertEquals("Rolex", WikidataSource.shortBrand("Rolex"))
    }
}
