package io.github.tunlezah.watches.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade
import io.github.tunlezah.watches.data.SettingsRepository
import io.github.tunlezah.watches.data.WatchRepository
import io.github.tunlezah.watches.ui.collection.CollectionViewModel
import io.github.tunlezah.watches.ui.detail.DetailViewModel
import io.github.tunlezah.watches.ui.diagnostics.SampleGenerator
import io.github.tunlezah.watches.ui.edit.EditViewModel
import io.github.tunlezah.watches.ui.nav.AppShell
import io.github.tunlezah.watches.ui.photos.CropViewModel
import io.github.tunlezah.watches.ui.photos.PhotosViewModel
import io.github.tunlezah.watches.ui.settings.DiagnosticsViewModel
import io.github.tunlezah.watches.ui.settings.SettingsViewModel
import io.github.tunlezah.watches.ui.theme.WatchTheme
import org.koin.compose.koinInject
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val uiModule = module {
    single { SampleGenerator(get(), get(), get()) }
    viewModel { CollectionViewModel(get(), get(), get()) }
    viewModel { params -> DetailViewModel(params.get(), get(), get(), get(), get()) }
    viewModel { params -> EditViewModel(params.getOrNull(), get(), get(), get()) }
    viewModel { params -> PhotosViewModel(params.get(), get(), get(), get()) }
    viewModel { params -> CropViewModel(params.get(), get(), get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { DiagnosticsViewModel(get(), get()) }
}

/** Root of the shared UI. Koin must already be started by the platform shell. */
@Composable
fun WatchesApp() {
    // Images come only from local files: no network fetcher is installed in this loader.
    setSingletonImageLoaderFactory { context -> ImageLoader.Builder(context).crossfade(120).build() }

    val settingsRepo = koinInject<SettingsRepository>()
    val watches = koinInject<WatchRepository>()
    val settings by settingsRepo.settings.collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(Unit) { watches.ensureSearchIndex() }

    val loaded = settings
    if (loaded == null) {
        // A few milliseconds while settings load: plain background, no flash of the wrong theme.
        Box(Modifier.fillMaxSize().background(if (isSystemInDarkTheme()) Color(0xFF0E0E0E) else Color(0xFFF4F4F4)))
        return
    }
    WatchTheme(loaded) { AppShell() }
}
