package io.github.tunlezah.watches.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.tunlezah.watches.core.care.CarePlan
import io.github.tunlezah.watches.core.format.DateFormat
import io.github.tunlezah.watches.core.format.WaterResistanceUnit
import io.github.tunlezah.watches.core.model.Currencies
import io.github.tunlezah.watches.core.settings.Accent
import io.github.tunlezah.watches.core.settings.AppSettings
import io.github.tunlezah.watches.core.settings.Direction
import io.github.tunlezah.watches.core.settings.LastBackup
import io.github.tunlezah.watches.core.settings.LockAfter
import io.github.tunlezah.watches.core.settings.ThemeMode
import io.github.tunlezah.watches.ui.components.ChipGroup
import io.github.tunlezah.watches.ui.components.FieldLabel
import io.github.tunlezah.watches.ui.components.ListRow
import io.github.tunlezah.watches.ui.components.RowDivider
import io.github.tunlezah.watches.ui.components.RowGroup
import io.github.tunlezah.watches.ui.components.ScreenHeader
import io.github.tunlezah.watches.ui.components.SwitchRow
import io.github.tunlezah.watches.ui.components.Tone
import io.github.tunlezah.watches.ui.platform.DeviceAuth
import io.github.tunlezah.watches.ui.theme.Palettes
import io.github.tunlezah.watches.ui.theme.WatchIcons
import io.github.tunlezah.watches.ui.theme.WatchTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

/** The pages under Settings. Keys are part of saved navigation state: don't rename them. */
enum class SettingsPage(val key: String, val title: String) {
    APPEARANCE("appearance", "Appearance"),
    UNITS("units", "Units & formats"),
    SOURCES("sources", "Lookup sources"),
    REMINDERS("reminders", "Service reminders"),
    BACKUP("backup", "Backup & restore"),
    PRIVACY("privacy", "Privacy & security"),
    ABOUT("about", "About Watches"),
    ;

    companion object {
        fun of(key: String): SettingsPage? = entries.firstOrNull { it.key == key }
    }
}

/** The current value each Settings row shows, so most questions are answered without opening a page. */
data class SettingsSummary(
    val lastBackup: LastBackup? = null,
    val sourcesOn: Int = 0,
    val sourcesTotal: Int = 0,
    val notificationsAllowed: Boolean? = null,
    val version: String = "",
)

@Composable
fun SettingsHome(settings: AppSettings, summary: SettingsSummary, today: LocalDate, onOpen: (SettingsPage) -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    Column(
        Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(horizontal = style.screenPadding),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Text(t.title("Settings"), style = t.type.screenTitle, color = c.fg, modifier = Modifier.padding(top = 20.dp).semantics { heading() })

        val last = summary.lastBackup
        val age = last?.at?.toLocalDateTime(TimeZone.currentSystemDefault())?.date?.daysUntil(today)
        RowGroup("Your collection") {
            ListRow(
                "Backup & restore", icon = WatchIcons.Backup,
                detail = when {
                    last == null -> "Not backed up yet"
                    age == 0 -> "Backed up today"
                    else -> "Backed up ${CarePlan.relative(-(age ?: 0))}"
                },
                tone = if (last == null || (age ?: 0) > 30) Tone.Warning else Tone.Normal,
                onClick = { onOpen(SettingsPage.BACKUP) },
            )
        }
        RowGroup("Display") {
            ListRow("Appearance", icon = WatchIcons.Palette, value = "${settings.direction.label} · ${settings.accent.label}", onClick = { onOpen(SettingsPage.APPEARANCE) })
            RowDivider()
            ListRow(
                "Units & formats", icon = WatchIcons.Ruler,
                value = "${settings.display.dateFormat.label} · ${settings.display.defaultCurrency}",
                onClick = { onOpen(SettingsPage.UNITS) },
            )
        }
        RowGroup("Features") {
            ListRow(
                "Lookup sources", icon = WatchIcons.Lookup,
                value = if (summary.sourcesTotal == 0) null else "${summary.sourcesOn} of ${summary.sourcesTotal} on",
                onClick = { onOpen(SettingsPage.SOURCES) },
            )
            RowDivider()
            ListRow(
                "Service reminders", icon = WatchIcons.Bell,
                value = when (summary.notificationsAllowed) {
                    true -> "Allowed"
                    false -> "Notifications off"
                    null -> null
                },
                onClick = { onOpen(SettingsPage.REMINDERS) },
            )
        }
        RowGroup("Privacy & security") {
            ListRow(
                "App lock", icon = WatchIcons.Lock,
                value = if (settings.device.appLock) "On" else "Off",
                onClick = { onOpen(SettingsPage.PRIVACY) },
            )
        }
        RowGroup(null) {
            ListRow("About Watches", icon = WatchIcons.Info, value = summary.version.takeIf { it.isNotBlank() }, onClick = { onOpen(SettingsPage.ABOUT) })
        }
        Spacer(Modifier.height(140.dp))
    }
}

