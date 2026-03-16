package com.storyteller_f.a.panel

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilePresent
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Topic
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.storyteller_f.a.app.core.common.LocalClient
import com.storyteller_f.a.app.core.components.CustomGlobalDialogController
import com.storyteller_f.a.app.core.components.CustomGlobalTask
import com.storyteller_f.a.app.core.components.GlobalDialog
import com.storyteller_f.a.app.core.components.GlobalDialogContext
import com.storyteller_f.a.app.core.components.GlobalDialogController
import com.storyteller_f.a.app.core.components.GlobalDialogState
import com.storyteller_f.a.app.core.components.GlobalTask
import com.storyteller_f.a.app.core.components.GlobalTaskContext
import com.storyteller_f.a.app.core.utils.SessionHistoryManager
import com.storyteller_f.a.app.core.utils.buildSessionHistoryFactory
import com.storyteller_f.a.app.core.utils.createSettings
import com.storyteller_f.a.app.core.utils.restoreFromStorage
import com.storyteller_f.a.client.core.ClientSessionState
import com.storyteller_f.a.client.core.LoadingState
import com.storyteller_f.a.client.core.PanelSessionManager
import com.storyteller_f.a.client.core.PanelSessionModel
import com.storyteller_f.a.client.core.createPanelSessionManager
import com.storyteller_f.a.client.core.defaultClientConfigureForPanel
import com.storyteller_f.a.client.core.getClient
import com.storyteller_f.a.client.core.startBackgroundTask
import com.storyteller_f.a.client.room.getRoomModelStorage
import com.storyteller_f.a.panel.common.OnUserAdded
import com.storyteller_f.a.panel.common.OnUserStatusUpdated
import com.storyteller_f.a.panel.common.PanelNav
import com.storyteller_f.a.panel.common.PanelNavFactory
import com.storyteller_f.a.panel.common.PanelOverviewScreen
import com.storyteller_f.a.panel.common.newPanelNav
import com.storyteller_f.a.panel.common.panelNavSerializersModule
import com.storyteller_f.a.panel.common.rootEntryProvider
import com.storyteller_f.a.panel.ui.theme.PanelTheme
import com.storyteller_f.storage.ModelStorage
import com.storyteller_f.storage.UserCollection
import com.storyteller_f.storage.update
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.CookiesStorage
import kotlinx.collections.immutable.PersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(DelicateCoroutinesApi::class)
val panelAccountInstance = PanelAccountInstance(GlobalScope)

typealias PanelGlobalDialogController = GlobalDialogController<GlobalDialogContext<CustomPanelSessionManager>>

val LocalPanelGlobalDialog = compositionLocalOf<PanelGlobalDialogController> {
    object : PanelGlobalDialogController {
        override val state: MutableState<PersistentList<GlobalDialogState>>
            get() = TODO("Not yet implemented")

        override suspend fun <T> useResult(
            block: suspend PanelGlobalDialogController.() -> Result<T>
        ): Result<T> {
            TODO("Not yet implemented")
        }

        override fun emitProgress(block: (GlobalDialogState.Loading) -> GlobalDialogState.Loading) {
            TODO("Not yet implemented")
        }

        override val context: GlobalDialogContext<CustomPanelSessionManager>
            get() = TODO("Not yet implemented")
    }
}

val LocalPanelGlobalTask = compositionLocalOf<GlobalTask<CustomPanelSessionManager>> {
    object : GlobalTask<CustomPanelSessionManager> {
        override val stateMap: SnapshotStateMap<String, LoadingState?>
            get() = TODO("Not yet implemented")
        override val context: GlobalTaskContext<CustomPanelSessionManager>
            get() = TODO("Not yet implemented")

        override fun use(
            key: String,
            block: suspend GlobalTask<CustomPanelSessionManager>.(MutableStateFlow<LoadingState?>) -> Unit
        ) {
            TODO("Not yet implemented")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val sessionManager = panelAccountInstance.sessionManager
    val client = sessionManager.client
    val config = remember {
        SavedStateConfiguration {
            serializersModule = panelNavSerializersModule
        }
    }
    val backStack = rememberNavBackStack(config, PanelOverviewScreen)

    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val controller = panelAccountInstance.controller
    val task = panelAccountInstance.task
    val nav = remember {
        object : PanelNavFactory {
            val panelNav = newPanelNav(backStack, drawerState, scope)
            override fun newPanelNav() = panelNav
        }
    }
    CompositionLocalProvider(
        LocalClient provides client,
        LocalPanelNav provides nav.newPanelNav(),
        LocalPanelGlobalDialog provides controller,
        LocalPanelGlobalTask provides task
    ) {
        PanelTheme {
            val scope = rememberCoroutineScope()
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    PanelDrawer(scope, drawerState, nav.newPanelNav())
                }
            ) {
                MainPanelPage(backStack, nav.newPanelNav())
            }
            GlobalDialog(controller)
        }
    }
}

