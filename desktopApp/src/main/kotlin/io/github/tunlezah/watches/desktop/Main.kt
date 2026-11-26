package io.github.tunlezah.watches.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.github.tunlezah.watches.ui.WatchesApp
import io.github.tunlezah.watches.ui.backup.BackupCoordinator
import io.github.tunlezah.watches.ui.backup.BackupState
import io.github.tunlezah.watches.ui.backup.BackupTask
import io.github.vinceglb.filekit.FileKit
import org.koin.core.context.startKoin
import java.awt.Color
import java.awt.Desktop
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JOptionPane
import javax.swing.JScrollPane
import javax.swing.JTextArea
import kotlin.system.exitProcess

/**
 * The Mac shell: the whole app is [WatchesApp], the same shared UI the phones run, in one window.
 * `--self-test [folder]` checks a build instead of opening it (see [SelfTest]).
 */
fun main(args: Array<String>) {
    // Before anything touches AWT: the name in the menu bar, and Light or Dark taken from macOS.
    System.setProperty("apple.awt.application.name", "Watches")
    if (System.getProperty("apple.awt.application.appearance") == null) System.setProperty("apple.awt.application.appearance", "system")

    Bridge.locate()

    if (args.firstOrNull() == "--self-test") exitProcess(SelfTest.run(args.getOrNull(1)?.let(::File)))

    showUnexpectedErrors()
    val dir = AppPaths.dataDir()
    val instance = SingleInstance.acquire(dir)
    if (instance == null) {
        tell("Watches is already open.")
        return
    }
    // A file that can't be read at all is left for the database to report when it opens.
    val stored = runCatching { StoredCollection.check(dir) }.getOrDefault(StoredCollection.Usable)
    if (stored is StoredCollection.Newer) {
        tell(stored.message)
        instance.close()
        return
    }

    // FileKit shows the open and save panels. On a desktop it is told the app's name before its first use.
    FileKit.init(appId = "Watches")

    // As on the phones, nothing is built until a screen asks for it.
    val backups = startKoin { modules(desktopModules(dir.path)) }.koin.get<BackupCoordinator>()
    installApplicationMenu(backups)

    application {
        val state = rememberWindowState(size = DpSize(1180.dp, 800.dp), position = WindowPosition(Alignment.Center))
        Window(onCloseRequest = { if (mayQuit(backups)) exitApplication() }, state = state, title = "Watches") {
            // The launch colour behind the first frame, so the window doesn't open white.
            val dark = isSystemInDarkTheme()
            SideEffect {
                window.background = if (dark) Color(0x12, 0x11, 0x10) else Color(0xF5, 0xF1, 0xE8)
                window.minimumSize = minimumWindowSize()
            }
            // Screens keep their view models here for as long as the window lives.
            val owner = remember { WindowViewModels() }
            DisposableEffect(Unit) { onDispose { owner.viewModelStore.clear() } }
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { WatchesApp() }
        }
    }
    instance.close()
}

/**
 * A Mac window is kept from being dragged smaller than the narrowest layout. On the server
 * (docs/SELF_HOSTING.md) the window fills a screen the browser sizes, a phone's included, upright
 * or on its side: a minimum larger than that would push the app's edge off the screen.
 */
internal fun minimumWindowSize(os: String = System.getProperty("os.name").orEmpty()): Dimension =
    if (os.startsWith("Mac")) Dimension(420, 600) else Dimension(300, 300)

private class WindowViewModels : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

/** About and Quit in the application menu, which macOS draws. Quit asks first if a backup or restore is running. */
private fun installApplicationMenu(backups: BackupCoordinator) {
    if (GraphicsEnvironment.isHeadless() || !Desktop.isDesktopSupported()) return
    val desktop = Desktop.getDesktop()
    if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
        desktop.setAboutHandler {
            JOptionPane.showMessageDialog(
                null,
                "Watches ${BuildInfo.VERSION} (${BuildInfo.BUILD})\nA private, offline-first watch collection.\nEverything stays on this Mac.",
                "About Watches",
                JOptionPane.PLAIN_MESSAGE,
            )
        }
    }
    if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
        desktop.setQuitHandler { _, response -> if (mayQuit(backups)) response.performQuit() else response.cancelQuit() }
    }
}

/**
 * True when the app may close now. With a backup, restore or export running it asks first. Quitting
 * then stops the job the usual way, which removes a half-written file; a restore that has started
 * saving finishes regardless, and one stopped earlier is undone at the next launch.
 */
internal fun mayQuit(backups: BackupCoordinator): Boolean {
    val running = backups.state.value as? BackupState.Working ?: return true
    val what = when (running.task) {
        BackupTask.BACKUP -> "A backup is being written."
        BackupTask.CSV -> "A spreadsheet is being written."
        BackupTask.RESTORE -> "A backup is being restored."
        BackupTask.READ -> return true
    }
    val options = arrayOf("Quit", "Keep Going")
    val choice = JOptionPane.showOptionDialog(
        null, "$what Quit anyway?", "Watches", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1],
    )
    if (choice != 0) return false
    backups.cancel()
    // Give the job a moment to tidy up after itself before the process goes.
    val deadline = System.currentTimeMillis() + 10_000
    while (backups.state.value.running && System.currentTimeMillis() < deadline) Thread.sleep(50)
    return true
}

private fun tell(message: String) {
    if (GraphicsEnvironment.isHeadless()) println(message) else JOptionPane.showMessageDialog(null, message, "Watches", JOptionPane.INFORMATION_MESSAGE)
}

/**
 * An error nothing handled is shown, with its detail to copy, rather than the app vanishing. It is
 * written to the terminal too. Nothing is sent anywhere: there is no crash reporting.
 */
private fun showUnexpectedErrors() {
    val showing = AtomicBoolean(false)
    Thread.setDefaultUncaughtExceptionHandler { _, error ->
        error.printStackTrace()
        if (GraphicsEnvironment.isHeadless() || !showing.compareAndSet(false, true)) return@setDefaultUncaughtExceptionHandler
        javax.swing.SwingUtilities.invokeLater {
            try {
                val detail = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                val text = JTextArea("Watches ${BuildInfo.VERSION} (${BuildInfo.BUILD}), ${AppPaths.platformName()}\n\n$detail", 16, 72).apply { isEditable = false }
                JOptionPane.showMessageDialog(
                    null,
                    arrayOf<Any>("Something went wrong. Your collection is safe on this Mac. The detail below can be copied.", JScrollPane(text)),
                    "Watches",
                    JOptionPane.ERROR_MESSAGE,
                )
            } finally {
                showing.set(false)
            }
        }
    }
}