/** The shared frame of a Settings page: header with back, then a scrolling column of groups. */
@Composable
fun SettingsPageScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = WatchTheme.colors
    val style = WatchTheme.style
    Column(Modifier.fillMaxSize().background(c.bg)) {
        ScreenHeader(title, null, onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = style.screenPadding).padding(top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            content = content,
        )
    }
}

/** A group whose body is free-form controls rather than rows. */
@Composable
private fun PanelGroup(title: String, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    RowGroup(title, footer = footer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun AppearancePage(settings: AppSettings, onUpdate: ((AppSettings) -> AppSettings) -> Unit, onBack: () -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    SettingsPageScaffold(SettingsPage.APPEARANCE.title, onBack) {
        RowGroup("Design", footer = "All three designs ship in this build so you can choose on your phone with your own watches. The other two are removed once you pick.") {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (d in Direction.entries) {
                    DirectionCard(d, selected = settings.direction == d) {
                        onUpdate { it.copy(direction = d, accent = if (it.direction != d) Accent.defaultFor(d) else it.accent) }
                    }
                }
            }
        }
        PanelGroup("Theme") {
            ChipGroup(ThemeMode.entries.map { it.key to it.label }, setOf(settings.themeMode.key), { k ->
                onUpdate { it.copy(themeMode = ThemeMode.entries.first { m -> m.key == k }) }
            })
        }
        PanelGroup("Accent") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (a in Accent.entries) {
                    val selected = settings.accent == a
                    Column(
                        Modifier.width(76.dp).clip(style.chipShape)
                            .selectable(selected = selected, role = Role.RadioButton) { onUpdate { it.copy(accent = a) } }
                            .semantics(mergeDescendants = true) { contentDescription = "${a.label} accent" }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(Palettes.accent(a, c.isDark))
                                .then(if (selected) Modifier.border(2.dp, c.fg, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Icon(WatchIcons.Check, null, tint = if (c.isDark) Color.Black else Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(a.label, style = t.type.specSource, color = if (selected) c.fg else c.fg2, maxLines = 2, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
fun UnitsPage(settings: AppSettings, onUpdate: ((AppSettings) -> AppSettings) -> Unit, onBack: () -> Unit) {
    SettingsPageScaffold(SettingsPage.UNITS.title, onBack) {
        PanelGroup("Dates") {
            ChipGroup(DateFormat.entries.map { it.key to it.label }, setOf(settings.display.dateFormat.key), { k ->
                onUpdate { it.copy(display = it.display.copy(dateFormat = DateFormat.entries.first { f -> f.key == k })) }
            })
        }
        PanelGroup("Water resistance", footer = "Depth is always shown in metres, with this pressure unit alongside.") {
            ChipGroup(WaterResistanceUnit.entries.map { it.key to it.label }, setOf(settings.display.waterResistanceUnit.key), { k ->
                onUpdate { it.copy(display = it.display.copy(waterResistanceUnit = WaterResistanceUnit.entries.first { u -> u.key == k })) }
            })
        }
        PanelGroup("Default currency", footer = "Used for new prices and service costs. Each price keeps its own currency, and nothing is converted.") {
            ChipGroup(Currencies.common.map { it to it }, setOf(settings.display.defaultCurrency), { k ->
                onUpdate { it.copy(display = it.display.copy(defaultCurrency = k)) }
            })
        }
    }
}

@Composable
fun SourcesPage(onBack: () -> Unit, content: @Composable () -> Unit) {
    SettingsPageScaffold(SettingsPage.SOURCES.title, onBack) {
        PanelGroup("Sources", footer = "Lookup is the only feature that uses the internet, and only when you press Search, open a result or save a picture.") {
            content()
        }
    }
}

@Composable
fun RemindersPage(onBack: () -> Unit, content: @Composable () -> Unit) {
    SettingsPageScaffold(SettingsPage.REMINDERS.title, onBack) {
        PanelGroup("Notifications") { content() }
    }
}

data class PrivacyState(
    val appLock: Boolean,
    val lockAfter: LockAfter,
    val availability: DeviceAuth.Availability,
    val message: String? = null,
)

@Composable
fun PrivacyPage(state: PrivacyState, onAppLock: (Boolean) -> Unit, onLockAfter: (LockAfter) -> Unit, onBack: () -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    SettingsPageScaffold(SettingsPage.PRIVACY.title, onBack) {
        RowGroup(
            "App lock",
            footer = when (state.availability) {
                DeviceAuth.Availability.READY -> "Asks for your fingerprint, face or phone PIN when you open Watches. It also hides the app's contents in the app switcher."
                DeviceAuth.Availability.NOT_SET_UP -> "Set up a screen lock in your phone's settings first, then come back to turn this on."
                DeviceAuth.Availability.UNSUPPORTED -> "This phone can't lock apps with its screen lock."
            },
        ) {
            SwitchRow(
                "Require unlock", state.appLock, onAppLock, icon = WatchIcons.Lock,
                enabled = state.appLock || state.availability == DeviceAuth.Availability.READY,
            )
            if (state.appLock) {
                RowDivider()
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FieldLabel("Lock again")
                    ChipGroup(LockAfter.entries.map { it.key to it.label }, setOf(state.lockAfter.key), { k ->
                        LockAfter.entries.firstOrNull { it.key == k }?.let(onLockAfter)
                    })
                }
            }
        }
        state.message?.let { Text(it, style = t.type.bodySmall, color = c.danger, modifier = Modifier.padding(horizontal = 4.dp)) }
        RowGroup("Your data") {
            ListRow("Everything stays on this phone", detail = "No accounts, analytics, ads or crash reporting.", icon = WatchIcons.Shield, chevron = false)
            RowDivider()
            ListRow("Online only when you search", detail = "Lookup is the only feature that uses the internet. Every request is in the request log.", icon = WatchIcons.Lookup, chevron = false)
            RowDivider()
            ListRow("Not in cloud backups", detail = "Android's automatic backup and device transfer are off for Watches. Your backups are the files you save.", icon = WatchIcons.Backup, chevron = false)
            RowDivider()
            ListRow("Serial numbers stay hidden", detail = "Shown only when you tap them.", icon = WatchIcons.EyeOff, chevron = false)
        }
    }
}

@Composable
fun AboutPage(version: String, onDiagnostics: () -> Unit, onLicences: () -> Unit, onBack: () -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    SettingsPageScaffold(SettingsPage.ABOUT.title, onBack) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(WatchIcons.Watch, null, tint = c.accent, modifier = Modifier.size(48.dp))
            Text(t.title("Watches"), style = t.type.groupTitle, color = c.fg)
            Text("Version $version", style = t.type.bodySmall, color = c.fg3)
            Text("A private catalogue for one collector.", style = t.type.bodySmall, color = c.fg2)
        }
        RowGroup(null) {
            ListRow("Diagnostics", detail = "Performance timings and 500 sample watches", icon = WatchIcons.Gauge, onClick = onDiagnostics)
            RowDivider()
            ListRow("Fonts and open-source licences", icon = WatchIcons.Info, onClick = onLicences)
        }
    }
}

@Composable
private fun DirectionCard(direction: Direction, selected: Boolean, onClick: () -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    val preview = Palettes.colors(direction, Accent.defaultFor(direction), c.isDark)
    Row(
        Modifier.fillMaxWidth().clip(style.cardShape)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.line, style.cardShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
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
        if (selected) Icon(WatchIcons.Check, null, tint = c.accent, modifier = Modifier.size(22.dp))
    }
}
