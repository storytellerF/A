package com.storyteller_f.a.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
import com.storyteller_f.a.panel.pages.OverviewPage
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

@OptIn(DelicateCoroutinesApi::class)
val panelAccountInstance = PanelAccountInstance(GlobalScope)

interface PanelNav {
    fun gotoLogin()
    fun gotoOverview()
    fun gotoAllUsers()
    fun gotoAllCommunities()
    fun gotoAllPublicRooms()
    fun gotoAllPrivateRooms()
    fun gotoAllTopics()
    fun gotoAllFiles()
    fun gotoAllTitles()
}

class Nav2PanelNav(val navigator: NavHostController) : PanelNav {
    override fun gotoLogin() {
        navigator.navigate("login")
    }

    override fun gotoOverview() {
        navigator.navigate("overview")
    }

    override fun gotoAllUsers() {
        navigator.navigate("all-users")
    }

    override fun gotoAllCommunities() {
        navigator.navigate("all-communities")
    }

    override fun gotoAllPublicRooms() {
        navigator.navigate("all-rooms-public")
    }

    override fun gotoAllPrivateRooms() {
        navigator.navigate("all-rooms-private")
    }

    override fun gotoAllTopics() {
        navigator.navigate("all-topics")
    }

    override fun gotoAllFiles() {
        navigator.navigate("all-files")
    }

    override fun gotoAllTitles() {
        navigator.navigate("all-titles")
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

@Composable
fun App() {
    val sessionManager = panelAccountInstance.sessionManager
    val client = sessionManager.client
    val navigator = rememberNavController()
    val nav = remember { Nav2PanelNav(navigator) }
    val controller = panelAccountInstance.controller
    val task = panelAccountInstance.task

    CompositionLocalProvider(
        LocalClient provides client,
        LocalPanelNav provides nav,
        LocalPanelGlobalDialog provides controller,
        LocalPanelGlobalTask provides task
    ) {
        MaterialTheme {
            NavHost(navigator, "overview") {
                composable("login") {
                    PanelLoginPage {
                        navigator.popBackStack()
                        nav.gotoOverview()
                    }
                }
                composable("all-users") {
                    AllUsersPage()
                }
                composable("all-communities") {
                    AllCommunitiesPage()
                }
                composable("all-rooms-public") {
                    AllPublicRoomsPage()
                }
                composable("all-rooms-private") {
                    AllPrivateRoomsPage()
                }
                composable("all-topics") {
                    AllTopicsPage()
                }
                composable("all-files") {
                    AllFilesPage()
                }
                composable("all-titles") {
                    AllTitlesPage()
                }
                composable("overview") {
                    PanelHost {
                        OverviewPage()
                    }
                }
            }
        }
        GlobalDialog(controller)
    }
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
        Box(Modifier.padding(top = it.calculateTopPadding())) {
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
                    Text("Start")
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
                "Sign In",
                style = MaterialTheme.typography.headlineMedium
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedButton({
                    navigator.navigate("input")
                }, shape = ButtonDefaults.outlinedShape) {
                    Text("Input")
                }
                val scope = rememberCoroutineScope()
                val globalDialogController = LocalPanelGlobalDialog.current
                OutlinedButton({
                    scope.launch {
                        globalDialogController.signInFromFile(back)
                    }
                }) {
                    Text("Select File")
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
