package io.github.tunlezah.watches.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.tunlezah.watches.core.settings.Direction
import io.github.tunlezah.watches.ui.resources.Res
import io.github.tunlezah.watches.ui.resources.archivo_expanded_bold
import io.github.tunlezah.watches.ui.resources.archivo_expanded_extrabold
import io.github.tunlezah.watches.ui.resources.archivo_expanded_semibold
import io.github.tunlezah.watches.ui.resources.cormorant_medium
import io.github.tunlezah.watches.ui.resources.cormorant_medium_italic
import io.github.tunlezah.watches.ui.resources.cormorant_semibold
import io.github.tunlezah.watches.ui.resources.instrument_sans_medium
import io.github.tunlezah.watches.ui.resources.instrument_sans_regular
import io.github.tunlezah.watches.ui.resources.instrument_sans_semibold
import io.github.tunlezah.watches.ui.resources.manrope_light
import io.github.tunlezah.watches.ui.resources.manrope_medium
import io.github.tunlezah.watches.ui.resources.manrope_regular
import io.github.tunlezah.watches.ui.resources.manrope_semibold
import io.github.tunlezah.watches.ui.resources.plex_mono_medium
import io.github.tunlezah.watches.ui.resources.plex_mono_regular
import io.github.tunlezah.watches.ui.resources.plex_sans_medium
import io.github.tunlezah.watches.ui.resources.plex_sans_regular
import io.github.tunlezah.watches.ui.resources.plex_sans_semibold
import org.jetbrains.compose.resources.Font

/**
 * Type roles. Every role that shows a measurement uses tabular figures so columns of numbers align.
 * Units are set in [specUnit], a lighter weight than [specValue].
 */
@Immutable
data class WatchTypography(
    val screenTitle: TextStyle,
    val heroTitle: TextStyle,
    val heroSubtitle: TextStyle,
    val tileTitle: TextStyle,
    val tileBrand: TextStyle,
    val tileMeta: TextStyle,
    val groupTitle: TextStyle,
    val specLabel: TextStyle,
    val specValue: TextStyle,
    val specUnit: TextStyle,
    val specSource: TextStyle,
    val reference: TextStyle,
    val label: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    val button: TextStyle,
    val nav: TextStyle,
    val stat: TextStyle,
)

private const val TNUM = "tnum"

@Composable
private fun cormorant() = FontFamily(
    Font(Res.font.cormorant_medium, FontWeight.Medium),
    Font(Res.font.cormorant_semibold, FontWeight.SemiBold),
    Font(Res.font.cormorant_medium_italic, FontWeight.Medium, FontStyle.Italic),
)

@Composable
private fun instrumentSans() = FontFamily(
    Font(Res.font.instrument_sans_regular, FontWeight.Normal),
    Font(Res.font.instrument_sans_medium, FontWeight.Medium),
    Font(Res.font.instrument_sans_semibold, FontWeight.SemiBold),
)

@Composable
private fun archivoExpanded() = FontFamily(
    Font(Res.font.archivo_expanded_semibold, FontWeight.SemiBold),
    Font(Res.font.archivo_expanded_bold, FontWeight.Bold),
    Font(Res.font.archivo_expanded_extrabold, FontWeight.ExtraBold),
)

@Composable
private fun plexSans() = FontFamily(
    Font(Res.font.plex_sans_regular, FontWeight.Normal),
    Font(Res.font.plex_sans_medium, FontWeight.Medium),
    Font(Res.font.plex_sans_semibold, FontWeight.SemiBold),
)

@Composable
private fun plexMono() = FontFamily(
    Font(Res.font.plex_mono_regular, FontWeight.Normal),
    Font(Res.font.plex_mono_medium, FontWeight.Medium),
)

@Composable
private fun manrope() = FontFamily(
    Font(Res.font.manrope_light, FontWeight.Light),
    Font(Res.font.manrope_regular, FontWeight.Normal),
    Font(Res.font.manrope_medium, FontWeight.Medium),
    Font(Res.font.manrope_semibold, FontWeight.SemiBold),
)

