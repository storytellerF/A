package com.storyteller_f.a.panel

import PanelFilePreviewPage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.storyteller_f.a.app.core.CoreStrings
import com.storyteller_f.a.app.core.PanelConfig
import com.storyteller_f.a.app.core.common.LocalClient
import com.storyteller_f.a.app.core.components.CenterBox
import com.storyteller_f.a.app.core.components.CustomGlobalDialogController
import com.storyteller_f.a.app.core.components.CustomGlobalTask
import com.storyteller_f.a.app.core.components.GlobalDialog
import com.storyteller_f.a.app.core.components.GlobalDialogContext
import com.storyteller_f.a.app.core.components.GlobalDialogController
import com.storyteller_f.a.app.core.components.GlobalDialogState
import com.storyteller_f.a.app.core.components.GlobalTask
import com.storyteller_f.a.app.core.components.GlobalTaskContext
import com.storyteller_f.a.app.core.components.PrivateKeyInput
import com.storyteller_f.a.app.core.components.SignInButton
import com.storyteller_f.a.app.core.components.request
import com.storyteller_f.a.app.core.components.safeArea
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
import com.storyteller_f.a.client.core.getPanelAccountInfo
import com.storyteller_f.a.client.core.startBackgroundTask
import com.storyteller_f.a.client.room.RoomModelStorage
import com.storyteller_f.a.client.room.getRoomModelStorage
import com.storyteller_f.a.panel.common.OnUserAdded
import com.storyteller_f.a.panel.pages.AllCommunitiesPage
import com.storyteller_f.a.panel.pages.AllFilesPage
import com.storyteller_f.a.panel.pages.AllPrivateRoomsPage
import com.storyteller_f.a.panel.pages.AllPublicRoomsPage
import com.storyteller_f.a.panel.pages.AllTitlesPage
import com.storyteller_f.a.panel.pages.AllTopicsPage
import com.storyteller_f.a.panel.pages.AllUsersPage
import com.storyteller_f.a.panel.pages.CommunityDetailPage
import com.storyteller_f.a.panel.pages.FileDetailPage
import com.storyteller_f.a.panel.pages.OverviewPage
import com.storyteller_f.a.panel.pages.RoomDetailPage
import com.storyteller_f.a.panel.pages.TitleDetailPage
import com.storyteller_f.a.panel.pages.TopicDetailPage
import com.storyteller_f.a.panel.pages.UserDetailPage
import com.storyteller_f.shared.replaceCrlf
import com.storyteller_f.storage.UserCollection
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.readBytes
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource

@OptIn(DelicateCoroutinesApi::class)
val panelAccountInstance = PanelAccountInstance(GlobalScope)

interface PanelNav {
    val drawerState: DrawerState
    fun gotoLogin()
    fun gotoOverview()
    fun gotoAllUsers()
    fun gotoUserDetail(uid: Long)
    fun gotoAllCommunities()
    fun gotoCommunityDetail(id: Long)
    fun gotoAllPublicRooms()
    fun gotoRoomDetail(id: Long)
    fun gotoAllPrivateRooms()
    fun gotoAllTopics()
    fun gotoTopicDetail(id: Long)
    fun gotoAllFiles()
    fun gotoFileDetail(id: Long)
    fun gotoAllTitles()
    fun gotoTitleDetail(id: Long)
    fun gotoFilePreview(id: Long, url: String, contentType: String, name: String)
    fun open()
}

