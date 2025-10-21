package com.storyteller_f.a.app.compose_app

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.dokar.sonner.rememberToasterState
import com.kdroid.composenotification.builder.ExperimentalNotificationsApi
import com.kdroid.composenotification.builder.Notification
import com.kdroid.composenotification.builder.getNotificationProvider
import com.russhwolf.settings.Settings
import com.storyteller_f.a.app.compose_app.common.AppNav
import com.storyteller_f.a.app.compose_app.common.Downloader
import com.storyteller_f.a.app.compose_app.common.HomeScreen
import com.storyteller_f.a.app.compose_app.common.OnTopicCreated
import com.storyteller_f.a.app.compose_app.common.RoomScreen
import com.storyteller_f.a.app.compose_app.common.Sonner
import com.storyteller_f.a.app.compose_app.common.Toast
import com.storyteller_f.a.app.compose_app.common.TopicScreen
import com.storyteller_f.a.app.compose_app.common.Uploader
import com.storyteller_f.a.app.compose_app.common.buildRootNav
import com.storyteller_f.a.app.compose_app.common.newAppNav
import com.storyteller_f.a.app.compose_app.common.processEvent
import com.storyteller_f.a.app.compose_app.common.toRoute
import com.storyteller_f.a.app.compose_app.components.ConstPlayItem
import com.storyteller_f.a.app.compose_app.components.CustomGlobalDialogController
import com.storyteller_f.a.app.compose_app.components.CustomVideoSize
import com.storyteller_f.a.app.compose_app.components.GlobalDialog
import com.storyteller_f.a.app.compose_app.components.GlobalDialogController
import com.storyteller_f.a.app.compose_app.components.GlobalTask
import com.storyteller_f.a.app.compose_app.components.RemoteMediaItem
import com.storyteller_f.a.app.compose_app.components.globalPlayerState
import com.storyteller_f.a.app.compose_app.components.rememberIsInPipMode
import com.storyteller_f.a.app.compose_app.pages.file.FileViewPage
import com.storyteller_f.a.app.compose_app.pages.user.AccountSwitch
import com.storyteller_f.a.app.compose_app.pages.user.AccountSwitcher
import com.storyteller_f.a.app.compose_app.ui.MaterialSymbolsOutlined
import com.storyteller_f.a.app.compose_app.ui.theme.AppTheme
import com.storyteller_f.a.app.compose_app.utils.appPlatform
import com.storyteller_f.a.app.compose_app.utils.createCustomDataStoreManager
import com.storyteller_f.a.app.core.common.LocalClient
import com.storyteller_f.a.app.core.utils.createSettings
import com.storyteller_f.a.app.core.utils.restoreFromStorage
import com.storyteller_f.a.client.core.ClientSessionState
import com.storyteller_f.a.client.core.UserPass
import com.storyteller_f.a.client.core.UserSessionManager
import com.storyteller_f.a.client.core.UserSessionModel
import com.storyteller_f.a.client.core.WebSocketClient
import com.storyteller_f.a.client.core.buildWebSocketUrl
import com.storyteller_f.a.client.core.createUserSessionManager
import com.storyteller_f.a.client.core.defaultClientConfigure
import com.storyteller_f.a.client.core.getClient
import com.storyteller_f.a.client.core.processEncryptedTopic
import com.storyteller_f.a.client.core.startBackgroundTask
import com.storyteller_f.a.client.room.RoomModelStorage
import com.storyteller_f.a.client.room.getRoomDatabase
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.storage.ModelStorage
import com.strabled.composepreferences.ProvideDataStoreManager
import com.strabled.composepreferences.setPreferences
import dev.tclement.fonticons.ProvideIconParameters
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun getAsyncImageLoader(context: PlatformContext) =
    ImageLoader.Builder(context).crossfade(true).logger(DebugLogger()).build()

interface ClientFileProvider {
    fun getDownloader(): Downloader?
    fun getUploader(): Uploader?
}

val LocalAppNav = compositionLocalOf {
    AppNav.EMPTY
}

val LocalWsClient = compositionLocalOf<WebSocketClient> {
    error("no ws client")
}

