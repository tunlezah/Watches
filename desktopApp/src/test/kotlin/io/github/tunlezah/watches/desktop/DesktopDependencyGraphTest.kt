package io.github.tunlezah.watches.desktop

import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify
import kotlin.test.Test

/** Fails if any screen or repository asks for something no desktop module provides. The Android and iOS graphs have the same check. */
class DesktopDependencyGraphTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun everyDefinitionCanBeBuilt() {
        module { includes(desktopModules("unused")) }.verify(
            // Plain values passed inline rather than injected.
            extraTypes = listOf(
                androidx.lifecycle.SavedStateHandle::class, okio.Path::class, okio.FileSystem::class,
                // The HTTP engine is built inline by the search module; Ktor isn't on this module's compile classpath.
                Class.forName("io.ktor.client.engine.HttpClientEngine").kotlin,
                kotlin.time.Clock::class, kotlin.time.Duration::class, Function0::class,
            ),
            injections = injectedParameters(
                definition<io.github.tunlezah.watches.ui.detail.DetailViewModel>(String::class),
                definition<io.github.tunlezah.watches.ui.edit.EditViewModel>(String::class),
                definition<io.github.tunlezah.watches.ui.photos.PhotosViewModel>(String::class),
                definition<io.github.tunlezah.watches.ui.photos.CropViewModel>(String::class),
                definition<io.github.tunlezah.watches.ui.lookup.LookupViewModel>(io.github.tunlezah.watches.ui.lookup.LookupTarget::class, String::class),
                definition<io.github.tunlezah.watches.ui.lookup.ResultViewModel>(String::class),
                definition<io.github.tunlezah.watches.ui.care.ServiceHistoryViewModel>(String::class),
                definition<io.github.tunlezah.watches.ui.care.ServiceEditViewModel>(String::class),
                definition<io.github.tunlezah.watches.ui.care.BoxPapersViewModel>(String::class),
                definition<io.github.tunlezah.watches.ui.care.DocumentsViewModel>(String::class),
                definition<io.github.tunlezah.watches.ui.care.DocumentViewerViewModel>(String::class),
                // The source list is built in the search module's definition, not resolved by type.
                definition<io.github.tunlezah.watches.search.LookupService>(List::class),
                definition<io.github.tunlezah.watches.ui.lookup.ApplyViewModel>(io.github.tunlezah.watches.ui.lookup.LookupTarget::class),
            ),
        )
    }
}
