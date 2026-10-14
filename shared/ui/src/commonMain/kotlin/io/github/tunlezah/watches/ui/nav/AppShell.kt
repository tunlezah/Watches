package io.github.tunlezah.watches.ui.nav

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import io.github.tunlezah.watches.core.settings.Direction
import io.github.tunlezah.watches.ui.collection.CollectionScreen
import io.github.tunlezah.watches.ui.collection.CollectionViewModel
import io.github.tunlezah.watches.ui.collection.FilterSheet
import io.github.tunlezah.watches.ui.components.EmptyState
import io.github.tunlezah.watches.ui.components.LocalAnimationsEnabled
import io.github.tunlezah.watches.ui.components.LocalEntryAnimatedScope
import io.github.tunlezah.watches.ui.components.LocalSharedTransitionScope
import io.github.tunlezah.watches.ui.detail.DetailActions
import io.github.tunlezah.watches.ui.detail.DetailScreen
import io.github.tunlezah.watches.ui.detail.DetailViewModel
import io.github.tunlezah.watches.ui.diagnostics.PerfLog
import io.github.tunlezah.watches.ui.edit.EditScreen
import io.github.tunlezah.watches.ui.edit.EditViewModel
import io.github.tunlezah.watches.ui.photos.CropScreen
import io.github.tunlezah.watches.ui.photos.CropViewModel
import io.github.tunlezah.watches.ui.photos.PhotosScreen
import io.github.tunlezah.watches.ui.photos.PhotosViewModel
import io.github.tunlezah.watches.ui.photos.ViewerScreen
import io.github.tunlezah.watches.ui.platform.processUptimeMillis
import io.github.tunlezah.watches.ui.platform.systemAnimationsEnabled
import io.github.tunlezah.watches.ui.settings.DiagnosticsScreen
import io.github.tunlezah.watches.ui.settings.DiagnosticsViewModel
import io.github.tunlezah.watches.ui.settings.ComingSoonScreen
import io.github.tunlezah.watches.ui.settings.LicencesScreen
import io.github.tunlezah.watches.ui.settings.SettingsScreen
import io.github.tunlezah.watches.ui.settings.SettingsViewModel
import io.github.tunlezah.watches.ui.theme.WatchIcons
import io.github.tunlezah.watches.ui.theme.WatchTheme
import io.github.tunlezah.watches.search.http.RequestLog
import io.github.tunlezah.watches.ui.lookup.ApplyScreen
import io.github.tunlezah.watches.ui.lookup.ApplyViewModel
import io.github.tunlezah.watches.ui.lookup.LookupActions
import io.github.tunlezah.watches.ui.lookup.LookupScreen
import io.github.tunlezah.watches.ui.lookup.LookupSession
import io.github.tunlezah.watches.ui.lookup.LookupSettingsSection
import io.github.tunlezah.watches.ui.lookup.LookupTarget
import io.github.tunlezah.watches.ui.lookup.LookupViewModel
import io.github.tunlezah.watches.ui.lookup.RemoteImages
import io.github.tunlezah.watches.ui.lookup.RequestLogScreen
import io.github.tunlezah.watches.ui.lookup.ResultScreen
import io.github.tunlezah.watches.ui.lookup.ResultViewModel
import io.github.tunlezah.watches.ui.lookup.SourcesViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.TimeSource