@Composable
private fun MainPanelPage(
    backStack: NavBackStack<NavKey>,
    nav: PanelNav
) {
    NavDisplay(
        backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        transitionSpec = {
            slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
        },
        popTransitionSpec = {
            slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
        },
        predictivePopTransitionSpec = {
            slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
        },
        entryProvider = rootEntryProvider(nav)
    )
}

@Composable
private fun PanelDrawer(
    scope: CoroutineScope,
    drawerState: DrawerState,
    nav: PanelNav
) {
    ModalDrawerSheet {
        DrawerHeader()
        DrawerNavItem(
            Icons.Default.Home,
            stringResource(Res.string.overview),
            onNavigate(scope, drawerState) { nav.gotoOverview() }
        )
        DrawerNavItem(
            Icons.Default.People,
            stringResource(Res.string.all_users),
            onNavigate(scope, drawerState) { nav.gotoAllUsers() }
        )
        DrawerNavItem(
            Icons.Default.Group,
            stringResource(Res.string.all_communities),
            onNavigate(scope, drawerState) { nav.gotoAllCommunities() }
        )
        DrawerNavItem(
            Icons.Default.Public,
            stringResource(Res.string.all_public_rooms),
            onNavigate(scope, drawerState) { nav.gotoAllPublicRooms() }
        )
        DrawerNavItem(
            Icons.Default.Lock,
            stringResource(Res.string.all_private_rooms),
            onNavigate(scope, drawerState) { nav.gotoAllPrivateRooms() }
        )
        DrawerNavItem(
            Icons.Default.Topic,
            stringResource(Res.string.all_topics),
            onNavigate(scope, drawerState) { nav.gotoAllTopics() }
        )
        DrawerNavItem(
            Icons.Default.FilePresent,
            stringResource(Res.string.all_files),
            onNavigate(scope, drawerState) { nav.gotoAllFiles() }
        )
        DrawerNavItem(
            Icons.Default.Title,
            stringResource(Res.string.all_titles),
            onNavigate(scope, drawerState) { nav.gotoAllTitles() }
        )
    }
}

@Composable
private fun DrawerHeader() {
    Text(
        stringResource(Res.string.navigation_menu),
        modifier = Modifier.padding(16.dp),
        style = MaterialTheme.typography.titleLarge
    )
    HorizontalDivider()
}

@Composable
private fun DrawerNavItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
        selected = false,
        onClick = onClick
    )
}

private fun onNavigate(
    scope: CoroutineScope,
    drawerState: DrawerState,
    navigate: () -> Unit
): () -> Unit = {
    scope.launch { drawerState.close() }
    navigate()
}

class PanelAccountInstance(scope: CoroutineScope) {
    val sessionManager = createCustomPanelSessionManager("default") { model, cookieManager ->
        getClient {
            defaultClientConfigureForPanel(cookieManager, manager = model, httpUrl = PanelConfig.SERVER_URL)
        }
    }
    val events = MutableSharedFlow<Any>()
    val controller = CustomGlobalDialogController(GlobalDialogContext(events, sessionManager))
    val task = CustomGlobalTask(scope, GlobalTaskContext(events, sessionManager))
    val guestDatabase = getRoomModelStorage("guest")
    val database = sessionManager.model.state.distinctUntilChangedBy {
        it
    }.map {
        if (it is ClientSessionState.Success) {
            val address = it.userPass.address().getOrThrow()
            getRoomModelStorage(address)
        } else {
            guestDatabase
        }
    }.stateIn(scope, SharingStarted.Eagerly, guestDatabase)

    init {
        scope.launch {
            sessionManager.startBackgroundTask()
        }
        scope.launch {
            database.collectLatest { storage ->
                events.collectLatest {
                    processEvent(it, storage)
                }
            }
        }
    }

    private suspend fun processEvent(
        any: Any,
        storage: ModelStorage
    ) {
        when (any) {
            is OnUserAdded -> {
                storage.user.saveToDefault(any.info)
            }
            is OnUserStatusUpdated -> {
                storage.user.update(UserCollection.Users, any.uid) {
                    it.copy(status = any.status)
                }
                storage.user.update(UserCollection.AllUsers, any.uid) {
                    it.copy(status = any.status)
                }
                val overview = storage.userOverview.observeDatum().firstOrNull()
                if (overview != null && overview.userInfo.id == any.uid) {
                    val updatedUser = overview.userInfo.copy(status = any.status)
                    storage.userOverview.save(overview.copy(userInfo = updatedUser))
                }
            }
        }
    }
}

class CustomPanelSessionManager(
    val proxy: PanelSessionManager,
    val historyFactory: SessionHistoryManager,
) : PanelSessionManager by proxy {
    companion object
}

fun createCustomPanelSessionManager(
    settingsName: String,
    createClient: (PanelSessionModel, CookiesStorage) -> HttpClient,
): CustomPanelSessionManager {
    val settings = createSettings(settingsName)
    val customSessionManager = createPanelSessionManager(createClient)
    customSessionManager.restoreFromStorage(settings)
    val historyFactory = buildSessionHistoryFactory(settings)
    return CustomPanelSessionManager(customSessionManager, historyFactory)
}
