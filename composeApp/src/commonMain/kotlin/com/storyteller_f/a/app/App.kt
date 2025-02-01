package com.storyteller_f.a.app

import a.composeapp.generated.resources.Res
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade
import coil3.util.DebugLogger
import com.dokar.sonner.Toaster
import com.dokar.sonner.ToasterState
import com.dokar.sonner.rememberToasterState
import com.mikepenz.aboutlibraries.ui.compose.m3.*
import com.storyteller_f.a.app.common.getOrCreateCollection
import com.storyteller_f.a.app.compontents.DialogState.Text
import com.storyteller_f.a.app.compontents.GlobalDialog
import com.storyteller_f.a.app.compontents.GlobalDialogController
import com.storyteller_f.a.app.pages.community.CommunityPage
import com.storyteller_f.a.app.pages.media.MediaPage
import com.storyteller_f.a.app.pages.room.RoomPage
import com.storyteller_f.a.app.pages.topic.TopicComposePage
import com.storyteller_f.a.app.pages.topic.TopicPage
import com.storyteller_f.a.app.pages.topic.processEncryptedTopic
import com.storyteller_f.a.app.pages.user.MemberPage
import com.storyteller_f.a.app.pages.user.UserPage
import com.storyteller_f.a.app.pages.user.UserSettingPage
import com.storyteller_f.a.app.pages.user.signOut
import com.storyteller_f.a.app.ui.theme.AppTheme
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.a.client_lib.addRequestHeaders
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.buildUrl
import io.ktor.http.path
import io.ktor.http.takeFrom
import kotbase.MutableDocument
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi

object StaticObj {
    init {
        Napier.base(DebugAntilog())
    }
}

val globalDialogState = GlobalDialogController()

val LocalAppNav = compositionLocalOf {
    AppNav.EMPTY
}

val LocalClient = compositionLocalOf {
    HttpClient()
}

val LocalWsClient = compositionLocalOf {
    ClientWebSocket({
        error("")
    }) {
    }
}

@OptIn(DelicateCoroutinesApi::class)
val LocalToaster = compositionLocalOf {
    ToasterState(GlobalScope)
}

@Serializable
data object HomeScreen

@Serializable
data class CommunityScreen(val communityId: PrimaryKey, val showDialog: Boolean)

@Serializable
data class RoomScreen(val roomId: PrimaryKey, val showDialog: Boolean)

@Serializable
data object LoginScreen

@Serializable
data class TopicScreen(val topicId: PrimaryKey)

@Serializable
data object AboutScreen

@Serializable
data class UserScreen(val uid: PrimaryKey)

@Serializable
data class TopicComposeScreen(
    val objectType: String,
    val objectId: PrimaryKey,
    val enableExperimental: Boolean,
    val privateRoomId: PrimaryKey?
)

@Serializable
data class MemberScreen(val objectType: String, val objectId: PrimaryKey)

@Serializable
data object UserSettingScreen

@Serializable
data class MediaScreen(val url: String)

@Composable
fun App() {
    val httpUrl = AppConfig.SERVER_URL
    val wsServerUrl = AppConfig.WS_SERVER_URL
    AppInternal(httpUrl, wsServerUrl)
}

@Composable
fun AppInternal(httpUrl: String, wsServerUrl: String) {
    StaticObj
    AppTheme(dynamicColor = true) {
        setSingletonImageLoaderFactory {
            getAsyncImageLoader(it)
        }
        val navigator = rememberNavController()
        App2(navigator, httpUrl, wsServerUrl) {
            NavHost(navigator, startDestination = HomeScreen) {
                buildRootNav(navigator)
            }
        }
    }
}

@Composable
fun App2(navigator: NavHostController, httpUrl: String, wsServerUrl: String, content: @Composable () -> Unit) {
    val appNav = remember {
        newAppNav(navigator)
    }

    CompositionLocalProvider(LocalAppNav provides appNav) {
        val client = getClient {
            defaultClientConfigure()
            setupRequest(httpUrl)
        }
        CompositionLocalProvider(LocalClient provides client) {
            val ws = buildWsClient(client, wsServerUrl)
            CompositionLocalProvider(LocalWsClient provides ws) {
                GlobalDialog(globalDialogState)
                val toasterState = rememberToasterState()
                Toaster(toasterState, alignment = Alignment.Center)
                CompositionLocalProvider(LocalToaster provides toasterState) {
                    LoginCheck {
                        content()
                    }
                }
            }
        }
    }
}

