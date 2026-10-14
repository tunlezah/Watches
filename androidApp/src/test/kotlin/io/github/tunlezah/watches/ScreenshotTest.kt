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
import io.github.tunlezah.watches.ui.theme.WatchTheme
import kotlinx.datetime.LocalDate
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
        themed(Direction.INSTRUMENT) { SettingsScreen(AppSettings(direction = Direction.INSTRUMENT, accent = Accent.LUME), {}, {}, {}) }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/settings-instrument.png")
    }
}
