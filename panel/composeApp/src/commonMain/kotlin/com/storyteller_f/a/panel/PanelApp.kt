package com.storyteller_f.a.panel

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
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
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
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
import com.storyteller_f.a.client.core.ConstPassHolder
import com.storyteller_f.a.client.core.PanelSessionManager
import com.storyteller_f.a.client.core.SignResult
import com.storyteller_f.a.client.core.SimplePanelSessionManager
import com.storyteller_f.a.client.core.UserPass
import com.storyteller_f.a.client.core.createSimplePanelSessionManager
import com.storyteller_f.a.client.core.defaultClientConfigureForPanel
import com.storyteller_f.a.client.core.getClient
import com.storyteller_f.a.client.room.getRoomModelStorage
import com.storyteller_f.a.panel.common.OnCommunityStatusUpdated
import com.storyteller_f.a.panel.common.OnFileStatusUpdated
import com.storyteller_f.a.panel.common.OnRoomStatusUpdated
import com.storyteller_f.a.panel.common.OnTitleStatusUpdated
import com.storyteller_f.a.panel.common.OnTopicStatusUpdated
import com.storyteller_f.a.panel.common.OnUserAdded
import com.storyteller_f.a.panel.common.OnUserStatusUpdated
import com.storyteller_f.a.panel.common.PanelNav
import com.storyteller_f.a.panel.common.PanelNavFactory
import com.storyteller_f.a.panel.common.PanelOverviewScreen
import com.storyteller_f.a.panel.common.newPanelNav
import com.storyteller_f.a.panel.common.panelNavSerializersModule
import com.storyteller_f.a.panel.common.rootEntryProvider
import com.storyteller_f.a.panel.ui.theme.PanelTheme
import com.storyteller_f.shared.model.PanelAccountInfo
import com.storyteller_f.storage.CommunityCollection
import com.storyteller_f.storage.FileCollection
import com.storyteller_f.storage.ModelStorage
import com.storyteller_f.storage.RoomCollection
import com.storyteller_f.storage.TitleCollection
import com.storyteller_f.storage.TopicCollection
import com.storyteller_f.storage.UserCollection
import com.storyteller_f.storage.update
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

typealias PanelGlobalDialogController = GlobalDialogController<GlobalDialogContext<CustomPanelSessionManager>>

