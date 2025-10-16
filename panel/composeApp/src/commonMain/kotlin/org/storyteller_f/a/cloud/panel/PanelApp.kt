package org.storyteller_f.a.cloud.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.russhwolf.settings.Settings
import com.storyteller_f.a.api.core.PaginationQuery
import com.storyteller_f.a.app.core.PanelConfig
import com.storyteller_f.a.app.core.common.CompatPagingSource
import com.storyteller_f.a.app.core.common.CustomRemoteMediator
import com.storyteller_f.a.app.core.common.IntKeyConverter
import com.storyteller_f.a.app.core.common.LocalClient
import com.storyteller_f.a.app.core.common.PagingViewModel
import com.storyteller_f.a.app.core.common.RegularPagingSource
import com.storyteller_f.a.app.core.compontents.CenterBox
import com.storyteller_f.a.app.core.compontents.PrivateKeyInput
import com.storyteller_f.a.app.core.compontents.StateView
import com.storyteller_f.a.app.core.compontents.UserIconInternal
import com.storyteller_f.a.app.core.compontents.pagingItems
import com.storyteller_f.a.app.core.utils.createSettings
import com.storyteller_f.a.app.core.utils.restoreFromStorage
import com.storyteller_f.a.client.core.ClientSessionState
import com.storyteller_f.a.client.core.PanelSessionManager
import com.storyteller_f.a.client.core.PanelSessionModel
import com.storyteller_f.a.client.core.RawUserPass
import com.storyteller_f.a.client.core.createPanelSessionManager
import com.storyteller_f.a.client.core.defaultClientConfigureForPanel
import com.storyteller_f.a.client.core.getAllUsers
import com.storyteller_f.a.client.core.getClient
import com.storyteller_f.a.client.core.getPanelAccountInfo
import com.storyteller_f.a.client.room.RoomModelStorage
import com.storyteller_f.a.client.room.getRoomDatabase
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.storage.ModelStorage
import com.storyteller_f.storage.UserCollection
import com.storyteller_f.storage.getName
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.readBytes
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.CookiesStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(DelicateCoroutinesApi::class)
val panelAccountInstance = PanelAccountInstance(GlobalScope)

@Composable
fun App() {
    val sessionManager = panelAccountInstance.sessionManager
    val client = sessionManager.client
    CompositionLocalProvider(LocalClient provides client) {
        MaterialTheme {
            val navigator = rememberNavController()
            NavHost(navigator, "login") {
                composable("login") {
                    PanelLoginPage {
                        navigator.popBackStack()
                        navigator.navigate("all-users")
                    }
                }
                composable("all-users") {
                    AllUsersPage()
                }
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
    CenterBox {
        val scope = rememberCoroutineScope()
        var privateKey by remember { mutableStateOf("") }
        val startSign: () -> Unit = {
            scope.launch {
                try {
                    panelAccountInstance.sessionManager.getPanelAccountInfo(
                        privateKey,
                        false
                    ) {
                        RawUserPass(it)
                    }
                    back()
                } catch (e: Exception) {
                    e.printStackTrace()
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
                OutlinedButton({
                    scope.launch {
                        try {
                            val f = FileKit.openFilePicker()
                            if (f != null) {
                                val privateKey = String(f.readBytes()).replace("\r\n", "\n")
                                panelAccountInstance.sessionManager.getPanelAccountInfo(
                                    privateKey,
                                    false
                                ) {
                                    RawUserPass(it)
                                }
                                back()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }) {
                    Text("Select File")
                }
            }
        }
    }
}

class AllUsersViewModel(
    sessionManager: PanelSessionManager,
    modelStorage: ModelStorage,
) : PagingViewModel<UserInfo>() {
    val modelCollection = UserCollection.AllUsers

    @OptIn(ExperimentalPagingApi::class)
    override val flow: Flow<PagingData<UserInfo>> = Pager(
        PagingConfig(pageSize = 20),
        remoteMediator = CustomRemoteMediator(
            modelStorage,
            modelCollection.getName(),
            RegularPagingSource { key, size ->
                sessionManager.getAllUsers(PaginationQuery(key, size = size))
            },
        ) { data, clean ->
            if (clean) {
                modelStorage.userInfoStorage.clean(modelCollection)
            }
            data.forEach {
                modelStorage.userInfoStorage.save(modelCollection, it)
            }
        },
    ) {
        CompatPagingSource(
            modelStorage.userInfoStorage.observeData(modelCollection),
            IntKeyConverter
        )
    }.flow.cachedIn(viewModelScope)
}

@Composable
fun AllUsersPage() {
    val modelStorage by panelAccountInstance.database.collectAsState()
    val viewModel = viewModel {
        AllUsersViewModel(panelAccountInstance.sessionManager, modelStorage)
    }
    AllUsersPageInternal(viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllUsersPageInternal(viewModel: AllUsersViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text("Home")
            })
        }
    ) {
        Box(Modifier.padding(top = it.calculateTopPadding())) {
            StateView(viewModel) { items ->
                LazyColumn {
                    pagingItems(items, key = {
                        it.id
                    }) {
                        UserCell(items.get(it))
                    }
                }
            }
        }
    }
}

@Composable
fun UserCell(userInfo: UserInfo?) {
    Row(
        modifier = Modifier.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UserIconInternal(
            isMe = false,
            setClickEvent = true,
            avatarUrl = userInfo?.avatar?.url,
        ) {
        }
        if (userInfo != null) {
            Column {
                Text(userInfo.nickname, style = MaterialTheme.typography.titleMedium)
                val aid = userInfo.aid
                if (aid != null) {
                    Text("aid: $aid", style = MaterialTheme.typography.labelSmall)
                } else {
                    Text("ad: ${userInfo.address}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
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
    val guestDatabase = RoomModelStorage(getRoomDatabase("guest"))
    val database = sessionManager.model.state.distinctUntilChangedBy {
        it
    }.map {
        if (it is ClientSessionState.Success) {
            val address = it.session.address().getOrThrow()
            RoomModelStorage(getRoomDatabase(address))
        } else {
            guestDatabase
        }
    }.stateIn(scope, SharingStarted.Eagerly, guestDatabase)
}

class CustomPanelSessionManager(
    val proxy: PanelSessionManager,
    val settings: Settings,
) : PanelSessionManager by proxy

fun createCustomPanelSessionManager(
    settingsName: String,
    createClient: (PanelSessionModel, CookiesStorage) -> HttpClient,
): CustomPanelSessionManager {
    val settings = createSettings(settingsName)
    val customSessionManager = createPanelSessionManager(createClient)
    customSessionManager.restoreFromStorage(settings)
    return CustomPanelSessionManager(customSessionManager, settings)
}
