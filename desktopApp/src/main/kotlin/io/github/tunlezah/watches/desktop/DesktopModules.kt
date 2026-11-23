package io.github.tunlezah.watches.desktop

import io.github.tunlezah.watches.data.backup.AppInfo
import io.github.tunlezah.watches.data.dataModule
import io.github.tunlezah.watches.data.jvmDataModule
import io.github.tunlezah.watches.search.jvmSearchModule
import io.github.tunlezah.watches.search.searchModule
import io.github.tunlezah.watches.ui.platform.DeviceAuth
import io.github.tunlezah.watches.ui.platform.FileSharer
import io.github.tunlezah.watches.ui.platform.NotificationAccess
import io.github.tunlezah.watches.ui.platform.ReminderScheduler
import io.github.tunlezah.watches.ui.platform.ServiceReminder
import io.github.tunlezah.watches.ui.uiModule
import org.koin.core.module.Module
import org.koin.dsl.module
import java.awt.Desktop
import java.io.File

/** Everything the app is wired from, with the collection in [root]. The same list as the phones', with the desktop's platform modules. */
internal fun desktopModules(root: String): List<Module> = listOf(
    dataModule,
    jvmDataModule(root, AppInfo(BuildInfo.VERSION, AppPaths.platformName())),
    searchModule,
    jvmSearchModule(),
    desktopPlatformModule(),
    uiModule,
)

/*
 * The services a phone takes from its system. Reminders and the app lock need the Mac bridge, which
 * arrives with later milestones (docs/MACOS_PLAN.md, 8.4). Until then they report that they aren't
 * there, and the screens say so (see platformLimits).
 */
internal fun desktopPlatformModule(): Module = module {
    single<ReminderScheduler> { NoReminders }
    single<NotificationAccess> { NoNotifications }
    single<DeviceAuth> { NoDeviceAuth }
    single<FileSharer> { OpenWithDefaultApp }
}

private object NoReminders : ReminderScheduler {
    override suspend fun reschedule(reminders: List<ServiceReminder>) = Unit
}

private object NoNotifications : NotificationAccess {
    override suspend fun granted(): Boolean = false
    override suspend fun request(): Boolean = false
}

private object NoDeviceAuth : DeviceAuth {
    override fun availability() = DeviceAuth.Availability.UNSUPPORTED
    override suspend fun authenticate(title: String, subtitle: String): Boolean = false
}

/** A Mac has no share sheet to hand a file to. Opening it in its usual app (Preview, for a PDF) is where sharing starts there. */
private object OpenWithDefaultApp : FileSharer {
    override fun share(path: String, mimeType: String, title: String) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) Desktop.getDesktop().open(File(path))
        }
    }
}
