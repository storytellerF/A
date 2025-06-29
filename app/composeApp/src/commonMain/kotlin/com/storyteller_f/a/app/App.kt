package com.storyteller_f.a.app

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
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
import com.russhwolf.settings.Settings
import com.storyteller_f.a.app.compontents.*
import com.storyteller_f.a.app.model.DownloadViewModel
import com.storyteller_f.a.app.model.OnTopicCreated
import com.storyteller_f.a.app.model.getDownloadViewModel
import com.storyteller_f.a.app.pages.media.MediaPage
import com.storyteller_f.a.app.pages.user.AccountSwitch
import com.storyteller_f.a.app.pages.user.AccountSwitcher
import com.storyteller_f.a.app.pages.user.switchUser
import com.storyteller_f.a.app.ui.MaterialSymbolsOutlined
import com.storyteller_f.a.app.ui.theme.AppTheme
import com.storyteller_f.a.app.utils.createCustomDataStoreManager
import com.storyteller_f.a.app.utils.createSettings
import com.storyteller_f.a.app.utils.platform
import com.storyteller_f.a.app.utils.restoreFromStorage
import com.storyteller_f.a.client.core.*
import com.storyteller_f.shared.kmpLogger
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.storage.StorageSource
import com.storyteller_f.storage.createKotbaseStorageSource
import com.strabled.composepreferences.ProvideDataStoreManager
import com.strabled.composepreferences.setPreferences
import dev.tclement.fonticons.ProvideIconParameters
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object StaticObj {
    init {
        Napier.base(kmpLogger)
    }
}

fun getAsyncImageLoader(context: PlatformContext) =
    ImageLoader.Builder(context).crossfade(true).logger(DebugLogger()).build()


val LocalAppNav = compositionLocalOf {
    AppNav.EMPTY
}

val LocalDownloadViewModel = compositionLocalOf<DownloadViewModel> {
    error("no download view model")
}

val LocalClient = compositionLocalOf {
    HttpClient()
}

val LocalWsClient = compositionLocalOf {
    WebSocketClient.EMPTY
}

val LocalGlobalDialog = compositionLocalOf<GlobalDialogController> {
    error("no default dialog")
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

val LocalSessionManager = compositionLocalOf<CustomSessionManager> {
    error("No user session")
}

val LocalMainSessionManager = compositionLocalOf<CustomSessionManager> {
    error("No main user session")
}

val LocalAccountSwitcher = compositionLocalOf<AccountSwitcher> {
    error("not found")
}


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
        val videoSize: CustomVideoSize?,
    ) : MediaPlaySession {
        val id = obj.url
    }

    @Serializable
    @SerialName("image")
    data class Image(val mediaInfo: MediaInfo) : MediaPlaySession

    @Serializable
    @SerialName("local-image")
    data class LocalImage(val url: String) : MediaPlaySession
}

@OptIn(ExperimentalUuidApi::class)
data class LocalMediaPlaySession(val id: String, val uuid: Uuid)

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
    localSession: MediaPlaySession.VideoOrAudio?,
) {
    val navigator = rememberNavController()
    val json = LocalJson.current

    if (isPip && localSession != null) {
        MediaPage(localSession)
    } else {
        val appNav = remember {
            newAppNav(navigator, json)
        }
        ObserveMessage({
            appNav.toRoute<RoomScreen>()?.roomId
        }, {
            appNav.toRoute<TopicScreen>()?.topicId
        }) {
            appNav.gotoTopic(it.id)
        }
        val downloadViewModel = getDownloadViewModel()
        CompositionLocalProvider(LocalAppNav provides appNav, LocalDownloadViewModel provides downloadViewModel) {
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
    content: @Composable () -> Unit,
) {
    AppTheme(dynamicColor = true) {
        setSingletonImageLoaderFactory {
            getAsyncImageLoader(it)
        }

        val accountSwitcher = remember {
            AccountSwitcher()
        }
        val mainUserSessionManager = createAppSessionManager("main", wsServerUrl, httpUrl)
        var currentUser by remember {
            mutableStateOf<RawUserPass?>(null)
        }
        val currentUserSessionManager = currentUser?.let {
            val sessionManager = createAppSessionManager("alternative$it", wsServerUrl, httpUrl)
            sessionManager.sessionModel.updateState(ClientSessionState.Success(it))
            sessionManager
        } ?: mainUserSessionManager
        val address by currentUserSessionManager.address.collectAsState()
        val database = remember(address) {
            createKotbaseStorageSource(address)
        }

        val globalDialogController = remember {
            CustomGlobalDialogController()
        }
        val toasterState = rememberToasterState()
        val json = remember {
            Json {
                ignoreUnknownKeys = true
            }
        }
        CommonEntryContent(
            database,
            globalDialogController,
            toasterState,
            currentUserSessionManager,
            json,
            accountSwitcher,
            mainUserSessionManager,
            {
                currentUser = it
            },
            content
        )
    }
}

@Composable
fun CommonEntryContent(
    database: StorageSource,
    globalDialogController: CustomGlobalDialogController,
    toasterState: ToasterState,
    currentUserSessionManager: CustomSessionManager,
    json: Json,
    accountSwitcher: AccountSwitcher,
    mainUserSessionManager: CustomSessionManager,
    switch: (RawUserPass) -> Unit,
    content: @Composable () -> Unit,
) {
    LaunchedEffect(database) {
        processEvent(database)
    }
    GlobalDialog(globalDialogController)
    Toaster(toasterState, alignment = Alignment.Center)
    CompositionLocalProvider(
        LocalClient provides currentUserSessionManager.client,
        LocalWsClient provides currentUserSessionManager.webSocketClient,
        LocalSessionManager provides currentUserSessionManager,
        LocalMainSessionManager provides mainUserSessionManager,
        LocalToaster provides toasterState,
        LocalDatabase provides database,
        LocalJson provides json,
        LocalGlobalDialog provides globalDialogController,
        LocalAccountSwitcher provides accountSwitcher,
    ) {
        val scope = rememberCoroutineScope()
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
                AccountSwitch(accountSwitcher) { derPrivateKeyStr ->
                    switchUser(scope, globalDialogController, derPrivateKeyStr, switch)
                }
            }
        }
    }
}

