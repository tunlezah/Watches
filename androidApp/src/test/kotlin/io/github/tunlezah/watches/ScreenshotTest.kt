package io.github.tunlezah.watches

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.tunlezah.watches.core.format.DisplayPrefs
import io.github.tunlezah.watches.core.model.Complication
import io.github.tunlezah.watches.core.model.Money
import io.github.tunlezah.watches.core.model.MovementType
import io.github.tunlezah.watches.core.model.Provenance
import io.github.tunlezah.watches.core.model.Watch
import io.github.tunlezah.watches.core.model.WatchStatus
import io.github.tunlezah.watches.core.settings.Accent
import io.github.tunlezah.watches.core.settings.AppSettings
import io.github.tunlezah.watches.core.settings.Direction
import io.github.tunlezah.watches.core.settings.ThemeMode
import io.github.tunlezah.watches.core.spec.SpecSheet
import io.github.tunlezah.watches.data.sample.SampleCatalog
import io.github.tunlezah.watches.ui.collection.CollectionItem
import io.github.tunlezah.watches.ui.collection.CollectionScreen
import io.github.tunlezah.watches.ui.collection.CollectionUiState
import io.github.tunlezah.watches.ui.components.WatchLook
import io.github.tunlezah.watches.ui.detail.DetailActions
import io.github.tunlezah.watches.ui.detail.DetailScreen
import io.github.tunlezah.watches.ui.detail.DetailStat
import io.github.tunlezah.watches.ui.detail.DetailUiState
import io.github.tunlezah.watches.ui.settings.SettingsScreen
import io.github.tunlezah.watches.core.lookup.Coverage
import io.github.tunlezah.watches.core.lookup.ExtraSpec
import io.github.tunlezah.watches.core.lookup.LookupDiff
import io.github.tunlezah.watches.core.lookup.RecordImage
import io.github.tunlezah.watches.core.lookup.SearchHit
import io.github.tunlezah.watches.core.lookup.SourceRecord
import io.github.tunlezah.watches.core.model.OtherSpec
import io.github.tunlezah.watches.core.spec.SpecValue
import io.github.tunlezah.watches.search.SourceInfo
import io.github.tunlezah.watches.search.SourceState
import io.github.tunlezah.watches.search.SourceStatus
import io.github.tunlezah.watches.search.http.LoggedRequest
import io.github.tunlezah.watches.search.http.LookupFailure
import io.github.tunlezah.watches.ui.lookup.ApplyScreen
import io.github.tunlezah.watches.ui.lookup.ApplyUiState
import io.github.tunlezah.watches.ui.lookup.ExtrasChoice
import io.github.tunlezah.watches.ui.lookup.ImageSource
import io.github.tunlezah.watches.ui.lookup.LookupActions
import io.github.tunlezah.watches.ui.lookup.LookupScreen
import io.github.tunlezah.watches.ui.lookup.LookupSettingsSection
import io.github.tunlezah.watches.ui.lookup.LookupTarget
import io.github.tunlezah.watches.ui.lookup.LookupUiState
import io.github.tunlezah.watches.ui.lookup.RequestLogScreen
import io.github.tunlezah.watches.ui.lookup.ResultScreen
import io.github.tunlezah.watches.ui.lookup.ResultUiState
import io.github.tunlezah.watches.ui.lookup.SourceUi
import io.github.tunlezah.watches.ui.lookup.SourcesUiState
import io.github.tunlezah.watches.ui.theme.WatchTheme
import kotlinx.datetime.LocalDate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Instant

/**
 * Renders real screens with fixed data in every design direction. Run with
 * `./gradlew :androidApp:recordRoborazziDebug`; images land in androidApp/build/outputs/roborazzi.
 */
/** Screens under test take their data directly, so no dependency injection is started. */
class ScreenshotApp : android.app.Application()

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi", application = ScreenshotApp::class)
class ScreenshotTest {

    @get:Rule val compose = createComposeRule()

    private val t0 = Instant.fromEpochMilliseconds(1_760_000_000_000)

    private val items: List<CollectionItem> = SampleCatalog.generate(8, seed = 7, now = t0).mapIndexed { i, s ->
        val w = s.watch
        CollectionItem(
            id = w.id, brand = w.brand, title = w.modelName ?: "", reference = w.reference,
            meta = listOfNotNull(w.diameterMm?.let { "${it.toString().removeSuffix(".0")} mm" }, w.movementType?.label?.replace(" winding", "")).joinToString(" · "),
            status = if (i == 3) WatchStatus.WISHLIST else WatchStatus.OWNED, archived = false, thumb = null, look = WatchLook.from(s.look),
        )
    }

