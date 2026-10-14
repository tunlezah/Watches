package io.github.tunlezah.watches.ui

import androidx.compose.ui.window.ComposeUIViewController
import io.github.tunlezah.watches.data.dataModule
import io.github.tunlezah.watches.data.iosDataModule
import org.koin.core.context.startKoin
import platform.UIKit.UIViewController

private var started = false

/** Called once from the Swift app before the first view controller is created. */
fun initKoin() {
    if (started) return
    started = true
    startKoin { modules(dataModule, iosDataModule(), uiModule) }
}

/** The whole app as a UIKit view controller, hosted by the SwiftUI shell. */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController {
    initKoin()
    return ComposeUIViewController { WatchesApp() }
}