class Nav2PanelNav(
    val navigator: NavHostController,
    override val drawerState: DrawerState,
    private val scope: CoroutineScope
) : PanelNav {
    override fun gotoLogin() {
        navigator.navigate(PanelLoginScreen)
    }

    override fun gotoOverview() {
        navigator.navigate(PanelOverviewScreen)
    }

    override fun gotoAllUsers() {
        navigator.navigate(PanelAllUsersScreen)
    }

    override fun gotoUserDetail(uid: Long) {
        navigator.navigate(PanelUserDetailScreen(uid))
    }

    override fun gotoAllCommunities() {
        navigator.navigate(PanelAllCommunitiesScreen)
    }

    override fun gotoCommunityDetail(id: Long) {
        navigator.navigate(PanelCommunityDetailScreen(id))
    }

    override fun gotoAllPublicRooms() {
        navigator.navigate(PanelAllPublicRoomsScreen)
    }

    override fun gotoRoomDetail(id: Long) {
        navigator.navigate(PanelRoomDetailScreen(id))
    }

    override fun gotoAllPrivateRooms() {
        navigator.navigate(PanelAllPrivateRoomsScreen)
    }

    override fun gotoAllTopics() {
        navigator.navigate(PanelAllTopicsScreen)
    }

    override fun gotoTopicDetail(id: Long) {
        navigator.navigate(PanelTopicDetailScreen(id))
    }

    override fun gotoAllFiles() {
        navigator.navigate(PanelAllFilesScreen)
    }

    override fun gotoFileDetail(id: Long) {
        navigator.navigate(PanelFileDetailScreen(id))
    }

    override fun gotoAllTitles() {
        navigator.navigate(PanelAllTitlesScreen)
    }

    override fun gotoTitleDetail(id: Long) {
        navigator.navigate(PanelTitleDetailScreen(id))
    }

    override fun gotoFilePreview(id: Long, url: String, contentType: String, name: String) {
        navigator.navigate(PanelFilePreviewScreen(id))
    }

    override fun open() {
        scope.launch { drawerState.open() }
    }
}

val LocalPanelNav = compositionLocalOf<PanelNav> { error("no nav") }

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
    val navigator = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val nav = remember { Nav2PanelNav(navigator, drawerState, scope) }
    val controller = panelAccountInstance.controller
    val task = panelAccountInstance.task

    CompositionLocalProvider(
        LocalClient provides client,
        LocalPanelNav provides nav,
        LocalPanelGlobalDialog provides controller,
        LocalPanelGlobalTask provides task
    ) {
        MaterialTheme {
            val scope = rememberCoroutineScope()
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    PanelDrawer(scope, drawerState, nav)
                }
            ) {
                PanelAppNavHost(navigator, nav)
            }
        }
        GlobalDialog(controller)
    }
}

@Composable
private fun PanelAppNavHost(
    navigator: NavHostController,
    nav: Nav2PanelNav
) {
    NavHost(navigator, PanelOverviewScreen) {
        composable<PanelLoginScreen> {
            PanelLoginPage {
                navigator.popBackStack()
                nav.gotoOverview()
            }
        }
        composable<PanelOverviewScreen> {
            PanelHost {
                OverviewPage()
            }
        }
        composable<PanelAllUsersScreen> { AllUsersPage() }
        composable<PanelUserDetailScreen> {
            val screen = it.toRoute<PanelUserDetailScreen>()
            UserDetailPage(screen.uid)
        }
        composable<PanelCommunityDetailScreen> {
            val screen = it.toRoute<PanelCommunityDetailScreen>()
            CommunityDetailPage(screen.id)
        }
        composable<PanelRoomDetailScreen> {
            val screen = it.toRoute<PanelRoomDetailScreen>()
            RoomDetailPage(screen.id)
        }
        composable<PanelTopicDetailScreen> {
            val screen = it.toRoute<PanelTopicDetailScreen>()
            TopicDetailPage(screen.id)
        }
        composable<PanelFileDetailScreen> {
            val screen = it.toRoute<PanelFileDetailScreen>()
            FileDetailPage(screen.id)
        }
        composable<PanelFilePreviewScreen> {
            val screen = it.toRoute<PanelFilePreviewScreen>()
            PanelFilePreviewPage(screen.id)
        }
        composable<PanelTitleDetailScreen> {
            val screen = it.toRoute<PanelTitleDetailScreen>()
            TitleDetailPage(screen.id)
        }
        composable<PanelAllCommunitiesScreen> { AllCommunitiesPage() }
        composable<PanelAllPublicRoomsScreen> { AllPublicRoomsPage() }
        composable<PanelAllPrivateRoomsScreen> { AllPrivateRoomsPage() }
        composable<PanelAllTopicsScreen> { AllTopicsPage() }
        composable<PanelAllFilesScreen> { AllFilesPage() }
        composable<PanelAllTitlesScreen> { AllTitlesPage() }
    }
}

