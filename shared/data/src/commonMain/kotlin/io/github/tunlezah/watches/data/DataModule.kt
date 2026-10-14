package io.github.tunlezah.watches.data

import io.github.tunlezah.watches.core.TimeSource
import io.github.tunlezah.watches.data.db.AppDatabase
import org.koin.dsl.module

/**
 * Repositories shared by every platform. Each platform adds a module that provides
 * [AppDatabase], [io.github.tunlezah.watches.data.files.FileStore] and
 * [io.github.tunlezah.watches.data.files.ImageProcessor].
 */
val dataModule = module {
    single { TimeSource.System }
    single { WatchRepository(get(), get(), get()) }
    single { PhotoRepository(get(), get(), get(), get()) }
    single { get<AppDatabase>().settingDao() }
    single { SettingsRepository(get()) }
}
