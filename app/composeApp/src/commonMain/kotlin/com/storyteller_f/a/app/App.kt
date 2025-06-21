package com.storyteller_f.a.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.russhwolf.settings.Settings
import com.storyteller_f.a.app.compontents.*
import com.storyteller_f.a.app.model.*
import com.storyteller_f.a.app.pages.PreferencePage
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
import com.storyteller_f.a.app.pages.user.LoginPage
import com.storyteller_f.a.app.pages.user.MemberPage
import com.storyteller_f.a.app.pages.user.UserPage
import com.storyteller_f.a.app.pages.user.UserSettingPage
import com.storyteller_f.a.app.ui.MaterialSymbolsOutlined
import com.storyteller_f.a.app.ui.theme.AppTheme
import com.storyteller_f.a.app.utils.createCustomDataStoreManager
import com.storyteller_f.a.app.utils.createSettings
import com.storyteller_f.a.app.utils.platform
import com.storyteller_f.a.app.utils.restoreFromStorage
import com.storyteller_f.a.client_lib.*
import com.storyteller_f.shared.kmpLogger
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.storage.*
import com.strabled.composepreferences.ProvideDataStoreManager
import com.strabled.composepreferences.setPreferences
import dev.tclement.fonticons.ProvideIconParameters
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object StaticObj {
    init {
        Napier.base(kmpLogger)
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
    WebSocketClient.EMPTY
}

val LocalJson = compositionLocalOf<Json> {
    error("no default json")
}

@OptIn(DelicateCoroutinesApi::class)
val LocalToaster = compositionLocalOf {
    ToasterState(GlobalScope)
}

val LocalDatabase = compositionLocalOf {
    StorageSource.EMPTY
}

val LocalSessionManager = compositionLocalOf<UserSessionManager> {
    error("No user session")
}

val LocalMainSessionManager = compositionLocalOf<UserSessionManager> {
    error("No main user session")
}

val LocalSettings = compositionLocalOf<Settings> {
    error("No default settings")
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
data object PreferenceScreen

@Serializable
sealed interface MediaPlaySession {
    @OptIn(ExperimentalUuidApi::class)
    @Serializable
    @SerialName("video")
    data class VideoOrAudio(
        val obj: RemoteMediaItem,
        val contentType: String,
        val playList: List<ConstPlayItem>,
        val uuids: List<Uuid>,
        val videoSize: CustomVideoSize?
    ) : MediaPlaySession {
        val id = obj.url
    }

    @Serializable
    @SerialName("image")
    data class Image(val mediaInfo: MediaInfo, val objectTuple: ObjectTuple) : MediaPlaySession

    @Serializable
    @SerialName("local-image")
    data class LocalImage(val url: String) : MediaPlaySession
}

@OptIn(ExperimentalUuidApi::class)
data class LocalMediaPlaySession(val id: String, val uuid: Uuid)

@Serializable
data class MediaScreen(val json: String)

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
    CommonEntry(httpUrl, wsServerUrl) {
        StaticObj
        val playerSession by currentPlayerSession
        val isPip = rememberIsInPipMode()

        MainAppPage(isPip, playerSession)
    }
}

@Composable
private fun MainAppPage(
    isPip: Boolean,
    localSession: MediaPlaySession.VideoOrAudio?
) {
    val navigator = rememberNavController()
    val json = LocalJson.current

    if (isPip && localSession != null) {
        MediaPage(localSession)
    } else {
        val appNav = remember<AppNav> {
            newAppNav(navigator, json)
        }
        ObserveMessage({
            appNav.toRoute<RoomScreen>()?.roomId
        }, {
            appNav.toRoute<TopicScreen>()?.topicId
        }) {
            appNav.gotoTopic(it.id)
        }
        CompositionLocalProvider(LocalAppNav provides appNav) {
            NavHost(navigator, startDestination = HomeScreen) {
                buildRootNav(navigator)
            }
        }
    }
}

@Composable
fun CommonEntry(
    httpUrl: String,
    wsServerUrl: String,
    content: @Composable () -> Unit
) {
    AppTheme(dynamicColor = true) {
        setSingletonImageLoaderFactory {
            getAsyncImageLoader(it)
        }

        val settings = remember {
            createSettings()
        }
        CompositionLocalProvider(LocalSettings provides settings) {
            val mainUserSession = createSessionManager(wsServerUrl, httpUrl)
            val address by mainUserSession.sessionModel.state.map {
                (it as? ClientSessionState.Success)?.session?.address()?.getOrNull()
            }.collectAsState(null)
            val database = remember(address) {
                createKotbaseStorageSource(address)
            }
            LaunchedEffect(database, address) {
                bus.collect { event ->
                    processEvent(event, database)
                }
            }
            GlobalDialog(globalDialogState)
            val toasterState = rememberToasterState()
            Toaster(toasterState, alignment = Alignment.Center)
            val json = remember {
                Json {
                    ignoreUnknownKeys = true
                }
            }
            CompositionLocalProvider(
                LocalClient provides mainUserSession.client,
                LocalWsClient provides mainUserSession.webSocketClient,
                LocalSessionManager provides mainUserSession,
                LocalMainSessionManager provides mainUserSession,
                LocalToaster provides toasterState,
                LocalDatabase provides database,
                LocalJson provides json,
            ) {
                ProvideIconParameters(
                    iconFont = MaterialSymbolsOutlined.rememberIconFont(),
                    size = 20.dp,
                    tintProvider = LocalContentColor,
                    weight = FontWeight.Normal
                ) {
                    val dataStoreManager = createCustomDataStoreManager()
                    ProvideDataStoreManager(dataStoreManager) {
                        setPreferences {
                            "gpt_model" defaultValue ""
                        }
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun createSessionManager(
    wsServerUrl: String,
    httpUrl: String
): UserSessionManager {
    val settings = LocalSettings.current
    val scope = rememberCoroutineScope()
    val mainUserSession = remember {
        createUserSessionManager(buildWebSocketUrl(wsServerUrl), { model, cookieManager ->
            buildHttpClient(httpUrl, cookieManager, model)
        }) { roomFrame, session ->
            if (roomFrame is RoomFrame.NewTopicInfo) {
                val info = processEncryptedTopic(listOf(roomFrame.topicInfo), session).first()
                bus.emit(OnTopicCreated(info))
                Napier.v(tag = "pagination") {
                    "save document $info"
                }
            }
        }.apply {
            restoreFromStorage(settings)
        }
    }
    DisposableEffect(mainUserSession) {
        val job = scope.launch {
            mainUserSession.start()
        }
        onDispose {
            job.cancel()
        }
    }
    return mainUserSession
}

fun buildHttpClient(
    httpUrl: String,
    cookieManager: CookiesStorage,
    model: UserSessionModel
): HttpClient = if (httpUrl.isEmpty()) {
    HttpClient { }
} else {
    getClient {
        defaultClientConfigure(cookieManager, manager = model, httpUrl = httpUrl)
    }
}

fun buildWebSocketUrl(wsServerUrl: String): String = buildUrl {
    takeFrom(wsServerUrl)
    appendPathSegments("link")
}.toString()

private fun processEvent(event: Any, database: StorageSource) {
    when (event) {
        is OnAddReaction -> processOnAddReaction(database, event)

        is OnRemoveReaction -> processRemoveReaction(database, event)

        is OnCommunityJoined -> database.getCollection("communities", CommunityInfo::class)
            .save(event.info.id, event.info)

        is OnCommunityExited -> database.getCollection("communities", CommunityInfo::class)
            .save(event.info.id, event.info)

        is OnCommunityUpdated -> {
            database.getCollection("communities", CommunityInfo::class).save(event.info.id, event.info)
            database.getCollectionByPrefix("communities_", CommunityInfo::class).filter {
                it.exists(StorageExpression.IdEq("id", event.info.id))
            }.forEach {
                it.save(event.info.id, event.info)
            }
        }

        is OnTopicChanged -> processTopicChanged(event, database)

        is OnTopicCreated -> processTopicCreated(event, database)

        is OnRoomJoined -> database.getCollection("rooms", RoomInfo::class).save(event.info.id, event.info)

        is OnRoomExited -> database.getCollection("rooms", RoomInfo::class).save(event.info.id, event.info)

        is OnRoomUpdated -> {
            database.getCollection("rooms", RoomInfo::class).save(event.info.id, event.info)
            database.getCollectionByPrefix("rooms_", RoomInfo::class).filter {
                it.exists(StorageExpression.IdEq("id", event.info.id))
            }.forEach {
                it.save(event.info.id, event.info)
            }
        }

        is OnUserUpdated -> {
            database.getCollection("users", UserInfo::class).save(event.info.id, event.info)
            database.getCollectionByPrefix("users_", UserInfo::class).filter {
                it.exists(StorageExpression.IdEq("id", event.info.id))
            }.forEach {
                it.save(event.info.id, event.info)
            }
        }

        is OnMediaUploaded -> {
            event.mediaInfos.forEach {
                database.getCollection("medias_${it.owner}", MediaInfo::class).save(it.id, it)
            }
        }
    }
}

private fun processTopicCreated(
    event: OnTopicCreated,
    database: StorageSource
) {
    val topicInfo = event.topicInfo
    database.getCollection("topics_${topicInfo.parentId}", TopicInfo::class).save(topicInfo.id, topicInfo)
    with(database.getCollection("topics", TopicInfo::class)) {
        save(topicInfo.id, topicInfo)
        topicInfo.aid?.let { saveDocument(it, topicInfo) }
    }
}

private fun processTopicChanged(
    event: OnTopicChanged,
    database: StorageSource,
) {
    val topicInfo = event.topicInfo
    database.getCollection("topics_${topicInfo.parentId}", TopicInfo::class).save(topicInfo.id, topicInfo)
    with(database.getCollection("topics", TopicInfo::class)) {
        save(topicInfo.id, topicInfo)
        topicInfo.aid?.let { saveDocument(it, topicInfo) }
    }
    // 尝试更新到推荐
    with(database.getCollection("topics_0", TopicInfo::class)) {
        if (exists(StorageExpression.IdEq("id", topicInfo.id))) {
            save(topicInfo.id, topicInfo)
        }
    }
}

private fun processRemoveReaction(
    database: StorageSource,
    event: OnRemoveReaction,
) {
    database.getCollection("topic", TopicInfo::class).update(event.topicId) { old ->
        val extension = old.extension ?: TopicInfo.Extension(UserInfo.EMPTY)
        val new = extension.reactions.orEmpty().map { info ->
            when {
                info.emoji != event.emoji -> info
                !info.hasReacted -> info
                else -> info.copy(count = info.count - 1, hasReacted = false)
            }
        }.toImmutableList()
        old.copy(extension = extension.copy(reactions = new), reactionCount = old.reactionCount + 1)
    }
}

private fun processOnAddReaction(
    database: StorageSource,
    event: OnAddReaction,
) {
    database.getCollection("topic", TopicInfo::class).update(event.topicId) { old ->
        val extension = old.extension ?: TopicInfo.Extension(UserInfo.EMPTY)
        val newReactionInfo = extension.reactions.orEmpty().map { info ->
            when {
                info.emoji != event.emoji -> info
                info.hasReacted -> info
                else -> info.copy(count = info.count + 1, hasReacted = true)
            }
        }.toImmutableList()
        old.copy(extension = extension.copy(reactions = newReactionInfo), reactionCount = old.reactionCount + 1)
    }
}

@Composable
fun TestContainer(block: @Composable () -> Unit) {
    CommonEntry("", "", {
        val appNav = remember {
            AppNav.EMPTY
        }
        CompositionLocalProvider(LocalAppNav provides appNav) {
            val ws = WebSocketClient.EMPTY
            CompositionLocalProvider(LocalWsClient provides ws) {
                block()
            }
        }
    })
}

private fun buildWsListener(
    messageToasterState: ToasterState,
    hasPermission: Boolean,
    roomScreenId: () -> PrimaryKey?,
    topicScreenId: () -> PrimaryKey?,
    onClickTopic: (TopicInfo) -> Unit,
) = object : WebSocketClientListener {
    override suspend fun onReceived(frame: RoomFrame) {
        if (frame is RoomFrame.NewTopicInfo) {
            val topicInfo = frame.topicInfo
            val message = topicInfo.content
            if (message is TopicContent.Plain) {
                Napier.i(tag = "WebSocket") {
                    "ws listener ${platform.isActive} $hasPermission"
                }
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
                    sendTopicNotification(message, topicInfo, onClickTopic)
                }
            }
        }
    }
}

@Composable
private fun ObserveMessage(
    roomScreenId: () -> PrimaryKey?,
    topicScreenId: () -> PrimaryKey?,
    onClickTopic: (TopicInfo) -> Unit = {},
) {
    val mainUserSession = LocalMainSessionManager.current
    val clientWebSocketImpl = mainUserSession.webSocketClient
    val messageToasterState = rememberToasterState()
    Toaster(messageToasterState, alignment = Alignment.TopCenter)
    val notificationProvider = getNotificationProvider()
    val hasPermission by notificationProvider.hasPermissionState
    val listener = remember(hasPermission) {
        buildWsListener(messageToasterState, hasPermission, roomScreenId, topicScreenId, onClickTopic)
    }
    clientWebSocketImpl.addListener(listener)
    DisposableEffect(null) {
        onDispose {
            clientWebSocketImpl.removeListener(listener)
        }
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
        val json = LocalJson.current
        val route = it.toRoute<MediaScreen>()
        val pack = json.decodeFromString<MediaPlaySession>(route.json)
        MediaPage(pack)
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
    composable<PreferenceScreen> {
        PreferencePage()
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

private fun newAppNav(navigator: NavHostController, json: Json) = object : AppNav {
    override val currentDestination: NavBackStackEntry?
        get() = navigator.currentBackStackEntry
    override val currentDestinationFlow: Flow<NavBackStackEntry>
        get() = navigator.currentBackStackEntryFlow

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

    override fun gotoPreference() {
        navigator.navigate(PreferenceScreen)
    }

    override fun gotoMedia(info: MediaInfo, objectTuple: ObjectTuple) {
        val route = MediaScreen(json.encodeToString<MediaPlaySession>(MediaPlaySession.Image(info, objectTuple)))
        navigator.navigate(route)
    }

    override fun gotoLocalImage(url: String) {
        val route = MediaScreen(json.encodeToString<MediaPlaySession>(MediaPlaySession.LocalImage(url)))
        navigator.navigate(route)
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

    override fun gotoSettingPage(objectId: PrimaryKey, objectType: ObjectType) {
        if (objectType == ObjectType.COMMUNITY) {
            navigator.navigate(CommunitySettingScreen(objectId))
        } else {
            navigator.navigate(RoomSettingScreen(objectId))
        }
    }
}

fun getAsyncImageLoader(context: PlatformContext) =
    ImageLoader.Builder(context).crossfade(true).logger(DebugLogger()).build()

val bus = MutableSharedFlow<Any>()

inline fun <reified T : Any> AppNav.toRoute(): T? {
    if (!hasRoute(T::class)) return null
    return currentDestination?.toRoute<T>()
}

inline fun <reified T : Any> AppNav.hasRouteFlow(crossinline block: (T) -> Boolean = { true }): Flow<Boolean> {
    return currentDestinationFlow.map {
        it.destination.hasRoute<T>() && block(it.toRoute<T>())
    }
}

interface AppNav {
    val currentDestination: NavBackStackEntry?

    val currentDestinationFlow: Flow<NavBackStackEntry>

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

    fun gotoPreference()

    fun gotoMedia(info: MediaInfo, objectTuple: ObjectTuple)

    fun gotoLocalImage(url: String)

    fun gotoTitleCompose()

    fun gotoCommunityCompose()

    fun gotoRoomCompose()

    fun gotoSettingPage(objectId: PrimaryKey, objectType: ObjectType)

    companion object {
        val EMPTY = object : AppNav {
            override val currentDestination: NavBackStackEntry
                get() = TODO("Not yet implemented")

            override val currentDestinationFlow: Flow<NavBackStackEntry>
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

            override fun gotoPreference() {
                TODO("Not yet implemented")
            }

            override fun gotoMedia(info: MediaInfo, objectTuple: ObjectTuple) {
                TODO("Not yet implemented")
            }

            override fun gotoLocalImage(url: String) {
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

            override fun gotoSettingPage(objectId: PrimaryKey, objectType: ObjectType) {
                TODO("Not yet implemented")
            }
        }
    }
}

@OptIn(ExperimentalNotificationsApi::class)
private suspend fun sendTopicNotification(
    message: TopicContent.Plain,
    topicInfo: TopicInfo,
    onClickTopic: (TopicInfo) -> Unit
) {
    withContext(Dispatchers.Main) {
        Notification(
            title = "New topic",
            message = message.plain,
            onActivated = {
                Napier.d(
                    message = "Notification 1 activated",
                    tag = "NotificationLog"
                )
                onClickTopic(topicInfo)
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
}

fun ToasterState.showShortToast(message: String) {
    show(message, duration = 1.seconds)
}
