package io.github.tunlezah.watches.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList

/** Every destination. Reserved for later milestones: Wear, Accuracy, Value, Glossary, Clock, Identify. */
sealed interface Route {
    data object Collection : Route
    data class Detail(val watchId: String) : Route
    data class Edit(val watchId: String?) : Route
    data class Photos(val watchId: String) : Route
    data class Crop(val photoId: String) : Route
    data class Viewer(val watchId: String, val index: Int) : Route
    data object Lookup : Route
    data object Service : Route
    data object Settings : Route
    data object Diagnostics : Route
    data object Licences : Route

    val isTopLevel: Boolean get() = this in TopLevel

    companion object {
        val TopLevel = listOf(Collection, Lookup, Service, Settings)

        fun encode(r: Route): String = when (r) {
            Collection -> "collection"
            is Detail -> "detail|${r.watchId}"
            is Edit -> "edit|${r.watchId ?: ""}"
            is Photos -> "photos|${r.watchId}"
            is Crop -> "crop|${r.photoId}"
            is Viewer -> "viewer|${r.watchId}|${r.index}"
            Lookup -> "lookup"
            Service -> "service"
            Settings -> "settings"
            Diagnostics -> "diagnostics"
            Licences -> "licences"
        }

        fun decode(s: String): Route? {
            val p = s.split('|')
            return when (p[0]) {
                "collection" -> Collection
                "detail" -> Detail(p[1])
                "edit" -> Edit(p.getOrNull(1)?.ifEmpty { null })
                "photos" -> Photos(p[1])
                "crop" -> Crop(p[1])
                "viewer" -> Viewer(p[1], p.getOrNull(2)?.toIntOrNull() ?: 0)
                "lookup" -> Lookup
                "service" -> Service
                "settings" -> Settings
                "diagnostics" -> Diagnostics
                "licences" -> Licences
                else -> null
            }
        }
    }
}

/** A plain list back stack (Navigation 3 style). It survives process death as encoded strings. */
@Stable
class Navigator(val backStack: SnapshotStateList<Route>) {

    val current: Route get() = backStack.last()
    val topLevel: Route get() = backStack.first()

    fun push(route: Route) {
        backStack.add(route)
    }

    /** Opens a watch. On wide screens the list stays and the detail pane is swapped, not stacked. */
    fun openDetail(watchId: String) {
        if (current is Route.Detail) backStack[backStack.lastIndex] = Route.Detail(watchId) else push(Route.Detail(watchId))
    }

    fun replaceTop(route: Route) {
        backStack[backStack.lastIndex] = route
    }

    fun pop(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    /** Pops until a route matching [predicate] is on top (it stays). */
    fun popTo(predicate: (Route) -> Boolean) {
        while (backStack.size > 1 && !predicate(current)) backStack.removeAt(backStack.lastIndex)
    }

    fun selectTab(route: Route) {
        if (topLevel == route && backStack.size == 1) return
        backStack.clear()
        backStack.add(route)
    }
}

@Composable
fun rememberNavigator(start: Route = Route.Collection): Navigator {
    val stack = rememberSaveable(
        saver = Saver<SnapshotStateList<Route>, List<String>>(
            save = { list -> list.map(Route::encode) },
            restore = { saved -> mutableStateListOf<Route>().apply { addAll(saved.mapNotNull(Route::decode).ifEmpty { listOf(start) }) } },
        ),
    ) { mutableStateListOf(start) }
    return androidx.compose.runtime.remember(stack) { Navigator(stack) }
}