val LocalPanelGlobalDialog = compositionLocalOf<PanelGlobalDialogController> {
    object : PanelGlobalDialogController {
        override val state: MutableStateFlow<PersistentList<GlobalDialogState>> = MutableStateFlow(persistentListOf())

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

val LocalPanelGlobalTask =
    compositionLocalOf<GlobalTask<GlobalTaskContext<CustomPanelSessionManager>>> {
        error("LocalPanelGlobalTask must be provided")
    }

val LocalPanelUiViewModel =
    staticCompositionLocalOf<PanelUIViewModel> {
        error("LocalPanelUiViewModel must be provided")
    }

fun createPanelUIViewModel() = PanelUIViewModel(kotlinx.coroutines.GlobalScope, PanelConfig.SERVER_URL)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val panelUiViewModel = LocalPanelUiViewModel.current
    val panelAccountInstance by panelUiViewModel.instance.collectAsState()
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
            PanelNavigationDrawer(
                drawerState = drawerState,
                drawerContent = { permanent ->
                    PanelDrawer(scope, drawerState, nav.newPanelNav(), permanent)
                }
            ) {
                MainPanelPage(backStack, nav.newPanelNav())
            }
            GlobalDialog(controller)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
private fun PanelNavigationDrawer(
    drawerState: DrawerState,
    drawerContent: @Composable (permanent: Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    val windowSizeClass = calculateWindowSizeClass()
    val usePermanentDrawer = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
    if (usePermanentDrawer) {
        PermanentNavigationDrawer(
            drawerContent = { drawerContent(true) },
            content = content
        )
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = { drawerContent(false) },
            content = content
        )
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
    nav: PanelNav,
    permanent: Boolean
) {
    val content: @Composable ColumnScope.() -> Unit = {
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
        DrawerNavItem(
            Icons.Default.Build,
            stringResource(Res.string.worker_records),
            onNavigate(scope, drawerState) { nav.gotoTaskRecords() }
        )
    }
    if (permanent) {
        PermanentDrawerSheet(content = content)
    } else {
        ModalDrawerSheet(content = content)
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

class CustomPanelSessionManager(
    val proxy: SimplePanelSessionManager,
    val historyFactory: SessionHistoryManager,
) : PanelSessionManager by proxy {
    companion object
}

sealed interface IPanelAccountInstance {
    val database: ModelStorage
    val sessionManager: CustomPanelSessionManager
    val task: CustomGlobalTask<GlobalTaskContext<CustomPanelSessionManager>>
    val controller: CustomGlobalDialogController<GlobalDialogContext<CustomPanelSessionManager>>
    val address: String

    val passHolder: ConstPassHolder

    val isAlreadySign get() = passHolder.currentUserPass != null

    class None(scope: CoroutineScope, httpUrl: String) : IPanelAccountInstance {
        val events = MutableSharedFlow<Any>()
        override val passHolder: ConstPassHolder = ConstPassHolder(null)

        override val sessionManager = createPanelSessionManager(
            httpUrl,
            passHolder,
            AcceptAllCookiesStorage()
        )
        override val database = getRoomModelStorage("guest")
        val context: GlobalTaskContext<CustomPanelSessionManager> =
            GlobalTaskContext(events, sessionManager)
        val dialogContext: GlobalDialogContext<CustomPanelSessionManager> =
            GlobalDialogContext(events, sessionManager)
        override val task = CustomGlobalTask(scope, context)
        override val controller = CustomGlobalDialogController(dialogContext)
        override val address: String = "guest"
    }

    class Regular(
        scope: CoroutineScope,
        override val address: String,
        httpUrl: String,
        override val passHolder: ConstPassHolder,
        cookiesStorage: AcceptAllCookiesStorage
    ) : IPanelAccountInstance {
        val events = MutableSharedFlow<Any>()
        override val sessionManager = createPanelSessionManager(httpUrl, passHolder, cookiesStorage)
        override val database = getRoomModelStorage(address)
        val context: GlobalTaskContext<CustomPanelSessionManager> =
            GlobalTaskContext(events, sessionManager)
        val dialogContext: GlobalDialogContext<CustomPanelSessionManager> =
            GlobalDialogContext(events, sessionManager)
        override val task = CustomGlobalTask(scope, context)
        override val controller = CustomGlobalDialogController(dialogContext)

        init {
            scope.launch {
                events.collectLatest {
                    processEvent(it, database)
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

                is OnUserStatusUpdated -> handleUserStatusUpdated(storage, any)
                is OnCommunityStatusUpdated -> handleCommunityReadOnlyUpdated(storage, any)
                is OnRoomStatusUpdated -> handleRoomReadOnlyUpdated(storage, any)
                is OnTopicStatusUpdated -> handleTopicReadOnlyUpdated(storage, any)
                is OnTitleStatusUpdated -> handleTitleReadOnlyUpdated(storage, any)
                is OnFileStatusUpdated -> handleFileReadOnlyUpdated(storage, any)
            }
        }

        private suspend fun handleUserStatusUpdated(
            storage: ModelStorage,
            event: OnUserStatusUpdated
        ) {
            storage.user.update(UserCollection.Users, event.uid) {
                it.copy(status = event.status)
            }
            storage.user.update(UserCollection.AllUsers, event.uid) {
                it.copy(status = event.status)
            }
            val overview = storage.userOverview.observeDatum().firstOrNull()
            if (overview != null && overview.userInfo.id == event.uid) {
                val updatedUser = overview.userInfo.copy(status = event.status)
                storage.userOverview.save(overview.copy(userInfo = updatedUser))
            }
        }

        private suspend fun handleCommunityReadOnlyUpdated(
            storage: ModelStorage,
            event: OnCommunityStatusUpdated
        ) {
            storage.community.update(CommunityCollection.AllCommunities, event.id) {
                it.copy(status = event.status)
            }
            storage.community.getDocument(CommunityCollection.AllCommunities, event.id)?.let {
                storage.community.saveToDefault(it.copy(status = event.status))
            }
        }

        private suspend fun handleRoomReadOnlyUpdated(
            storage: ModelStorage,
            event: OnRoomStatusUpdated
        ) {
            storage.room.update(RoomCollection.AllRooms(false), event.id) {
                it.copy(status = event.status)
            }
            storage.room.update(RoomCollection.AllRooms(true), event.id) {
                it.copy(status = event.status)
            }
            val room = storage.room.getDocument(RoomCollection.AllRooms(false), event.id)
                ?: storage.room.getDocument(RoomCollection.AllRooms(true), event.id)
            if (room != null) {
                storage.room.saveToDefault(room.copy(status = event.status))
            }
        }

        private suspend fun handleTopicReadOnlyUpdated(
            storage: ModelStorage,
            event: OnTopicStatusUpdated
        ) {
            storage.topic.update(TopicCollection.AllTopics, event.id) {
                it.copy(status = event.status)
            }
            storage.topic.getDocument(TopicCollection.AllTopics, event.id)?.let {
                storage.topic.saveToDefault(it.copy(status = event.status))
            }
        }

        private suspend fun handleTitleReadOnlyUpdated(
            storage: ModelStorage,
            event: OnTitleStatusUpdated
        ) {
            storage.title.update(TitleCollection.AllTitles, event.id) {
                it.copy(status = event.status)
            }
            storage.title.getDocument(TitleCollection.AllTitles, event.id)?.let {
                storage.title.saveToDefault(it.copy(status = event.status))
            }
        }

        private suspend fun handleFileReadOnlyUpdated(
            storage: ModelStorage,
            event: OnFileStatusUpdated
        ) {
            storage.fileInfo.update(FileCollection.FileList(0), event.id) {
                it.copy(status = event.status)
            }
            storage.fileInfo.getDocument(FileCollection.FileList(0), event.id)?.let {
                storage.fileInfo.update(FileCollection.FileList(it.owner), event.id) { fileInfo ->
                    fileInfo.copy(status = event.status)
                }
                storage.fileInfo.saveToDefault(it.copy(status = event.status))
            }
        }
    }
}

fun createPanelSessionManager(
    httpUrl: String,
    passHolder: ConstPassHolder,
    cookiesStorage: AcceptAllCookiesStorage
): CustomPanelSessionManager {
    val simpleManager = createSimplePanelSessionManager(
        passHolder,
        cookiesStorage
    ) { m, c ->
        getClient {
            defaultClientConfigureForPanel(c, m, passHolder, httpUrl)
        }
    }
    val settings = createSettings("main")
    val historyManager = buildSessionHistoryFactory(settings)
    return CustomPanelSessionManager(simpleManager, historyManager)
}

class PanelUIViewModel(val viewModelScope: CoroutineScope, val httpUrl: String) {
    val instance: MutableStateFlow<IPanelAccountInstance>

    init {
        val value = IPanelAccountInstance.None(viewModelScope, httpUrl)
        instance = MutableStateFlow(value)
        val settings = createSettings("main")
        val state = restoreFromStorage(settings)
        if (state is ClientSessionState.Success) {
            viewModelScope.launch {
                val address = state.userPass.address().getOrNull() ?: return@launch
                val regular = IPanelAccountInstance.Regular(
                    viewModelScope,
                    address,
                    httpUrl,
                    ConstPassHolder(state.userPass),
                    AcceptAllCookiesStorage()
                )
                instance.value = regular
            }
        }
    }

    fun logout() {
        instance.value = IPanelAccountInstance.None(viewModelScope, httpUrl)
    }

    fun login(it: SignResult<PanelAccountInfo>, pass: UserPass) {
        val old = instance.value.sessionManager.proxy.cookieManager
        instance.value = IPanelAccountInstance.Regular(
            viewModelScope, it.address, httpUrl,
            ConstPassHolder(pass), old
        )
    }
}
