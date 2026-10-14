package io.github.tunlezah.watches

import android.app.Application
import io.github.tunlezah.watches.data.androidDataModule
import io.github.tunlezah.watches.data.dataModule
import io.github.tunlezah.watches.ui.uiModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class WatchesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Koin wiring is lazy: nothing is built until a screen asks for it, keeping cold start short.
        startKoin {
            androidContext(this@WatchesApplication)
            modules(dataModule, androidDataModule(this@WatchesApplication), uiModule)
        }
    }
}
