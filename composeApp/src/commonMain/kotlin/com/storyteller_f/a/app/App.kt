package com.storyteller_f.a.app

import a.composeapp.generated.resources.Res
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade
import coil3.util.DebugLogger
import com.dokar.sonner.Toaster
import com.dokar.sonner.ToasterState
import com.dokar.sonner.rememberToasterState
import com.kdroid.composenotification.builder.ExperimentalNotificationsApi
import com.kdroid.composenotification.builder.Notification
import com.kdroid.composenotification.builder.getNotificationProvider
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.m3.rememberLibraries
import com.storyteller_f.a.app.common.getOrCreateCollection
import com.storyteller_f.a.app.common.save
import com.storyteller_f.a.app.compontents.GlobalDialog
import com.storyteller_f.a.app.compontents.GlobalDialogController
import com.storyteller_f.a.app.pages.community.CommunityComposePage
import com.storyteller_f.a.app.pages.community.CommunityPage
import com.storyteller_f.a.app.pages.community.CommunitySettingPage
import com.storyteller_f.a.app.pages.media.MediaPage
import com.storyteller_f.a.app.pages.room.RoomComposePage
import com.storyteller_f.a.app.pages.room.RoomPage
import com.storyteller_f.a.app.pages.room.RoomSettingPage
import com.storyteller_f.a.app.pages.title.TitleComposePage
import com.storyteller_f.a.app.pages.topic.TopicComposePage
import com.storyteller_f.a.app.pages.topic.TopicPage
import com.storyteller_f.a.app.pages.user.MemberPage
import com.storyteller_f.a.app.pages.user.UserPage
import com.storyteller_f.a.app.pages.user.UserSettingPage
import com.storyteller_f.a.app.pages.user.signOut
import com.storyteller_f.a.app.ui.theme.AppTheme
import com.storyteller_f.a.app.utils.platform
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.finalData
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.reflect.KClass

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

@Serializable
data object TitleComposeScreen

@Serializable
data object CommunityComposeScreen

@Serializable
data object RoomComposeScreen

@Serializable
data class CommunitySettingScreen(val communityId: PrimaryKey)

@Serializable
data class RoomSettingScreen(val roomId: PrimaryKey)

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
        val appNav = remember<AppNav> {
            newAppNav(navigator)
        }
        CompositionLocalProvider(LocalAppNav provides appNav) {
            CommonEntry(httpUrl, wsServerUrl, {
                appNav.toRoute<RoomScreen>()?.roomId
            }, {
                appNav.toRoute<TopicScreen>()?.topicId
            }) {
                NavHost(navigator, startDestination = HomeScreen) {
                    buildRootNav(navigator)
                }
            }
        }
    }
}

