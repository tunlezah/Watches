package io.github.tunlezah.watches

import android.app.Application
import androidx.work.Configuration
import io.github.tunlezah.watches.data.androidDataModule
import io.github.tunlezah.watches.data.dataModule
import io.github.tunlezah.watches.platform.androidPlatformModule
import io.github.tunlezah.watches.search.androidSearchModule
import io.github.tunlezah.watches.search.searchModule
import io.github.tunlezah.watches.ui.uiModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class WatchesApplication : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        // Koin wiring is lazy: nothing is built until a screen asks for it, keeping cold start short.
        startKoin {
            androidContext(this@WatchesApplication)
            modules(
                dataModule,
                androidDataModule(this@WatchesApplication),
                searchModule,
                androidSearchModule(this@WatchesApplication),
                androidPlatformModule(this@WatchesApplication),
                uiModule,
            )
        }
    }

    // WorkManager initialises on first use (scheduling a reminder), not at launch.
    override val workManagerConfiguration: Configuration get() = Configuration.Builder().build()
}