    private val speedmaster = Watch(
        id = "w1", createdAt = t0, updatedAt = t0, brand = "Omega", collection = "Speedmaster", modelName = "Speedmaster Professional",
        nickname = "Moonwatch", reference = "310.30.42.50.01.001", yearFrom = 2021,
        caseMaterial = "Stainless steel", diameterMm = 42.0, thicknessMm = 13.2, lugToLugMm = 47.5, lugWidthMm = 20.0,
        bezel = "Aluminium tachymeter", crystal = "Hesalite", waterResistanceM = 50,
        dialColour = "Black", dialFinish = "Matte, stepped", indices = "Printed, lumed", lume = "Super-LumiNova",
        caliber = "3861", baseCaliber = "1861", movementType = MovementType.MANUAL, jewels = 26, frequencyBph = 21_600, powerReserveH = 50.0,
        complications = setOf(Complication.CHRONOGRAPH.key, Complication.SMALL_SECONDS.key),
        strapType = "Bracelet", strapMaterial = "Stainless steel", clasp = "Folding, push-button",
        acquiredOn = LocalDate(2023, 3, 14), acquiredFrom = "Authorised dealer", pricePaid = Money(1_095_000, "AUD"),
        serialNumber = "87654321", serviceIntervalMonths = 60, warrantyStart = LocalDate(2023, 3, 14), warrantyMonths = 60,
    )

    private val provenance = listOf("case.diameter_mm", "case.thickness_mm", "case.bezel", "case.crystal", "case.water_resistance_m", "dial.lume")
        .associateWith { Provenance(it, "watchbase", "https://watchbase.com", t0) } +
        listOf("movement.caliber", "movement.base_caliber", "movement.type", "movement.jewels", "movement.frequency_bph", "movement.power_reserve_h")
            .associateWith { Provenance(it, "ranfft", "https://ranfft.org", t0) } +
        mapOf("case.lug_to_lug_mm" to Provenance("case.lug_to_lug_mm", "watchbase", null, t0, userEdited = true))

    private fun detailState() = DetailUiState(
        loading = false, watch = speedmaster, brand = "Omega", title = "Speedmaster Professional", subtitle = "“Moonwatch”",
        reference = speedmaster.reference, statusLabel = "Owned", look = WatchLook.guess("Black", "Steel", speedmaster.complications),
        stats = listOf(DetailStat("Status", "Owned", true), DetailStat("Case", "42 mm"), DetailStat("Movement", "Manual"), DetailStat("Next service", "03/2028")),
        groups = SpecSheet.build(speedmaster, provenance, DisplayPrefs()), footer = "Added 14/03/2023",
    )

    private fun themed(direction: Direction, dark: Boolean = true, content: @Composable () -> Unit) {
        compose.setContent {
            WatchTheme(AppSettings(direction = direction, accent = Accent.defaultFor(direction), themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT), content)
        }
    }

