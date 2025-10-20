package org.storyteller_f.a.cloud.panel

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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.russhwolf.settings.Settings
import com.storyteller_f.a.app.core.PanelConfig
import com.storyteller_f.a.app.core.common.LocalClient
import com.storyteller_f.a.app.core.compontents.CenterBox
import com.storyteller_f.a.app.core.compontents.LoginButton
import com.storyteller_f.a.app.core.compontents.PrivateKeyInput
import com.storyteller_f.a.app.core.utils.buildLoginHistoryFactory
import com.storyteller_f.a.app.core.utils.createSettings
import com.storyteller_f.a.app.core.utils.restoreFromStorage
import com.storyteller_f.a.client.core.ClientSessionState
import com.storyteller_f.a.client.core.PanelSessionManager
import com.storyteller_f.a.client.core.PanelSessionModel
import com.storyteller_f.a.client.core.createPanelSessionManager
import com.storyteller_f.a.client.core.defaultClientConfigureForPanel
import com.storyteller_f.a.client.core.getClient
import com.storyteller_f.a.client.core.getPanelAccountInfo
import com.storyteller_f.a.client.core.startBackgroundTask
import com.storyteller_f.a.client.room.RoomModelStorage
import com.storyteller_f.a.client.room.getRoomDatabase
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.readBytes
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.CookiesStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.storyteller_f.a.cloud.panel.pages.AllUsersPage
import org.storyteller_f.a.cloud.panel.pages.OverviewPage

@OptIn(DelicateCoroutinesApi::class)
val panelAccountInstance = PanelAccountInstance(GlobalScope)

interface PanelNav {
    fun gotoLogin()
    fun gotoOverview()
    fun gotoAllUsers()
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
}

val LocalNav = compositionLocalOf<PanelNav> { error("no nav") }

@Composable
fun App() {
    val sessionManager = panelAccountInstance.sessionManager
    val client = sessionManager.client
    val navigator = rememberNavController()
    val nav = remember { Nav2PanelNav(navigator) }
    CompositionLocalProvider(
        LocalClient provides client,
        LocalNav provides nav
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
                composable("overview") {
                    PanelHost {
                        OverviewPage()
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelHost(content: @Composable () -> Unit) {
    val nav = LocalNav.current
    val session = panelAccountInstance.sessionManager
    val user by session.isAlreadySignIn.collectAsState()
    if (user) {
        content()
    } else {
        CenterBox {
            LoginButton {
                nav.gotoLogin()
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
    val sessionManager = panelAccountInstance.sessionManager
    CenterBox {
        val scope = rememberCoroutineScope()
        var privateKey by remember { mutableStateOf("") }
        val startSign: () -> Unit = {
            scope.launch {
                try {
                    sessionManager.getPanelAccountInfo(
                        privateKey,
                        false
                    ) {
                        buildLoginHistoryFactory(sessionManager.settings).addSession(it)
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
    val panelSessionManager = panelAccountInstance.sessionManager
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
                                panelSessionManager.getPanelAccountInfo(
                                    privateKey,
                                    false
                                ) {
                                    buildLoginHistoryFactory(panelSessionManager.settings).addSession(
                                        it
                                    )
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

    init {
        scope.launch {
            sessionManager.startBackgroundTask()
        }
    }
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