@Composable
private fun createAppSessionManager(
    settingName: String,
    wsServerUrl: String,
    httpUrl: String,
): CustomSessionManager {
    val scope = rememberCoroutineScope()
    val sessionManager = remember(settingName) {
        createCustomUserSessionManager(settingName, buildWebSocketUrl(wsServerUrl), { model, cookieManager ->
            buildHttpClient(httpUrl, cookieManager, model)
        }) { roomFrame, session ->
            if (roomFrame is RoomFrame.NewTopicInfo) {
                bus.emit(OnTopicCreated(roomFrame.topicInfo))
                Napier.v(tag = "pagination") {
                    "save document ${roomFrame.topicInfo}"
                }
            }
        }
    }
    DisposableEffect(sessionManager) {
        val job = scope.launch {
            sessionManager.start()
        }
        onDispose {
            job.cancel()
        }
    }
    return sessionManager
}

fun buildHttpClient(
    httpUrl: String,
    cookieManager: CookiesStorage,
    model: UserSessionModel,
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


private fun buildWsListener(
    messageToasterState: ToasterState,
    hasPermission: Boolean,
    roomScreenId: () -> PrimaryKey?,
    topicScreenId: () -> PrimaryKey?,
    sessionManager: UserSessionManager,
    onClickTopic: (TopicInfo) -> Unit,
) = object : WebSocketClientListener {
    override suspend fun onReceived(frame: RoomFrame) {
        if (frame is RoomFrame.NewTopicInfo) {
            val plainFrame = if (frame.topicInfo.content is TopicContent.Encrypted) {
                val topicInfo = processEncryptedTopic(listOf(frame.topicInfo), sessionManager).first()
                RoomFrame.NewTopicInfo(topicInfo)
            } else {
                frame
            }
            val topicInfo = plainFrame.topicInfo
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
    val sessionManager = LocalSessionManager.current
    val clientWebSocketImpl = sessionManager.webSocketClient
    val messageToasterState = rememberToasterState()
    Toaster(messageToasterState, alignment = Alignment.TopCenter)
    val notificationProvider = getNotificationProvider()
    val hasPermission by notificationProvider.hasPermissionState
    val listener = remember(hasPermission) {
        buildWsListener(messageToasterState, hasPermission, roomScreenId, topicScreenId, sessionManager, onClickTopic)
    }
    DisposableEffect(null) {
        clientWebSocketImpl.addListener(listener)
        onDispose {
            clientWebSocketImpl.removeListener(listener)
        }
    }
}


@OptIn(ExperimentalNotificationsApi::class)
private suspend fun sendTopicNotification(
    message: TopicContent.Plain,
    topicInfo: TopicInfo,
    onClickTopic: (TopicInfo) -> Unit,
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

class CustomSessionManager(
    manager: UserSessionManager,
    val settings: Settings,
) : UserSessionManager(
    manager.client,
    manager.webSocketClient,
    manager.sessionModel
)

fun createCustomUserSessionManager(
    settingsName: String,
    webSocketUrl: String,
    createClient: (UserSessionModel, CookiesStorage) -> HttpClient,
    onReceiveFrame: suspend (RoomFrame, UserSessionModel) -> Unit,
): CustomSessionManager {
    val settings = createSettings(settingsName)
    val customSessionManager = createUserSessionManager(webSocketUrl, createClient, onReceiveFrame)
    customSessionManager.restoreFromStorage(settings)
    return CustomSessionManager(customSessionManager, settings)
}