private fun buildWsClient(
    client: HttpClient,
    wsServerUrl: String
): ClientWebSocket = ClientWebSocket({
    client.webSocketSession(buildUrl {
        takeFrom(wsServerUrl)
        path("link")
    }.toString()) {
        addRequestHeaders(LoginViewModel.session?.first)
    }
}) {
    if (it is RoomFrame.NewTopicInfo) {
        val info = processEncryptedTopic(listOf(it.topicInfo)).first()
        updateDocumentInParent(info)
        Napier.v(tag = "pagination") {
            "save document $info"
        }
    }
}

@Composable
fun LoginCheck(content: @Composable () -> Unit) {
    val client = LocalClient.current
    val state by LoginViewModel.state.collectAsState(false)
    val user by LoginViewModel.user.collectAsState()
    val signUpSuccessSession = state
    var tried by remember {
        mutableStateOf(false)
    }
    LaunchedEffect(signUpSuccessSession, tried) {
        if (!tried) {
            if (signUpSuccessSession is ClientSession.SignUpSuccess) {
                LoginViewModel.state
                signUpOrSignIn(signUpSuccessSession.privateKey, client, false, {
                    tried = true
                }) {
                    tried = true
                }
            }
        }
    }
    val scope = rememberCoroutineScope()
    if (signUpSuccessSession is ClientSession.SignUpSuccess && user == null) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (tried) {
                Column {
                    Button({
                        scope.launch {
                            signOut(client)
                        }
                    }) {
                        Text("Sign out")
                    }
                    Button({
                        tried = false
                    }) {
                        Text("Retry")
                    }
                }

            } else {
                CircularProgressIndicator(modifier = Modifier.size(40.dp))
            }
        }
    } else {
        content()
    }
}

@OptIn(ExperimentalResourceApi::class)
private fun NavGraphBuilder.buildRootNav(
    navigator: NavHostController
) {
    composable<HomeScreen> {
        HomePage()
    }
    composable<LoginScreen> {
        LoginPage()
    }
    composable<CommunityScreen> {
        val screen = it.toRoute<CommunityScreen>()
        CommunityPage(screen.communityId, screen.showDialog)
    }
    composable<RoomScreen> {
        val screen = it.toRoute<RoomScreen>()
        RoomPage(screen.roomId, screen.showDialog)
    }
    composable<TopicScreen> {
        TopicPage(it.toRoute<TopicScreen>().topicId)
    }
    composable<TopicComposeScreen> {
        val (objectType, objectId, enableExperimental, privateRoomId) = it.toRoute<TopicComposeScreen>()
        TopicComposePage(ObjectType.valueOf(objectType), objectId, enableExperimental, privateRoomId) {
            navigator.popBackStack()
        }
    }
    composable<MemberScreen> {
        val (objectType, objectId) = it.toRoute<MemberScreen>()
        MemberPage(objectId, ObjectType.valueOf(objectType))
    }
    composable<AboutScreen> {
        val libraries by rememberLibraries {
            Res.readBytes("files/aboutlibraries.json").decodeToString()
        }
        Surface {
            LibrariesContainer(
                libraries,
                Modifier.fillMaxSize().statusBarsPadding(),
                colors = LibraryDefaults.libraryColors(backgroundColor = MaterialTheme.colorScheme.background)
            )
        }
    }
    composable<UserScreen> {
        val route = it.toRoute<UserScreen>()
        UserPage(route.uid)
    }
    composable<UserSettingScreen> {
        UserSettingPage()
    }
    composable<MediaScreen> {
        val route = it.toRoute<MediaScreen>()
        MediaPage(route.url)
    }
}