private data class Tab(val route: Route, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Route.Collection, "Collection", WatchIcons.Grid),
    Tab(Route.Lookup, "Lookup", WatchIcons.Lookup),
    Tab(Route.Service, "Service", WatchIcons.Service),
    Tab(Route.Settings, "Settings", WatchIcons.Sliders),
)

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AppShell() {
    val c = WatchTheme.colors
    val navigator = rememberNavigator()
    val animations = remember { systemAnimationsEnabled() }

    BoxWithConstraints(Modifier.fillMaxSize().background(c.bg)) {
        val wide = maxWidth >= 600.dp
        // Two panes only when the adaptive directive says so (840 dp and up by default), which can
        // differ from the 600 dp rail breakpoint. Both the strategy and the screens read this one value.
        val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())
        val twoPane = directive.maxHorizontalPartitions > 1
        val listDetail = rememberListDetailSceneStrategy<Route>(directive = directive)
        // Tabs hide while a form is open, so switching tab can't throw unsaved work away.
        val editing = navigator.backStack.any { it is Route.Edit || it is Route.Crop }
        Row(Modifier.fillMaxSize()) {
            if (wide && !editing) NavRail(navigator)
            Box(Modifier.weight(1f).fillMaxHeight()) {
                SharedTransitionLayout {
                    CompositionLocalProvider(
                        LocalSharedTransitionScope provides if (animations && !twoPane) this else null,
                        LocalAnimationsEnabled provides animations,
                    ) {
                        NavDisplay(
                            backStack = navigator.backStack,
                            onBack = { navigator.pop() },
                            entryDecorators = listOf(
                                rememberSaveableStateHolderNavEntryDecorator(),
                                rememberViewModelStoreNavEntryDecorator(),
                            ),
                            sceneStrategies = listOf(listDetail),
                            sharedTransitionScope = if (animations && !twoPane) this@SharedTransitionLayout else null,
                            transitionSpec = { forward(animations) },
                            popTransitionSpec = { backward(animations) },
                            predictivePopTransitionSpec = { backward(animations) },
                            entryProvider = entryProvider {
                                entry<Route.Collection>(metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { DetailPlaceholder() })) {
                                    InEntry { CollectionEntry(navigator) }
                                }
                                entry<Route.Detail>(metadata = ListDetailSceneStrategy.detailPane()) { r ->
                                    InEntry { DetailEntry(r.watchId, navigator, showBack = !twoPane) }
                                }
                                entry<Route.Edit> { r -> InEntry { EditEntry(r.watchId, r.draftKey, navigator) } }
                                entry<Route.Photos> { r -> InEntry { PhotosEntry(r.watchId, navigator) } }
                                entry<Route.Crop> { r -> InEntry { CropEntry(r.photoId, navigator) } }
                                entry<Route.Viewer> { r -> InEntry { ViewerEntry(r, navigator) } }
                                entry<Route.Lookup> { InEntry { LookupEntry(LookupTarget.None, null, navigator) } }
                                entry<Route.LookupFor> { r -> InEntry { LookupEntry(r.target, r.query, navigator) } }
                                entry<Route.LookupResult> { r -> InEntry { ResultEntry(r.hitId, navigator) } }
                                entry<Route.Apply> { r -> InEntry { ApplyEntry(r.target, navigator) } }
                                entry<Route.RequestLog> { InEntry { RequestLogEntry(navigator) } }
                                entry<Route.Service> {
                                    InEntry {
                                        ComingSoonScreen("Service", "Service history, what's due across the collection, reminders, box and papers, and documents. Arrives in milestone 3.")
                                    }
                                }
                                entry<Route.Settings> { InEntry { SettingsEntry(navigator) } }
                                entry<Route.Diagnostics> { InEntry { DiagnosticsEntry(navigator) } }
                                entry<Route.Licences> { InEntry { LicencesScreen(onBack = { navigator.pop() }) } }
                            },
                        )
                    }
                }
                if (!wide && navigator.backStack.size == 1) {
                    BottomBar(navigator, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
}

private fun forward(animations: Boolean): ContentTransform =
    if (!animations) EnterTransition.None togetherWith ExitTransition.None
    else (fadeIn(tween(220)) + slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { it / 10 }) togetherWith fadeOut(tween(160))

private fun backward(animations: Boolean): ContentTransform =
    if (!animations) EnterTransition.None togetherWith ExitTransition.None
    else fadeIn(tween(220)) togetherWith (fadeOut(tween(180)) + slideOutHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { it / 10 })

/** Gives screens the entry's animation scope for shared elements. */
@Composable
private fun InEntry(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalEntryAnimatedScope provides LocalNavAnimatedContentScope.current, content = content)
}

@Composable
private fun DetailPlaceholder() {
    val c = WatchTheme.colors
    Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
        EmptyState("Choose a watch", "Its photos and full spec sheet appear here.")
    }
}

@Composable
private fun CollectionEntry(navigator: Navigator) {
    val vm = koinViewModel<CollectionViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }
    LaunchedEffect(state.loaded) {
        if (state.loaded) {
            withFrameNanos { }
            processUptimeMillis()?.let(PerfLog::coldStart)
        }
    }
    CollectionScreen(
        state = state,
        onQuery = vm::setQuery,
        onTab = vm::setTab,
        onOpenFilters = { showFilters = true },
        onClearFilters = vm::clearFilters,
        onToggleLayout = { vm.toggleLayout() },
        onOpen = { id ->
            PerfLog.detailRequestedAt = TimeSource.Monotonic.markNow()
            navigator.openDetail(id)
        },
        onAdd = { navigator.push(Route.Edit(null)) },
    )
    if (showFilters) FilterSheet(state, { vm.setSort(it) }, vm::setFilter, { showFilters = false })
}

