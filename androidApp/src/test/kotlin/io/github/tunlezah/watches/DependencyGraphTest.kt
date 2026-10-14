package io.github.tunlezah.watches

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import io.github.tunlezah.watches.data.androidDataModule
import io.github.tunlezah.watches.data.dataModule
import io.github.tunlezah.watches.search.androidSearchModule
import io.github.tunlezah.watches.search.searchModule
import io.github.tunlezah.watches.ui.uiModule
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Fails if any screen or repository asks for something no module provides. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = ScreenshotApp::class)
class DependencyGraphTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun everyDefinitionCanBeBuilt() {
        val context: Context = ApplicationProvider.getApplicationContext()
        module { includes(dataModule, androidDataModule(context), searchModule, androidSearchModule(context), uiModule) }.verify(
            // Plain values passed inline rather than injected.
            extraTypes = listOf(
                Context::class, SavedStateHandle::class, okio.Path::class, okio.FileSystem::class,
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
                // The source list is built in the search module's definition, not resolved by type.
                definition<io.github.tunlezah.watches.search.LookupService>(List::class),
                definition<io.github.tunlezah.watches.ui.lookup.ApplyViewModel>(io.github.tunlezah.watches.ui.lookup.LookupTarget::class),
            ),
        )
    }
}
