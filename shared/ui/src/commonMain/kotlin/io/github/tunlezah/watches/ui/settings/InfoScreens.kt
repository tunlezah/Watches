package io.github.tunlezah.watches.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.tunlezah.watches.ui.components.EmptyState
import io.github.tunlezah.watches.ui.components.IconAction
import io.github.tunlezah.watches.ui.theme.WatchIcons
import io.github.tunlezah.watches.ui.theme.WatchTheme

private val fonts = listOf(
    "Cormorant Garamond" to "Christian Thalmann, SIL Open Font License 1.1",
    "Instrument Sans" to "Instrument, SIL Open Font License 1.1",
    "Archivo" to "Omnibus-Type, SIL Open Font License 1.1",
    "IBM Plex Sans and Plex Mono" to "IBM, SIL Open Font License 1.1",
    "Manrope" to "Mikhail Sharanda, SIL Open Font License 1.1",
)

private val libraries = listOf(
    "Kotlin, kotlinx.coroutines, kotlinx.serialization, kotlinx-datetime" to "JetBrains, Apache 2.0",
    "Compose Multiplatform, Navigation 3, Lifecycle, Material 3" to "JetBrains and Google, Apache 2.0",
    "AndroidX (Activity, Core, Room, SQLite, ExifInterface)" to "Google, Apache 2.0",
    "Coil 3" to "Coil Contributors, Apache 2.0",
    "Okio" to "Square, Apache 2.0",
    "Koin" to "Kotzilla, Apache 2.0",
    "FileKit" to "Vincent Guillebaud, MIT",
    "Ktor, Ksoup (search, milestone 2)" to "JetBrains; Fleeksoft, Apache 2.0",
)

@Composable
fun LicencesScreen(onBack: () -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(WatchIcons.Back, "Back", onBack)
            Text(t.title("Licences"), style = t.type.groupTitle, color = c.fg, modifier = Modifier.semantics { heading() })
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = style.screenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(t.title("Fonts"), style = t.type.groupTitle, color = c.fg)
            Text("Bundled in the app; never downloaded.", style = t.type.bodySmall, color = c.fg2)
            for ((name, credit) in fonts) Entry(name, credit)
            Text(t.title("Libraries"), style = t.type.groupTitle, color = c.fg, modifier = Modifier.padding(top = 12.dp))
            for ((name, credit) in libraries) Entry(name, credit)
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun Entry(name: String, credit: String) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    Column(Modifier.fillMaxWidth()) {
        Text(name, style = t.type.body, color = c.fg)
        Text(credit, style = t.type.bodySmall, color = c.fg3)
    }
}

/** Tabs that arrive in later milestones keep their place in the navigation now. */
@Composable
fun ComingSoonScreen(title: String, message: String) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    Column(Modifier.fillMaxSize().background(c.bg).padding(horizontal = style.screenPadding)) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Text(t.title(title), style = t.type.screenTitle, color = c.fg, modifier = Modifier.padding(top = 20.dp).semantics { heading() })
        EmptyState(title = "Coming soon", message = message, modifier = Modifier.fillMaxWidth().padding(top = 40.dp))
    }
}