    private fun collection(direction: Direction, dark: Boolean = true) {
        themed(direction, dark) {
            CollectionScreen(
                CollectionUiState(loaded = true, items = items, totalActive = items.size, ownedCount = items.size - 1),
                {}, {}, {}, {}, {}, {}, {},
            )
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/collection-${direction.key}-${if (dark) "dark" else "light"}.png")
    }

    private fun detail(direction: Direction, dark: Boolean = true) {
        themed(direction, dark) {
            DetailScreen(detailState(), "w1", DetailActions({}, {}, {}, {}, {}, {}, {}))
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/detail-${direction.key}-${if (dark) "dark" else "light"}.png")
    }

    @Test fun collectionAtelier() = collection(Direction.ATELIER)
    @Test fun collectionInstrument() = collection(Direction.INSTRUMENT)
    @Test fun collectionGallery() = collection(Direction.GALLERY)
    @Test fun collectionAtelierLight() = collection(Direction.ATELIER, dark = false)
    @Test fun detailAtelier() = detail(Direction.ATELIER)
    @Test fun detailInstrument() = detail(Direction.INSTRUMENT)
    @Test fun detailGallery() = detail(Direction.GALLERY)
    @Test fun detailGalleryLight() = detail(Direction.GALLERY, dark = false)

    @Test fun emptyCollection() {
        themed(Direction.ATELIER) { CollectionScreen(CollectionUiState(loaded = true), {}, {}, {}, {}, {}, {}, {}) }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/collection-empty.png")
    }

    @Test fun settings() {
        themed(Direction.INSTRUMENT) {
            SettingsScreen(AppSettings(direction = Direction.INSTRUMENT, accent = Accent.LUME), {}, {}, {}) {
                LookupSettingsSection(
                    SourcesUiState(
                        sources = sourceInfos.map { SourceUi(it, enabled = it.id != "grail", state = null) },
                        watchBase = SourceInfo("watchbase", "WatchBase", "https://watchbase.com/", Coverage.WATCH,
                            "Personal, non-commercial use. Not searchable yet: its pages couldn't be checked from the build server.", SourceStatus.UNVERIFIED),
                    ),
                    {}, {}, {}, {},
                )
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/settings-instrument.png")
    }

    @Test fun settingsSources() {
        themed(Direction.GALLERY) {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.fillMaxSize().background(io.github.tunlezah.watches.ui.theme.WatchTheme.colors.bg).padding(20.dp),
            ) {
                LookupSettingsSection(
                    SourcesUiState(
                        sources = sourceInfos.map { SourceUi(it, enabled = it.id != "grail", state = null) },
                        watchBase = SourceInfo("watchbase", "WatchBase", "https://watchbase.com/", Coverage.WATCH,
                            "Personal, non-commercial use. Not searchable yet: its pages couldn't be checked from the build server.", SourceStatus.UNVERIFIED),
                        watchBaseResult = "WatchBase blocked the request. The app won't disguise itself to get past it.",
                    ),
                    {}, {}, {}, {},
                )
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/settings-sources-gallery.png")
    }

    // Lookup

    private val sourceInfos = listOf(
        SourceInfo("wikidata", "Wikidata & Wikipedia", "https://www.wikidata.org/", Coverage.IDENTITY, "Public APIs under Wikimedia's API policy."),
        SourceInfo("ranfft", "Ranfft DB", "https://ranfft.org/", Coverage.MOVEMENT, "Used with the site owner's permission for single lookups."),
        SourceInfo("grail", "Grail Watch Reference", "https://reference.grail-watch.com/", Coverage.MOVEMENT, "Public WordPress API."),
    )

    private val noImages = ImageSource { _, _ -> null }

    private val ranfftRecord = SourceRecord(
        source = "ranfft", key = "4187", url = "https://ranfft.org/caliber/4187-ETA-2824-2", title = "ETA 2824-2", fetchedAt = t0,
        values = mapOf(
            "movement.caliber" to SpecValue.Text("ETA 2824-2"),
            "movement.base_caliber" to SpecValue.Text("ETA 2824-1"),
            "movement.type" to SpecValue.Choice(MovementType.AUTOMATIC.key),
            "movement.jewels" to SpecValue.Whole(25),
            "movement.frequency_bph" to SpecValue.Whole(28_800),
            "movement.power_reserve_h" to SpecValue.Number(40.0),
            "movement.hacking" to SpecValue.Flag(true),
            "movement.complications" to SpecValue.Keys(setOf(Complication.DATE.key)),
        ),
        extras = listOf(
            ExtraSpec("Movement maker", "ETA"), ExtraSpec("Also known as", "Sellita SW200-1"), ExtraSpec("Movement size", "26 mm (11.5‴)"),
            ExtraSpec("Movement height", "4.60 mm"), ExtraSpec("Lift angle", "50 °"), ExtraSpec("Shock protection", "Incabloc"),
        ),
        summary = "Automatic · 11.5‴ · Switzerland · 1982", summaryCredit = "Ranfft DB",
        images = listOf(RecordImage("https://ranfft.org/img/1", credit = "Ranfft DB contributors")),
    )

    private val grailRecord = SourceRecord(
        source = "grail", key = "eta-2824-2", url = "https://reference.grail-watch.com/movement/eta-2824-2/", title = "ETA 2824-2", fetchedAt = t0,
        values = mapOf("movement.power_reserve_h" to SpecValue.Number(38.0), "movement.frequency_bph" to SpecValue.Whole(28_800), "movement.jewels" to SpecValue.Whole(25)),
    )

    private fun hit(source: String, key: String, title: String, subtitle: String, thumb: Boolean = false) =
        SearchHit(source, key, title, subtitle, "https://example.org/$key", if (thumb) "https://ranfft.org/storage/$key.jpg" else null)

    private fun lookupState(searched: Boolean) = LookupUiState(
        target = if (searched) LookupTarget.Watch("w2") else LookupTarget.None,
        targetTitle = if (searched) "Tudor Black Bay 58" else null,
        query = "ETA 2824-2",
        searched = if (searched) "ETA 2824-2" else null,
        sources = listOf(
            SourceUi(sourceInfos[0], true, SourceState.Results(listOf(hit("wikidata", "Q1", "ETA 2824", "mechanical watch movement")))),
            SourceUi(sourceInfos[1], true, SourceState.Results(listOf(
                hit("ranfft", "4186", "ETA 2824", "ETA · Automatic · 26 mm · 1971", true),
                hit("ranfft", "4184", "ETA 2824-1", "ETA · Automatic · 26 mm · 1979", true),
                hit("ranfft", "4187", "ETA 2824-2", "ETA · Automatic · 26 mm · 1982", true),
            ))),
            SourceUi(sourceInfos[2], true, SourceState.Failed(LookupFailure.TimedOut("reference.grail-watch.com"))),
        ),
        recent = listOf("Omega Speedmaster", "116610LN", "Seiko 6R35", "Valjoux 7750"),
        tray = if (searched) listOf(ranfftRecord) else emptyList(),
    )

    private val lookupActions = LookupActions(null, {}, {}, {}, {}, {}, {}, {}, {}, {})

    @Test fun lookupHome() {
        themed(Direction.ATELIER) { LookupScreen(lookupState(searched = false), noImages, emptyList(), lookupActions) }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/lookup-home-atelier.png")
    }

    @Test fun lookupResults() {
        themed(Direction.INSTRUMENT) { LookupScreen(lookupState(searched = true), noImages, emptyList(), lookupActions) }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/lookup-results-instrument.png")
    }

    @Test fun lookupResult() {
        themed(Direction.GALLERY) {
            ResultScreen(
                ResultUiState(hit = hit("ranfft", "4187", "ETA 2824-2", "ETA · Automatic · 26 mm · 1982"), sourceName = "Ranfft DB", record = ranfftRecord, loading = false),
                noImages, DisplayPrefs(), {}, {}, {}, {},
            )
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/lookup-result-gallery.png")
    }

    @Test fun lookupApply() {
        val blackBay = Watch(
            id = "w2", createdAt = t0, updatedAt = t0, brand = "Tudor", modelName = "Black Bay 58", caliber = "ETA 2824",
            movementType = MovementType.AUTOMATIC, jewels = 21, powerReserveH = 38.0,
        )
        val prov = mapOf(
            "movement.caliber" to Provenance("movement.caliber", "manual", userEdited = true),
            "movement.jewels" to Provenance("movement.jewels", "manual", userEdited = true),
            "movement.power_reserve_h" to Provenance("movement.power_reserve_h", "grail", "u", t0),
            "movement.type" to Provenance("movement.type", "manual", userEdited = true),
        )
        val rows = LookupDiff.rows(blackBay, prov, listOf(grailRecord, ranfftRecord))
        val state = ApplyUiState(
            loaded = true, target = LookupTarget.Watch("w2"), targetTitle = "Tudor Black Bay 58", rows = rows,
            choices = rows.mapNotNull { r -> r.defaultChoice?.let { r.field.key to it } }.toMap(),
            extras = listOf(ExtrasChoice("ranfft", "ETA 2824-2", ranfftRecord.extras.mapIndexed { i, e -> OtherSpec("o$i", e.label, e.value, "ranfft") })),
            records = listOf(grailRecord, ranfftRecord),
        )
        themed(Direction.ATELIER) { ApplyScreen(state, DisplayPrefs(), {}, { _, _ -> }, {}, {}) }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/lookup-apply-atelier.png")
    }

    @Test fun requestLog() {
        val entries = listOf(
            LoggedRequest(t0, "https://ranfft.org/caliber/4187-ETA-2824-2", "ranfft.org", 200, "OK", 412, 83_709),
            LoggedRequest(t0, "https://ranfft.org/calibers?quick=ETA%202824-2", "ranfft.org", 200, "OK", 655, 298_495),
            LoggedRequest(t0, "https://www.wikidata.org/w/api.php?action=wbsearchentities&search=ETA%202824-2", "www.wikidata.org", 200, "OK (API)", 230, 1_897),
            LoggedRequest(t0, "https://ranfft.org/robots.txt", "ranfft.org", 200, "OK", 180, 62),
            LoggedRequest(t0, "https://ranfft.org/calibers?sort=name", "ranfft.org", null, "Refused: robots.txt", 0, null, sent = false),
            LoggedRequest(t0, "https://reference.grail-watch.com/wp-json/wp/v2/movement?search=ETA%202824-2", "reference.grail-watch.com", null, "Timed out", 10_000, null),
        )
        themed(Direction.ATELIER, dark = false) { RequestLogScreen(entries, {}, {}) }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/request-log-atelier-light.png")
    }
}