val LocalGlobalDialog = compositionLocalOf<GlobalDialogController> {
    error("no default dialog")
}

val LocalGlobalTask = compositionLocalOf<GlobalTask> {
    error("no default task")
}

@OptIn(DelicateCoroutinesApi::class)
val LocalToaster = compositionLocalOf<Toast> {
    error("no default toast")
}

val LocalDatabase = compositionLocalOf<ModelStorage> {
    error("No database")
}

val LocalSessionManager = compositionLocalOf<CustomUserSessionManager> {
    error("No user session")
}

val LocalMainSessionManager = compositionLocalOf<CustomUserSessionManager> {
    error("No main user session")
}

val LocalAccountSwitcher = compositionLocalOf<AccountSwitcher> {
    error("not found")
}

val LocalClientFileProvider = compositionLocalOf<ClientFileProvider> {
    error("no file provider")
}

@Serializable
sealed interface FileViewInfo {
    @OptIn(ExperimentalUuidApi::class)
    @Serializable
    @SerialName("player")
    data class Player(
        val obj: RemoteMediaItem,
        val contentType: String,
        val playList: List<ConstPlayItem>,
        val uuids: List<Uuid>,
        val videoSize: CustomVideoSize?,
    ) : FileViewInfo {
        val id = obj.url
    }

    @Serializable
    @SerialName("regular")
    data class Regular(val fileInfo: FileInfo) : FileViewInfo

    @Serializable
    @SerialName("local-image")
    data class LocalImage(val url: String) : FileViewInfo
}

@OptIn(ExperimentalUuidApi::class)
data class LocalMediaPlaySession(val id: String, val uuid: Uuid)

@OptIn(DelicateCoroutinesApi::class)
val uiViewModel by lazy {
    UIViewModel(GlobalScope, AppConfig.WS_SERVER_URL, AppConfig.SERVER_URL)
}

@Composable
fun App() {
    CommonEntry {
        val playerSession by globalPlayerState
        val isPip = rememberIsInPipMode()
        MainAppPage(isPip, playerSession)
    }
}

@Composable
fun MainAppPage(
    isPip: Boolean,
    player: FileViewInfo.Player?,
) {
    val navigator = rememberNavController()
    val scope = rememberCoroutineScope()
    val appNav = remember {
        newAppNav(navigator, scope)
    }
    if (isPip && player != null) {
        FileViewPage(player)
    } else {
        ObserveMessage({
            appNav.toRoute<RoomScreen>()?.roomId
        }, {
            appNav.toRoute<TopicScreen>()?.topicId
        }) {
            appNav.gotoTopic(it.id)
        }
        CompositionLocalProvider(
            LocalAppNav provides appNav,
        ) {
            NavHost(navigator, startDestination = HomeScreen, enterTransition = {
                slideInHorizontally {
                    it
                }
            }, exitTransition = {
                slideOutHorizontally {
                    -it
                }
            }, popEnterTransition = {
                slideInHorizontally {
                    -it
                }
            }, popExitTransition = {
                slideOutHorizontally {
                    it
                }
            }) {
                buildRootNav(navigator)
            }
        }
    }
}

@Composable
fun CommonEntry(content: @Composable () -> Unit) {
    AppTheme(dynamicColor = true) {
        setSingletonImageLoaderFactory {
            getAsyncImageLoader(it)
        }
        val accountSwitcher = remember {
            AccountSwitcher()
        }
        val mainUserSessionManager = uiViewModel.mainInstance.sessionManager
        val instance by uiViewModel.instance.collectAsState()
        val currentUserSessionManager = instance.sessionManager
        val database by instance.database.collectAsState()
        val task = instance.task
        val controller = instance.controller

        val toasterState = rememberToasterState()
        Toaster(toasterState, alignment = Alignment.TopCenter)
        GlobalDialog(instance.controller)
        CompositionLocalProvider(
            LocalSessionManager provides currentUserSessionManager,
            LocalMainSessionManager provides mainUserSessionManager,
            LocalAccountSwitcher provides accountSwitcher,
            LocalClient provides currentUserSessionManager.client,
            LocalWsClient provides currentUserSessionManager.webSocketClient,
            LocalToaster provides Sonner(toasterState),
            LocalDatabase provides database,
            LocalGlobalDialog provides controller,
            LocalGlobalTask provides task
        ) {
            CommonEntryInternal(content)
        }
    }
}