@Composable
private fun PanelDrawer(
    scope: CoroutineScope,
    drawerState: DrawerState,
    nav: Nav2PanelNav
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

@Composable
private fun PanelHost(content: @Composable () -> Unit) {
    val panelNav = LocalPanelNav.current
    val session = panelAccountInstance.sessionManager
    val user by session.isAlreadySignIn.collectAsState()
    if (user) {
        content()
    } else {
        CenterBox {
            SignInButton {
                panelNav.gotoLogin()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelLoginPage(back: () -> Unit) {
    val navigator = rememberNavController()
    Scaffold {
        val direction = LocalLayoutDirection.current
        Box(Modifier.safeArea(it, direction)) {
            NavHost(navigator, "select") {
                composable("select") {
                    PanelSelectLoginPage(navigator, back)
                }
                composable("input") {
                    PanelInputPage(back)
                }
            }
        }
    }
}

@Composable
private fun PanelInputPage(back: () -> Unit) {
    val dialogController = LocalPanelGlobalDialog.current
    CenterBox {
        val scope = rememberCoroutineScope()
        var privateKey by remember { mutableStateOf("") }
        val startSign: () -> Unit = {
            scope.launch {
                dialogController.useResult {
                    request {
                        runCatching {
                            getPanelAccountInfo(
                                privateKey,
                                false
                            ) {
                                historyFactory.addSession(it)
                            }
                        }
                    }
                }.onSuccess {
                    back()
                }
            }
        }
        Column(modifier = Modifier.padding(20.dp)) {
            PrivateKeyInput(privateKey, {
                privateKey = it
            }, startSign)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(startSign) {
                    Text(CoreStrings.start())
                }
            }
        }
    }
}

@Composable
private fun PanelSelectLoginPage(navigator: NavHostController, back: () -> Unit) {
    CenterBox {
        Column(
            verticalArrangement = Arrangement.spacedBy(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(Res.string.sign_in),
                style = MaterialTheme.typography.headlineMedium
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedButton({
                    navigator.navigate("input")
                }, shape = ButtonDefaults.outlinedShape) {
                    Text(stringResource(Res.string.input))
                }
                val scope = rememberCoroutineScope()
                val globalDialogController = LocalPanelGlobalDialog.current
                OutlinedButton({
                    scope.launch {
                        globalDialogController.signInFromFile(back)
                    }
                }) {
                    Text(CoreStrings.selectFile())
                }
            }
        }
    }
}

private suspend fun PanelGlobalDialogController.signInFromFile(
    back: () -> Unit
) {
    useResult {
        request {
            runCatching {
                val f = FileKit.openFilePicker()
                if (f != null) {
                    val privateKey = String(f.readBytes()).replaceCrlf()
                    getPanelAccountInfo(
                        privateKey,
                        false
                    ) {
                        historyFactory.addSession(it)
                    }
                }
            }
        }
    }.onSuccess {
        back()
    }
}

class PanelAccountInstance(scope: CoroutineScope) {
    val sessionManager = createCustomPanelSessionManager("default") { model, cookieManager ->
        getClient {
            defaultClientConfigureForPanel(
                cookieManager,
                manager = model,
                httpUrl = PanelConfig.SERVER_URL
            )
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
        storage: RoomModelStorage
    ) {
        when (any) {
            is OnUserAdded -> {
                storage.user.save(UserCollection.AllUsers, any.info)
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

@Serializable
data class PanelUserDetailScreen(val uid: Long)

@Serializable
data class PanelCommunityDetailScreen(val id: Long)

@Serializable
data class PanelRoomDetailScreen(val id: Long)

@Serializable
data class PanelTopicDetailScreen(val id: Long)

@Serializable
data class PanelFileDetailScreen(val id: Long)

@Serializable
data class PanelFilePreviewScreen(val id: Long)

@Serializable
data class PanelTitleDetailScreen(val id: Long)

@Serializable
object PanelLoginScreen

@Serializable
object PanelOverviewScreen

@Serializable
object PanelAllUsersScreen

@Serializable
object PanelAllCommunitiesScreen

@Serializable
object PanelAllPublicRoomsScreen

@Serializable
object PanelAllPrivateRoomsScreen

@Serializable
object PanelAllTopicsScreen

@Serializable
object PanelAllFilesScreen

@Serializable
object PanelAllTitlesScreen
