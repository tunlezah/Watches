package io.github.tunlezah.watches.ui

import io.github.tunlezah.watches.data.dataModule
import io.github.tunlezah.watches.data.db.AppDatabase
import io.github.tunlezah.watches.data.iosDataModule
import io.github.tunlezah.watches.search.iosSearchModule
import io.github.tunlezah.watches.search.searchModule
import io.github.tunlezah.watches.ui.care.ServiceEditViewModel
import io.github.tunlezah.watches.ui.edit.EditViewModel
import io.github.tunlezah.watches.ui.lookup.ApplyViewModel
import io.github.tunlezah.watches.ui.lookup.LookupTarget
import io.github.tunlezah.watches.ui.lookup.LookupViewModel
import io.github.tunlezah.watches.ui.platform.iosPlatformModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.parameter.ParametersHolder
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import platform.Foundation.NSTemporaryDirectory
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Fails if anything the iPhone app wires up asks for something no module provides: the iOS twin of
 * androidApp's DependencyGraphTest. Koin's verify() is JVM-only, so this builds every definition.
 */
class IosDependencyGraphTest {

    @OptIn(KoinInternalApi::class, ExperimentalCoroutinesApi::class)
    @Test fun everyDefinitionCanBeBuilt() {
        // View models start loading as soon as they're built, on the main dispatcher. A test dispatcher
        // that never runs holds that work back, so nothing is still using the database when it's closed
        // below. Work racing that close once crashed the test process (signal 11).
        Dispatchers.setMain(StandardTestDispatcher())
        val root = NSTemporaryDirectory().toPath() / "watches-graph-${Uuid.random()}"
        FileSystem.SYSTEM.createDirectories(root)
        val app = koinApplication {
            modules(dataModule, iosDataModule(root.toString()), searchModule, iosSearchModule(), iosPlatformModule(), uiModule)
        }
        try {
            val definitions = app.koin.instanceRegistry.instances.values.map { it.beanDefinition }.distinct()
            assertTrue(definitions.size > 30, "only ${definitions.size} definitions found")
            val failures = definitions.mapNotNull { d ->
                runCatching { app.koin.get<Any>(d.primaryType, d.qualifier) { parametersFor(d.primaryType) } }
                    .exceptionOrNull()?.let { "${d.primaryType.simpleName}: $it" }
            }
            assertTrue(failures.isEmpty(), failures.joinToString("\n"))
        } finally {
            runCatching { app.koin.get<AppDatabase>().close() }
            app.close()
            FileSystem.SYSTEM.deleteRecursively(root)
            Dispatchers.resetMain()
        }
    }

    /** What the screens pass when they open these view models; every other definition takes one id or nothing. */
    private fun parametersFor(type: KClass<*>): ParametersHolder = when (type) {
        LookupViewModel::class -> parametersOf(LookupTarget.None, "omega")
        ApplyViewModel::class -> parametersOf(LookupTarget.None)
        EditViewModel::class -> parametersOf(null, null)
        ServiceEditViewModel::class -> parametersOf("watch", null)
        else -> parametersOf("id")
    }
}