private fun newAppNav(navigator: NavHostController) = object : AppNav {
    override val currentDestination: NavBackStackEntry?
        get() = navigator.currentBackStackEntry

    override fun gotoLogin() {
        navigator.navigate(route = LoginScreen)
    }

    override fun gotoRoom(roomId: PrimaryKey, showDialog: Boolean) {
        navigator.navigate(route = RoomScreen(roomId, showDialog))
    }

    override fun gotoCommunity(communityId: PrimaryKey, showDialog: Boolean) {
        navigator.navigate(route = CommunityScreen(communityId, showDialog))
    }

    override fun gotoTopic(topicId: PrimaryKey) {
        navigator.navigate(route = TopicScreen(topicId))
    }

    override fun gotoHome() {
        navigator.popBackStack(HomeScreen, false)
    }

    override fun gotoTopicCompose(
        objectType: ObjectType,
        objectId: PrimaryKey,
        enableExperimental: Boolean,
        privateRoomId: PrimaryKey?
    ) {
        navigator.navigate(route = TopicComposeScreen(objectType.name, objectId, enableExperimental, privateRoomId))
    }

    override fun gotoMemberPage(
        objectId: PrimaryKey,
        objectType: ObjectType
    ) {
        navigator.navigate(route = MemberScreen(objectType.name, objectId))
    }

    override fun gotoAbout() {
        navigator.navigate(AboutScreen)
    }

    override fun gotoUser(uid: PrimaryKey) {
        navigator.navigate(UserScreen(uid))
    }

    override fun back() {
        navigator.popBackStack()
    }

    override fun gotoUserSetting() {
        navigator.navigate(UserSettingScreen)
    }

    override fun gotoMedia(info: MediaInfo) {
        navigator.navigate(MediaScreen(info.url))
    }
}

fun getAsyncImageLoader(context: PlatformContext) =
    ImageLoader.Builder(context).crossfade(true).logger(DebugLogger()).build()

fun updateDocumentInParent(info: TopicInfo) {
    val collectionName = "topics${info.parentId}"
    updateDocument(collectionName, info)
}

fun updateDocument(collectionName: String, info: TopicInfo) {
    getOrCreateCollection(collectionName).save(
        MutableDocument(
            info.id.toString(),
            Json.encodeToString(info)
        )
    )
}

fun HttpClientConfig<*>.setupRequest(httpUrl: String) {
    defaultRequest {
        url(httpUrl)
    }
}

val bus = MutableSharedFlow<Any>()

interface AppNav {
    val currentDestination: NavBackStackEntry?
    fun gotoLogin()

    fun gotoRoom(roomId: PrimaryKey, showDialog: Boolean)

    fun gotoCommunity(communityId: PrimaryKey, showDialog: Boolean)

    fun gotoTopic(topicId: PrimaryKey)

    fun gotoHome()

    fun gotoTopicCompose(
        objectType: ObjectType,
        objectId: PrimaryKey,
        enableExperimental: Boolean,
        privateRoomId: PrimaryKey?
    )

    fun gotoMemberPage(objectId: PrimaryKey, objectType: ObjectType)

    fun gotoAbout()

    fun gotoUser(uid: PrimaryKey)

    fun back()

    fun gotoUserSetting()

    fun gotoMedia(info: MediaInfo)

    companion object {
        val EMPTY = object : AppNav {
            override val currentDestination: NavBackStackEntry
                get() = TODO("Not yet implemented")

            override fun gotoLogin() {
                TODO("Not yet implemented")
            }

            override fun gotoRoom(roomId: PrimaryKey, showDialog: Boolean) {
                TODO("Not yet implemented")
            }

            override fun gotoCommunity(communityId: PrimaryKey, showDialog: Boolean) {
                TODO("Not yet implemented")
            }

            override fun gotoTopic(topicId: PrimaryKey) {
                TODO("Not yet implemented")
            }

            override fun gotoHome() {
                TODO("Not yet implemented")
            }

            override fun gotoTopicCompose(
                objectType: ObjectType,
                objectId: PrimaryKey,
                enableExperimental: Boolean,
                privateRoomId: PrimaryKey?
            ) {
                TODO("Not yet implemented")
            }

            override fun gotoMemberPage(
                objectId: PrimaryKey,
                objectType: ObjectType
            ) {
                TODO("Not yet implemented")
            }

            override fun gotoAbout() {
                TODO("Not yet implemented")
            }

            override fun gotoUser(uid: PrimaryKey) {
                TODO("Not yet implemented")
            }

            override fun back() {
                TODO("Not yet implemented")
            }

            override fun gotoUserSetting() {
                TODO("Not yet implemented")
            }

            override fun gotoMedia(info: MediaInfo) {
                TODO("Not yet implemented")
            }
        }
    }
}
