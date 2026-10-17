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
import io.github.tunlezah.watches.ui.care.BoxPapersViewModel
import io.github.tunlezah.watches.ui.care.DocumentViewerViewModel
import io.github.tunlezah.watches.ui.care.DocumentsViewModel
import io.github.tunlezah.watches.ui.care.ReminderSync
import io.github.tunlezah.watches.ui.care.ServiceDueViewModel
import io.github.tunlezah.watches.ui.care.ServiceEditViewModel
import io.github.tunlezah.watches.ui.care.ServiceHistoryViewModel
import io.github.tunlezah.watches.ui.collection.CollectionViewModel
import io.github.tunlezah.watches.ui.detail.DetailViewModel
import io.github.tunlezah.watches.ui.diagnostics.SampleGenerator
import io.github.tunlezah.watches.ui.edit.EditViewModel
import io.github.tunlezah.watches.ui.nav.AppShell
import io.github.tunlezah.watches.ui.photos.CropViewModel
import io.github.tunlezah.watches.ui.lookup.ApplyViewModel
import io.github.tunlezah.watches.ui.lookup.FileRobotsStore
import io.github.tunlezah.watches.ui.lookup.LookupDrafts
import io.github.tunlezah.watches.ui.lookup.LookupSession
import io.github.tunlezah.watches.ui.lookup.LookupViewModel
import io.github.tunlezah.watches.ui.lookup.RefreshSpecs
import io.github.tunlezah.watches.ui.lookup.RemoteImages
import io.github.tunlezah.watches.ui.lookup.ResultViewModel
import io.github.tunlezah.watches.ui.lookup.SourcesViewModel
import io.github.tunlezah.watches.search.http.RobotsStore
import io.github.tunlezah.watches.ui.photos.PhotoImporter
import io.github.tunlezah.watches.ui.photos.PhotosViewModel
import io.github.tunlezah.watches.ui.settings.DiagnosticsViewModel
import io.github.tunlezah.watches.ui.settings.SettingsViewModel
import io.github.tunlezah.watches.ui.theme.WatchTheme
import org.koin.compose.koinInject
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val uiModule = module {
    single { SampleGenerator(get(), get(), get(), get(), get()) }
    single { PhotoImporter(get()) }
    single { LookupSession() }
    single { LookupDrafts() }
    single { RemoteImages(get()) }
    single { RefreshSpecs(get(), get(), get()) }
    single<RobotsStore> { FileRobotsStore(get()) }
    viewModel { CollectionViewModel(get(), get(), get()) }
    viewModel { params -> DetailViewModel(params.get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { params ->
        // Two optional strings: the watch being edited, and a lookup draft for a new watch.
        EditViewModel(params.values.getOrNull(0) as String?, params.values.getOrNull(1) as String?, get(), get(), get(), get(), get())
    }
    viewModel { params -> PhotosViewModel(params.get(), get(), get(), get(), get()) }
    viewModel { params -> CropViewModel(params.get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { DiagnosticsViewModel(get(), get()) }
    viewModel { params -> LookupViewModel(params.get(), params.getOrNull(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { params -> ResultViewModel(params.get(), get(), get(), get()) }
    viewModel { params -> ApplyViewModel(params.get(), get(), get(), get(), get(), get()) }
    viewModel { SourcesViewModel(get(), get(), get()) }
    single { ReminderSync(get(), get(), get()) }
    viewModel { ServiceDueViewModel(get(), get(), get()) }
    viewModel { params -> ServiceHistoryViewModel(params.get(), get(), get(), get(), get(), get(), get()) }
    viewModel { params ->
        // The watch, and the entry being edited (null for a new one).
        ServiceEditViewModel(params.values[0] as String, params.values.getOrNull(1) as String?, get(), get(), get(), get())
    }
    viewModel { params -> BoxPapersViewModel(params.get(), get(), get(), get(), get(), get()) }
    viewModel { params -> DocumentsViewModel(params.get(), get(), get(), get(), get(), get()) }
    viewModel { params -> DocumentViewerViewModel(params.get(), get(), get()) }
}

/** Root of the shared UI. Koin must already be started by the platform shell. */
@Composable
fun WatchesApp() {
    // No network fetcher is installed: photos load from local files, and lookup pictures arrive as
    // bytes fetched by the lookup's polite HTTP client.
    setSingletonImageLoaderFactory { context -> ImageLoader.Builder(context).crossfade(120).build() }

    val settingsRepo = koinInject<SettingsRepository>()
    val watches = koinInject<WatchRepository>()
    val settings by settingsRepo.settings.collectAsStateWithLifecycle(initialValue = null)
    val reminders = koinInject<ReminderSync>()
    LaunchedEffect(Unit) { watches.ensureSearchIndex() }
    // Keeps scheduled service reminders in step with the collection while the app runs.
    LaunchedEffect(Unit) { reminders.start(this) }

    val loaded = settings
    if (loaded == null) {
        // A few milliseconds while settings load: plain background, no flash of the wrong theme.
        Box(Modifier.fillMaxSize().background(if (isSystemInDarkTheme()) Color(0xFF0E0E0E) else Color(0xFFF4F4F4)))
        return
    }
    WatchTheme(loaded) { AppShell() }
}