@Composable
private fun DetailEntry(watchId: String, navigator: Navigator, showBack: Boolean) {
    val vm = koinViewModel<DetailViewModel>(key = "detail-$watchId") { parametersOf(watchId) }
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.loading) {
        if (!state.loading) {
            withFrameNanos { }
            PerfLog.detailRequestedAt?.let { PerfLog.detailOpen(it.elapsedNow().inWholeMilliseconds) }
            PerfLog.detailRequestedAt = null
        }
    }
    DetailScreen(
        state = state,
        watchId = watchId,
        showBack = showBack,
        actions = DetailActions(
            onBack = { navigator.pop() },
            onEdit = { navigator.push(Route.Edit(watchId)) },
            onPhotos = { navigator.push(Route.Photos(watchId)) },
            onViewPhoto = { i -> navigator.push(Route.Viewer(watchId, i)) },
            onDuplicate = { vm.duplicate { id -> navigator.openDetail(id); navigator.push(Route.Edit(id)) } },
            onArchive = vm::setArchived,
            onDelete = { vm.delete { navigator.popTo { it !is Route.Detail && it !is Route.Edit } } },
            onLookUp = {
                val w = state.watch
                val query = listOfNotNull(w?.brand, w?.reference ?: w?.modelName ?: w?.collection).joinToString(" ")
                navigator.push(Route.LookupFor(LookupTarget.Watch(watchId), query))
            },
            onRefresh = {
                vm.refresh {
                    // Only if the watch is still what's on screen: never on top of a form opened meanwhile.
                    if (navigator.current == Route.Detail(watchId)) {
                        vm.dismissNotice()
                        navigator.push(Route.Apply(LookupTarget.Watch(watchId)))
                    }
                }
            },
            onDismissNotice = {
                if (state.reviewReady) navigator.push(Route.Apply(LookupTarget.Watch(watchId)))
                vm.dismissNotice()
            },
        ),
    )
}

@Composable
private fun EditEntry(watchId: String?, draftKey: String?, navigator: Navigator) {
    val vm = koinViewModel<EditViewModel>(key = "edit-${watchId ?: "new"}-${draftKey.orEmpty()}") { parametersOf(watchId, draftKey) }
    val state by vm.state.collectAsStateWithLifecycle()
    val session = koinInject<LookupSession>()
    EditScreen(
        state = state,
        vm = vm,
        onClose = { navigator.pop() },
        onSaved = { id ->
            // A watch made from lookup results: those results are used up, so "Add" can't make it twice.
            if (draftKey != null) session.clearTray()
            if (watchId == null) navigator.openDetail(id) else navigator.pop()
        },
        onLookUp = { query -> navigator.push(Route.LookupFor(LookupTarget.Form(vm.formKey), query)) },
    )
}

