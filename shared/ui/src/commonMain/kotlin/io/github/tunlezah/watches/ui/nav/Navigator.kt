package io.github.tunlezah.watches.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import io.github.tunlezah.watches.ui.lookup.LookupTarget

/** Every destination. Reserved for later milestones: Wear, Accuracy, Value, Glossary, Clock, Identify. */
sealed interface Route {
    data object Collection : Route
    data class Detail(val watchId: String) : Route
    data class Edit(val watchId: String?, val draftKey: String? = null) : Route
    data class Photos(val watchId: String) : Route
    data class Crop(val photoId: String) : Route
    data class Viewer(val watchId: String, val index: Int) : Route
    data object Lookup : Route
    data class LookupFor(val target: LookupTarget, val query: String) : Route
    data class LookupResult(val hitId: String) : Route
    data class Apply(val target: LookupTarget) : Route
    data object RequestLog : Route
    data class ServiceHistory(val watchId: String) : Route
    data class ServiceEdit(val watchId: String, val entryId: String?) : Route
    data class BoxPapers(val watchId: String) : Route
    /** [addKind] opens the add dialog straight away, for "Add manual" on a watch. */
    data class Documents(val watchId: String, val addKind: String? = null) : Route
    data class DocumentViewer(val documentId: String) : Route
    data object Service : Route
    data object Settings : Route
    /** A page under Settings, by [io.github.tunlezah.watches.ui.settings.SettingsPage] key. */
    data class SettingsPage(val page: String) : Route
    /** Reviewing a backup, restoring it, and the report. */
    data object RestoreBackup : Route
    data object Diagnostics : Route
    data object Licences : Route

    val isTopLevel: Boolean get() = this in TopLevel

    companion object {
        val TopLevel = listOf(Collection, Lookup, Service, Settings)

        fun encode(r: Route): String = when (r) {
            Collection -> "collection"
            is Detail -> "detail|${r.watchId}"
            is Edit -> "edit|${r.watchId ?: ""}|${r.draftKey ?: ""}"
            is Photos -> "photos|${r.watchId}"
            is Crop -> "crop|${r.photoId}"
            is Viewer -> "viewer|${r.watchId}|${r.index}"
            Lookup -> "lookup"
            is LookupFor -> "lookupfor|${r.target.encode()}|${r.query.replace('|', ' ')}"
            is LookupResult -> "result|${r.hitId}"
            is Apply -> "apply|${r.target.encode()}"
            RequestLog -> "requestlog"
            is ServiceHistory -> "service-history|${r.watchId}"
            is ServiceEdit -> "service-edit|${r.watchId}|${r.entryId ?: ""}"
            is BoxPapers -> "papers|${r.watchId}"
            is Documents -> "documents|${r.watchId}|${r.addKind ?: ""}"
            is DocumentViewer -> "document|${r.documentId}"
            Service -> "service"
            Settings -> "settings"
            is SettingsPage -> "settings-page|${r.page}"
            RestoreBackup -> "restore"
            Diagnostics -> "diagnostics"
            Licences -> "licences"
        }

        fun decode(s: String): Route? {
            val p = s.split('|')
            return when (p[0]) {
                "collection" -> Collection
                "detail" -> Detail(p[1])
                "edit" -> Edit(p.getOrNull(1)?.ifEmpty { null }, p.getOrNull(2)?.ifEmpty { null })
                "photos" -> Photos(p[1])
                "crop" -> Crop(p[1])
                "viewer" -> Viewer(p[1], p.getOrNull(2)?.toIntOrNull() ?: 0)
                "lookup" -> Lookup
                "lookupfor" -> LookupFor(LookupTarget.decode(p[1]), p.getOrNull(2).orEmpty())
                "result" -> LookupResult(p[1])
                "apply" -> Apply(LookupTarget.decode(p[1]))
                "requestlog" -> RequestLog
                "service-history" -> ServiceHistory(p[1])
                "service-edit" -> ServiceEdit(p[1], p.getOrNull(2)?.ifEmpty { null })
                "papers" -> BoxPapers(p[1])
                // A restored screen doesn't reopen the add dialog.
                "documents" -> Documents(p[1])
                "document" -> DocumentViewer(p[1])
                "service" -> Service
                "settings" -> Settings
                "settings-page" -> SettingsPage(p[1])
                // A restore can't resume after the app was killed; dropping it lands on Backup & restore, beneath it.
                "restore" -> null
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

    /**
     * Opens a watch from its tab's list. Whatever sat above the list (another watch, its photos)
     * goes first, so the stack never holds two details and back always returns to the list.
     */
    fun openDetail(watchId: String) {
        popTo { it.isTopLevel }
        push(Route.Detail(watchId))
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