class AccountInstance(scope: CoroutineScope, name: String, wsServerUrl: String, httpUrl: String) {
    val events = MutableSharedFlow<Any>()
    val controller = CustomGlobalDialogController(events)
    val task = GlobalTask(scope, events)
    val sessionManager = createCustomUserSessionManager(
        name,
        buildWebSocketUrl(wsServerUrl),
        { model, cookieManager ->
            buildHttpClient(httpUrl, cookieManager, model)
        }
    ) { frame, _, _ ->
        if (frame is RoomFrame.NewTopicInfo) {
            events.emit(OnTopicCreated(frame.topicInfo))
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
            database.collectLatest {
                processEvent(it, events)
            }
        }
        scope.launch {
            val jobs = sessionManager.proxy.startBackgroundTask()
            try {
                awaitCancellation()
            } finally {
                jobs.forEach {
                    it.cancel()
                }
            }
        }
    }
}

class UIViewModel(viewModelScope: CoroutineScope, wsServerUrl: String, httpUrl: String) {
    val mainInstance = AccountInstance(viewModelScope, "main", wsServerUrl, httpUrl)

    val childAccount = MutableStateFlow<UserPass?>(null)
    val instance = childAccount.map {
        it?.address()?.getOrNull()?.let { address ->
            AccountInstance(viewModelScope, address, wsServerUrl, httpUrl).apply {
                sessionManager.model.updateState(ClientSessionState.Success(it))
            }
        } ?: mainInstance
    }.stateIn(viewModelScope, SharingStarted.Eagerly, mainInstance)

    init {
        viewModelScope.launch {
            instance.collectLatest {
                it.sessionManager.proxy.startBackgroundTask().forEach { job ->
                    job.join()
                }
            }
        }
    }
}

@Composable
private fun CommonEntryInternal(
    content: @Composable (() -> Unit)
) {
    val accountSwitcher = LocalAccountSwitcher.current
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
            AccountSwitch(accountSwitcher)
        }
    }
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

@Composable
private fun ObserveMessage(
    roomScreenId: () -> PrimaryKey?,
    topicScreenId: () -> PrimaryKey?,
    onClickTopic: (TopicInfo) -> Unit = {},
) {
    val sessionManager = LocalSessionManager.current
    val messageToasterState = rememberToasterState()
    Toaster(messageToasterState, alignment = Alignment.TopCenter)
    val notificationProvider = getNotificationProvider()
    val hasPermission by notificationProvider.hasPermissionState
    LaunchedEffect(sessionManager) {
        sessionManager.webSocketClient.frameFlow.collect { frame ->
            if (frame is RoomFrame.NewTopicInfo) {
                val plainFrame = if (frame.topicInfo.content is TopicContent.Encrypted) {
                    val topicInfo =
                        processEncryptedTopic(listOf(frame.topicInfo), sessionManager).first()
                    RoomFrame.NewTopicInfo(topicInfo)
                } else {
                    frame
                }
                val topicInfo = plainFrame.topicInfo
                val message = topicInfo.content
                if (message is TopicContent.Plain) {
                    Napier.i(tag = "WebSocket") {
                        "ws listener ${appPlatform.isActive} $hasPermission"
                    }
                    if (appPlatform.isActive) {
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

class CustomUserSessionManager(
    val proxy: UserSessionManager,
    val settings: Settings,
) : UserSessionManager by proxy

fun createCustomUserSessionManager(
    settingsName: String,
    webSocketUrl: String,
    createClient: (UserSessionModel, CookiesStorage) -> HttpClient,
    onReceiveFrame: suspend (RoomFrame, UserSessionModel, DefaultClientWebSocketSession) -> Unit,
): CustomUserSessionManager {
    val settings = createSettings(settingsName)
    val customSessionManager = createUserSessionManager(webSocketUrl, createClient, onReceiveFrame)
    customSessionManager.restoreFromStorage(settings)
    return CustomUserSessionManager(customSessionManager, settings)
}