@Composable
private fun LookupEntry(target: LookupTarget, query: String?, navigator: Navigator) {
    val vm = koinViewModel<LookupViewModel>(key = "lookup-${target.encode()}") { parametersOf(target, query) }
    val state by vm.state.collectAsStateWithLifecycle()
    val choices by vm.watchChoices.collectAsStateWithLifecycle()
    LookupScreen(
        state = state,
        images = vm.sessionImages,
        watchChoices = choices,
        actions = LookupActions(
            onBack = if (target == LookupTarget.None) null else ({ navigator.pop() }),
            onQuery = vm::setQuery,
            onSearch = { q -> vm.search(q ?: state.query) },
            onForgetRecent = { vm.forgetRecent(it) },
            onClearRecent = { vm.clearRecent() },
            onToggleSource = { vm.toggleSource(it) },
            onOpen = { hit -> navigator.push(Route.LookupResult(LookupSession.idOf(hit))) },
            onRetry = vm::retry,
            onReview = { t -> navigator.push(Route.Apply(t)) },
            onClearTray = vm::clearTray,
        ),
    )
}

@Composable
private fun ResultEntry(hitId: String, navigator: Navigator) {
    val vm = koinViewModel<ResultViewModel>(key = "result-$hitId") { parametersOf(hitId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val settingsVm = koinViewModel<SettingsViewModel>()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    ResultScreen(
        state = state,
        images = koinInject<RemoteImages>(),
        prefs = settings?.display ?: io.github.tunlezah.watches.core.format.DisplayPrefs(),
        onBack = { navigator.pop() },
        onRetry = vm::load,
        onToggle = {
            val adding = !state.inTray
            vm.toggleTray()
            if (adding) navigator.pop()
        },
        onSavePicture = vm::savePicture,
    )
}

@Composable
private fun ApplyEntry(target: LookupTarget, navigator: Navigator) {
    val vm = koinViewModel<ApplyViewModel>(key = "apply-${target.encode()}") { parametersOf(target) }
    val state by vm.state.collectAsStateWithLifecycle()
    val settingsVm = koinViewModel<SettingsViewModel>()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    ApplyScreen(
        state = state,
        prefs = settings?.display ?: io.github.tunlezah.watches.core.format.DisplayPrefs(),
        onBack = { navigator.pop() },
        onChoose = vm::choose,
        onToggleExtras = vm::toggleExtras,
        onApply = {
            vm.apply(
                onSaved = { id -> navigator.popTo { it == Route.Detail(id) } },
                onFormFilled = { navigator.popTo { it is Route.Edit } },
                onDraft = { key -> navigator.push(Route.Edit(null, key)) },
            )
        },
    )
}

@Composable
private fun RequestLogEntry(navigator: Navigator) {
    val log = koinInject<RequestLog>()
    val entries by log.entries.collectAsStateWithLifecycle()
    RequestLogScreen(entries, onBack = { navigator.pop() }, onClear = log::clear)
}

@Composable
private fun PhotosEntry(watchId: String, navigator: Navigator) {
    val vm = koinViewModel<PhotosViewModel>(key = "photos-$watchId") { parametersOf(watchId) }
    val state by vm.state.collectAsStateWithLifecycle()
    PhotosScreen(
        state = state,
        onBack = { navigator.pop() },
        onAdd = vm::addFiles,
        onSetCover = { vm.setCover(it) },
        onMove = { id, d -> vm.move(id, d) },
        onCrop = { navigator.push(Route.Crop(it)) },
        onDelete = { vm.delete(it) },
        onDismissMessage = vm::dismissMessage,
    )
}

@Composable
private fun CropEntry(photoId: String, navigator: Navigator) {
    val vm = koinViewModel<CropViewModel>(key = "crop-$photoId") { parametersOf(photoId) }
    val state by vm.state.collectAsStateWithLifecycle()
    CropScreen(state, onBack = { navigator.pop() }, onSave = { rect -> vm.save(rect) { navigator.pop() } })
}

@Composable
private fun ViewerEntry(route: Route.Viewer, navigator: Navigator) {
    val vm = koinViewModel<DetailViewModel>(key = "viewer-${route.watchId}") { parametersOf(route.watchId) }
    val state by vm.state.collectAsStateWithLifecycle()
    if (state.photos.isNotEmpty()) ViewerScreen(state.photos, route.index) { navigator.pop() }
}

@Composable
private fun SettingsEntry(navigator: Navigator) {
    val vm = koinViewModel<SettingsViewModel>()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sources = koinViewModel<SourcesViewModel>()
    val sourcesState by sources.state.collectAsStateWithLifecycle()
    settings?.let { s ->
        SettingsScreen(
            settings = s,
            onUpdate = { vm.update(it) },
            onDiagnostics = { navigator.push(Route.Diagnostics) },
            onLicences = { navigator.push(Route.Licences) },
            lookupSection = {
                LookupSettingsSection(
                    state = sourcesState,
                    onToggle = { sources.toggle(it) },
                    onTestWatchBase = sources::testWatchBase,
                    onRequestLog = { navigator.push(Route.RequestLog) },
                    onClearRecent = { sources.clearRecent() },
                )
            },
        )
    }
}

@Composable
private fun DiagnosticsEntry(navigator: Navigator) {
    val vm = koinViewModel<DiagnosticsViewModel>()
    val state by vm.state.collectAsStateWithLifecycle()
    DiagnosticsScreen(state, onBack = { navigator.pop() }, onGenerate = vm::generate, onRemove = vm::removeSamples, onRebuild = vm::rebuildIndex)
}

@Composable
private fun BottomBar(navigator: Navigator, modifier: Modifier = Modifier) {
    val c = WatchTheme.colors
    val style = WatchTheme.style
    val current = navigator.topLevel
    if (style.floatingNav) {
        Row(
            modifier
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(c.surface.copy(alpha = 0.94f))
                .border(1.dp, c.line, RoundedCornerShape(32.dp))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (tab in tabs) TabItem(tab, current == tab.route, Modifier.weight(1f).fillMaxHeight()) { navigator.selectTab(tab.route) }
        }
    } else {
        Column(modifier.fillMaxWidth().background(c.bg)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp)) {
                for (tab in tabs) TabItem(tab, current == tab.route, Modifier.weight(1f).heightIn(min = 64.dp)) { navigator.selectTab(tab.route) }
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = WatchTheme.colors
    val t = WatchTheme.values
    val style = WatchTheme.style
    val color = if (selected) c.accent else c.fg3
    Column(
        modifier
            .then(if (style.floatingNav) Modifier.clip(RoundedCornerShape(26.dp)).background(if (selected) c.accentTint else c.surface.copy(alpha = 0f)) else Modifier)
            .then(
                if (style.direction == Direction.INSTRUMENT && selected) {
                    Modifier.drawBehind { drawLine(c.accent, Offset(0f, 0f), Offset(size.width, 0f), 2.dp.toPx()) }
                } else {
                    Modifier
                },
            )
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(3.dp))
        Text(if (style.direction == Direction.INSTRUMENT) tab.label.uppercase() else tab.label, style = t.type.nav, color = color, maxLines = 1)
    }
}

@Composable
private fun NavRail(navigator: Navigator) {
    val c = WatchTheme.colors
    Column(
        Modifier.width(88.dp).fillMaxHeight().background(c.bg).statusBarsPadding().padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (tab in tabs) TabItem(tab, navigator.topLevel == tab.route, Modifier.size(width = 80.dp, height = 64.dp)) { navigator.selectTab(tab.route) }
    }
    Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
}