@Composable
fun CommonEntry(
    httpUrl: String,
    wsServerUrl: String,
    roomScreenId: () -> PrimaryKey?,
    topicScreenId: () -> PrimaryKey?,
    content: @Composable () -> Unit
) {
    val client = remember {
        getClient {
            defaultClientConfigure()
            setupRequest(httpUrl)
        }
    }
    CompositionLocalProvider(LocalClient provides client) {
        val ws = rememberWsClient(client, wsServerUrl, roomScreenId, topicScreenId)
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

private fun buildWsListener(
    messageToasterState: ToasterState,
    hasPermission: Boolean,
    roomScreenId: () -> PrimaryKey?,
    topicScreenId: () -> PrimaryKey?,
) = object : ClientWsListener {
    override fun onReceived(frame: RoomFrame) {
        if (frame is RoomFrame.NewTopicInfo) {
            val topicInfo = frame.topicInfo
            val message = topicInfo.content
            if (message is TopicContent.Plain) {
                if (platform.isActive) {
                    val roomId = roomScreenId()
                    val topicId = topicScreenId()
                    if (roomId != topicInfo.parentId &&
                        topicId != topicInfo.parentId
                    ) {
                        val nickname = topicInfo.extension?.authorInfo?.nickname
                        messageToasterState.show("$nickname: ${message.plain}")
                    }
                } else if (hasPermission) {
                    sendTopicNotification(message)
                }
            }
        }
    }
}

@Composable
private fun rememberWsClient(
    client: HttpClient,
    wsServerUrl: String,
    roomScreenId: () -> PrimaryKey?,
    topicScreenId: () -> PrimaryKey?,
): ClientWebSocket {
    val remember = remember {
        ClientWebSocket({
            client.webSocketSession(buildUrl {
                takeFrom(wsServerUrl)
                appendPathSegments("link")
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
    }
    val messageToasterState = rememberToasterState()
    Toaster(messageToasterState, alignment = Alignment.TopCenter)
    val notificationProvider = getNotificationProvider()
    val hasPermission by notificationProvider.hasPermissionState
    val listener = remember(hasPermission) {
        buildWsListener(messageToasterState, hasPermission, roomScreenId, topicScreenId)
    }
    remember.addListener(listener)
    DisposableEffect(null) {
        onDispose {
            remember.removeListener(listener)
        }
    }
    return remember
}

@Composable
fun LoginCheck(content: @Composable () -> Unit) {
    val client = LocalClient.current
    val state by LoginViewModel.state.collectAsState()
    val user by LoginViewModel.user.collectAsState()
    val currentState = state
    var tried by remember {
        mutableStateOf(false)
    }
    LaunchedEffect(currentState, tried) {
        if (!tried && user == null) {
            if (currentState is ClientSession.SignUpSuccess) {
                globalDialogState.use {
                    val data = client.getData().getOrThrow()
                    val signature = currentState.session.signature(finalData(data))
                    val add = currentState.session.address()
                    val u = client.signIn(add, signature).getOrThrow()
                    LoginViewModel.updateUser(u)
                    LoginViewModel.updateSession(data, signature)
                }
                tried = true
            }
        }
    }
    val scope = rememberCoroutineScope()
    if (currentState is ClientSession.SignUpSuccess && user == null) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (tried) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
            }
        }
        Napier.i {
            "render waiting"
        }
    } else {
        Napier.i {
            "render content"
        }
        content()
    }
}

@OptIn(ExperimentalResourceApi::class)
private fun NavGraphBuilder.buildRootNav(
    navigator: NavHostController
) {
    buildMainScreen()
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
    composable<MediaScreen> {
        val route = it.toRoute<MediaScreen>()
        MediaPage(route.url)
    }
    buildComposeScreen(navigator)
}

private fun NavGraphBuilder.buildMainScreen() {
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

    composable<MemberScreen> {
        val (objectType, objectId) = it.toRoute<MemberScreen>()
        MemberPage(objectId, ObjectType.valueOf(objectType))
    }
    composable<UserScreen> {
        val route = it.toRoute<UserScreen>()
        UserPage(route.uid)
    }
    composable<UserSettingScreen> {
        UserSettingPage()
    }
    composable<CommunitySettingScreen> {
        val communityId = it.toRoute<CommunitySettingScreen>().communityId
        CommunitySettingPage(communityId)
    }
    composable<RoomSettingScreen> {
        val roomId = it.toRoute<RoomSettingScreen>().roomId
        RoomSettingPage(roomId)
    }
}

private fun NavGraphBuilder.buildComposeScreen(navigator: NavHostController) {
    composable<TitleComposeScreen> {
        TitleComposePage()
    }
    composable<CommunityComposeScreen> {
        CommunityComposePage()
    }
    composable<RoomComposeScreen> {
        RoomComposePage()
    }
    composable<TopicComposeScreen> {
        val (objectType, objectId, enableExperimental, privateRoomId) = it.toRoute<TopicComposeScreen>()
        TopicComposePage(ObjectType.valueOf(objectType), objectId, enableExperimental, privateRoomId) {
            navigator.popBackStack()
        }
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
        navigator.navigate(TopicComposeScreen(objectType.name, objectId, enableExperimental, privateRoomId))
    }

    override fun gotoMemberPage(
        objectId: PrimaryKey,
        objectType: ObjectType
    ) {
        navigator.navigate(MemberScreen(objectType.name, objectId))
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

    override fun gotoTitleCompose() {
        navigator.navigate(TitleComposeScreen)
    }

    override fun gotoCommunityCompose() {
        navigator.navigate(CommunityComposeScreen)
    }

    override fun gotoRoomCompose() {
        navigator.navigate(RoomComposeScreen)
    }

    override fun gotoCommunitySetting(communityId: PrimaryKey) {
        navigator.navigate(CommunitySettingScreen(communityId))
    }

    override fun gotoRoomSetting(roomId: PrimaryKey) {
        navigator.navigate(RoomSettingScreen(roomId))
    }
}

fun getAsyncImageLoader(context: PlatformContext) =
    ImageLoader.Builder(context).crossfade(true).logger(DebugLogger()).build()

fun updateDocumentInParent(info: TopicInfo) {
    val collectionName = "topics_${info.parentId}"
    updateDocument(collectionName, info)
}

fun updateDocument(collectionName: String, info: TopicInfo) {
    assert(!info.isPrivate || info.content is TopicContent.Encrypted)
    getOrCreateCollection(collectionName).save(info.id, Json.encodeToString(info))
}

fun HttpClientConfig<*>.setupRequest(httpUrl: String) {
    defaultRequest {
        url(httpUrl)
    }
}

val bus = MutableSharedFlow<Any>()

inline fun <reified T : Any> AppNav.toRoute(): T? {
    if (!hasRoute(T::class)) return null
    return currentDestination?.toRoute<T>()
}

interface AppNav {
    val currentDestination: NavBackStackEntry?
    fun <T : Any> hasRoute(any: KClass<T>): Boolean {
        return currentDestination?.destination?.hasRoute(any) == true
    }

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

    fun gotoTitleCompose()

    fun gotoCommunityCompose()

    fun gotoRoomCompose()

    fun gotoCommunitySetting(communityId: PrimaryKey)

    fun gotoRoomSetting(roomId: PrimaryKey)

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

            override fun gotoTitleCompose() {
                TODO("Not yet implemented")
            }

            override fun gotoCommunityCompose() {
                TODO("Not yet implemented")
            }

            override fun gotoRoomCompose() {
                TODO("Not yet implemented")
            }

            override fun gotoCommunitySetting(communityId: PrimaryKey) {
                TODO("Not yet implemented")
            }

            override fun gotoRoomSetting(roomId: PrimaryKey) {
                TODO("Not yet implemented")
            }
        }
    }
}

@OptIn(ExperimentalNotificationsApi::class)
private fun sendTopicNotification(message: TopicContent.Plain) {
    Notification(
        title = "New topic",
        message = message.plain,
        onActivated = {
            Napier.d(
                message = "Notification 1 activated",
                tag = "NotificationLog"
            )
        },
        onDismissed = { reason ->
            Napier.d(
                message = "Notification 1 dismissed: $reason",
                tag = "NotificationLog"
            )
        },
        onFailed = {
            Napier.d(
                tag = "NotificationLog",
                message = "Notification 1 failed"
            )
        }
    ) {
    }
}