@Composable
fun typographyFor(direction: Direction): WatchTypography = when (direction) {
    Direction.ATELIER -> {
        val display = cormorant()
        val sans = instrumentSans()
        WatchTypography(
            screenTitle = TextStyle(fontFamily = display, fontWeight = FontWeight.Medium, fontSize = 38.sp, lineHeight = 40.sp),
            heroTitle = TextStyle(fontFamily = display, fontWeight = FontWeight.Medium, fontSize = 38.sp, lineHeight = 40.sp),
            heroSubtitle = TextStyle(fontFamily = display, fontWeight = FontWeight.Medium, fontStyle = FontStyle.Italic, fontSize = 19.sp, lineHeight = 24.sp),
            tileTitle = TextStyle(fontFamily = display, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 22.sp),
            tileBrand = TextStyle(fontFamily = sans, fontSize = 10.5.sp, letterSpacing = 0.16.em, lineHeight = 14.sp),
            tileMeta = TextStyle(fontFamily = sans, fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = TNUM),
            groupTitle = TextStyle(fontFamily = display, fontWeight = FontWeight.Medium, fontSize = 25.sp, lineHeight = 28.sp),
            specLabel = TextStyle(fontFamily = sans, fontSize = 13.5.sp, lineHeight = 18.sp),
            specValue = TextStyle(fontFamily = sans, fontSize = 15.sp, lineHeight = 20.sp, fontFeatureSettings = TNUM),
            specUnit = TextStyle(fontFamily = sans, fontSize = 12.5.sp, lineHeight = 18.sp, fontFeatureSettings = TNUM),
            specSource = TextStyle(fontFamily = sans, fontSize = 11.sp, lineHeight = 14.sp),
            reference = TextStyle(fontFamily = sans, fontSize = 14.sp, lineHeight = 18.sp, fontFeatureSettings = TNUM),
            label = TextStyle(fontFamily = sans, fontSize = 11.sp, letterSpacing = 0.16.em, lineHeight = 14.sp),
            body = TextStyle(fontFamily = sans, fontSize = 15.sp, lineHeight = 22.sp),
            bodySmall = TextStyle(fontFamily = sans, fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = TNUM),
            button = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
            nav = TextStyle(fontFamily = sans, fontSize = 11.sp, letterSpacing = 0.04.em, lineHeight = 14.sp),
            stat = TextStyle(fontFamily = sans, fontSize = 15.sp, lineHeight = 20.sp, fontFeatureSettings = TNUM),
        )
    }
    Direction.INSTRUMENT -> {
        val display = archivoExpanded()
        val sans = plexSans()
        val mono = plexMono()
        WatchTypography(
            screenTitle = TextStyle(fontFamily = display, fontWeight = FontWeight.ExtraBold, fontSize = 25.sp, lineHeight = 28.sp),
            heroTitle = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 28.sp),
            heroSubtitle = TextStyle(fontFamily = mono, fontSize = 12.sp, lineHeight = 16.sp),
            tileTitle = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 14.5.sp, lineHeight = 18.sp),
            tileBrand = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, letterSpacing = 0.06.em, lineHeight = 14.sp),
            tileMeta = TextStyle(fontFamily = mono, fontSize = 11.sp, lineHeight = 15.sp),
            groupTitle = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 0.08.em, lineHeight = 16.sp),
            specLabel = TextStyle(fontFamily = sans, fontSize = 13.sp, lineHeight = 18.sp),
            specValue = TextStyle(fontFamily = sans, fontSize = 14.sp, lineHeight = 19.sp),
            specUnit = TextStyle(fontFamily = mono, fontSize = 11.sp, lineHeight = 16.sp),
            specSource = TextStyle(fontFamily = mono, fontSize = 9.5.sp, lineHeight = 12.sp),
            reference = TextStyle(fontFamily = mono, fontSize = 12.5.sp, lineHeight = 16.sp),
            label = TextStyle(fontFamily = mono, fontSize = 10.5.sp, lineHeight = 14.sp),
            body = TextStyle(fontFamily = sans, fontSize = 15.sp, lineHeight = 22.sp),
            bodySmall = TextStyle(fontFamily = sans, fontSize = 13.sp, lineHeight = 18.sp),
            button = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
            nav = TextStyle(fontFamily = mono, fontSize = 10.sp, lineHeight = 13.sp),
            stat = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 24.sp, fontFeatureSettings = TNUM),
        )
    }
    Direction.GALLERY -> {
        val m = manrope()
        WatchTypography(
            screenTitle = TextStyle(fontFamily = m, fontWeight = FontWeight.Light, fontSize = 36.sp, letterSpacing = (-0.03).em, lineHeight = 40.sp),
            heroTitle = TextStyle(fontFamily = m, fontWeight = FontWeight.Light, fontSize = 32.sp, letterSpacing = (-0.025).em, lineHeight = 36.sp),
            heroSubtitle = TextStyle(fontFamily = m, fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = TNUM),
            tileTitle = TextStyle(fontFamily = m, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 19.sp),
            tileBrand = TextStyle(fontFamily = m, fontSize = 12.5.sp, lineHeight = 17.sp, fontFeatureSettings = TNUM),
            tileMeta = TextStyle(fontFamily = m, fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = TNUM),
            groupTitle = TextStyle(fontFamily = m, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
            specLabel = TextStyle(fontFamily = m, fontSize = 14.sp, lineHeight = 19.sp),
            specValue = TextStyle(fontFamily = m, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp, fontFeatureSettings = TNUM),
            specUnit = TextStyle(fontFamily = m, fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = TNUM),
            specSource = TextStyle(fontFamily = m, fontSize = 11.5.sp, lineHeight = 15.sp),
            reference = TextStyle(fontFamily = m, fontSize = 14.sp, lineHeight = 19.sp, fontFeatureSettings = TNUM),
            label = TextStyle(fontFamily = m, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
            body = TextStyle(fontFamily = m, fontSize = 15.sp, lineHeight = 22.sp),
            bodySmall = TextStyle(fontFamily = m, fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = TNUM),
            button = TextStyle(fontFamily = m, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
            nav = TextStyle(fontFamily = m, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp),
            stat = TextStyle(fontFamily = m, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = (-0.01).em, lineHeight = 26.sp, fontFeatureSettings = TNUM),
        )
    }
}
