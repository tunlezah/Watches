package io.github.tunlezah.watches

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import io.github.tunlezah.watches.data.androidDataModule
import io.github.tunlezah.watches.data.dataModule
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
        module { includes(dataModule, androidDataModule(context), uiModule) }.verify(
            // Plain values passed inline rather than injected.
            extraTypes = listOf(Context::class, SavedStateHandle::class, okio.Path::class, okio.FileSystem::class),
            injections = injectedParameters(
                definition<io.github.tunlezah.watches.ui.detail.DetailViewModel>(String::class),
                definition<io.github.tunlezah.watches.ui.edit.EditViewModel>(String::class),
                definition<io.github.tunlezah.watches.ui.photos.PhotosViewModel>(String::class),
                definition<io.github.tunlezah.watches.ui.photos.CropViewModel>(String::class),
            ),
        )
    }
}
