package io.github.tunlezah.watches.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.tunlezah.watches.core.format.DateFormat
import io.github.tunlezah.watches.core.format.WaterResistanceUnit
import io.github.tunlezah.watches.core.model.Currencies
import io.github.tunlezah.watches.core.settings.Accent
import io.github.tunlezah.watches.core.settings.AppSettings
import io.github.tunlezah.watches.core.settings.Direction
import io.github.tunlezah.watches.core.settings.ThemeMode
import io.github.tunlezah.watches.ui.components.ChipGroup
import io.github.tunlezah.watches.ui.components.FieldLabel
import io.github.tunlezah.watches.ui.theme.Palettes
import io.github.tunlezah.watches.ui.theme.WatchIcons
import io.github.tunlezah.watches.ui.theme.WatchTheme

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onDiagnostics: () -> Unit,
    onLicences: () -> Unit,
    lookupSection: @Composable () -> Unit = {},
) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    Column(
        Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(horizontal = style.screenPadding),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Text(t.title("Settings"), style = t.type.screenTitle, color = c.fg, modifier = Modifier.padding(top = 20.dp).semantics { heading() })

        Section("Design direction") {
            Text(
                "All three directions ship in this build so you can choose on your phone. The other two are removed once you pick.",
                style = t.type.bodySmall, color = c.fg2,
            )
            for (d in Direction.entries) {
                DirectionCard(d, settings, selected = settings.direction == d) {
                    onUpdate { it.copy(direction = d, accent = if (it.direction != d) Accent.defaultFor(d) else it.accent) }
                }
            }
        }

        Section("Theme") {
            ChipGroup(ThemeMode.entries.map { it.key to it.label }, setOf(settings.themeMode.key), { k ->
                onUpdate { it.copy(themeMode = ThemeMode.entries.first { m -> m.key == k }) }
            })
        }

        Section("Accent") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (a in Accent.entries) {
                    val selected = settings.accent == a
                    Column(
                        Modifier.width(76.dp).clip(style.chipShape)
                            .clickable(role = Role.RadioButton) { onUpdate { it.copy(accent = a) } }
                            .semantics(mergeDescendants = true) { this.selected = selected; contentDescription = "${a.label} accent" }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(Palettes.accent(a, c.isDark))
                                .then(if (selected) Modifier.border(2.dp, c.fg, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Icon(WatchIcons.Check, null, tint = if (c.isDark) Color.Black else Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(a.label, style = t.type.specSource, color = if (selected) c.fg else c.fg3, maxLines = 2,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }

        Section("Units and formats") {
            FieldLabel("Dates")
            ChipGroup(DateFormat.entries.map { it.key to it.label }, setOf(settings.display.dateFormat.key), { k ->
                onUpdate { it.copy(display = it.display.copy(dateFormat = DateFormat.entries.first { f -> f.key == k })) }
            })
            Spacer(Modifier.height(4.dp))
            FieldLabel("Water resistance pressure")
            ChipGroup(WaterResistanceUnit.entries.map { it.key to it.label }, setOf(settings.display.waterResistanceUnit.key), { k ->
                onUpdate { it.copy(display = it.display.copy(waterResistanceUnit = WaterResistanceUnit.entries.first { u -> u.key == k })) }
            })
            Spacer(Modifier.height(4.dp))
            FieldLabel("Default currency")
            ChipGroup(Currencies.common.map { it to it }, setOf(settings.display.defaultCurrency), { k ->
                onUpdate { it.copy(display = it.display.copy(defaultCurrency = k)) }
            })
        }

        Section("Lookup sources") {
            lookupSection()
        }

        Section("Coming in later milestones") {
            Note("Backup and restore", "Export everything to one .zip you save where you like, restore with replace or merge, and CSV export. Milestone 4.")
            Note("App lock", "Optional biometric lock. Milestone 4.")
        }

        Section("Privacy") {
            Note("Online only when you search", "Lookup is the only feature that uses the internet, and only when you press Search, open a result or save a picture. Every request is listed in the request log. There are no accounts, analytics or crash reporting.")
            Note("Not in cloud backups", "Your collection is excluded from Android's automatic cloud backup and device transfer. Export (milestone 4) is the backup.")
        }

        Section("About") {
            NavRow("Diagnostics", "Performance timings, 500 sample watches", onDiagnostics)
            NavRow("Fonts and open-source licences", null, onLicences)
        }
        Spacer(Modifier.height(140.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(t.title(title), style = t.type.groupTitle, color = c.fg, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun Note(title: String, body: String) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(title, style = t.type.button, color = c.fg)
        Text(body, style = t.type.bodySmall, color = c.fg2)
    }
}

@Composable
private fun NavRow(title: String, subtitle: String?, onClick: () -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = t.type.body, color = c.fg)
            if (subtitle != null) Text(subtitle, style = t.type.bodySmall, color = c.fg3)
        }
        Icon(WatchIcons.Forward, null, tint = c.fg3, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DirectionCard(direction: Direction, settings: AppSettings, selected: Boolean, onClick: () -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    val preview = Palettes.colors(direction, Accent.defaultFor(direction), c.isDark)
    Row(
        Modifier.fillMaxWidth().clip(style.cardShape)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.line, style.cardShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { this.selected = selected }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.clip(style.chipShape).border(1.dp, c.line, style.chipShape)) {
            for (col in listOf(preview.bg, preview.surface2, preview.fg, preview.accent)) Box(Modifier.size(width = 14.dp, height = 40.dp).background(col))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(direction.label, style = t.type.button, color = c.fg)
            Text(direction.description, style = t.type.bodySmall, color = c.fg2)
        }
        if (selected) Icon(WatchIcons.Check, "Selected", tint = c.accent, modifier = Modifier.size(22.dp))
    }
}
