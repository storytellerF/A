package com.storyteller_f.a.app

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
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
import com.storyteller_f.a.app.common.AppNav
import com.storyteller_f.a.app.common.AppNavFactory
import com.storyteller_f.a.app.common.Downloader
import com.storyteller_f.a.app.common.ExternalUriHandler
import com.storyteller_f.a.app.common.HomeScreen
import com.storyteller_f.a.app.common.OnTopicCreated
import com.storyteller_f.a.app.common.RoomScreen
import com.storyteller_f.a.app.common.TopicScreen
import com.storyteller_f.a.app.common.Uploader
import com.storyteller_f.a.app.common.appNavSerializersModule
import com.storyteller_f.a.app.common.newAppNav
import com.storyteller_f.a.app.common.processEvent
import com.storyteller_f.a.app.common.rootEntryProvider
import com.storyteller_f.a.app.common.toRoute
import com.storyteller_f.a.app.core.common.LocalClient
import com.storyteller_f.a.app.core.components.AudioViewFilled
import com.storyteller_f.a.app.core.components.CustomGlobalDialogController
import com.storyteller_f.a.app.core.components.CustomGlobalTask
import com.storyteller_f.a.app.core.components.FileViewData
import com.storyteller_f.a.app.core.components.GlobalDialog
import com.storyteller_f.a.app.core.components.GlobalDialogContext
import com.storyteller_f.a.app.core.components.GlobalDialogController
import com.storyteller_f.a.app.core.components.GlobalTask
import com.storyteller_f.a.app.core.components.GlobalTaskContext
import com.storyteller_f.a.app.core.components.LocalMediaPlayerService
import com.storyteller_f.a.app.core.components.LocalToaster
import com.storyteller_f.a.app.core.components.MediaPlaySession
import com.storyteller_f.a.app.core.components.RemoteMediaItem
import com.storyteller_f.a.app.core.components.Sonner
import com.storyteller_f.a.app.core.components.VideoViewFilled
import com.storyteller_f.a.app.core.components.rememberIsInPipMode
import com.storyteller_f.a.app.core.utils.SavedSession
import com.storyteller_f.a.app.core.utils.SessionHistoryManager
import com.storyteller_f.a.app.core.utils.buildSessionHistoryFactory
import com.storyteller_f.a.app.core.utils.createSettings
import com.storyteller_f.a.app.core.utils.restoreFromStorage
import com.storyteller_f.a.app.pages.HOME_START_DESTINATION_PREFERENCE_KEY
import com.storyteller_f.a.app.pages.HOME_START_DESTINATION_WORLD
import com.storyteller_f.a.app.pages.file.FileExplorerPage
import com.storyteller_f.a.app.pages.file.FileViewPage
import com.storyteller_f.a.app.pages.room.RoomPage
import com.storyteller_f.a.app.pages.topic.TopicComposeData
import com.storyteller_f.a.app.pages.user.AccountSwitch
import com.storyteller_f.a.app.pages.user.AccountSwitcher
import com.storyteller_f.a.app.ui.MaterialSymbolsOutlined
import com.storyteller_f.a.app.ui.theme.AppTheme
import com.storyteller_f.a.app.utils.appPlatform
import com.storyteller_f.a.app.utils.createCustomDataStoreManager
import com.storyteller_f.a.client.core.ClientSessionState
import com.storyteller_f.a.client.core.RawUserPassInfo
import com.storyteller_f.a.client.core.SessionModel
import com.storyteller_f.a.client.core.UserPass
import com.storyteller_f.a.client.core.UserSessionManager
import com.storyteller_f.a.client.core.UserSessionModel
import com.storyteller_f.a.client.core.WebSocketClientImpl
import com.storyteller_f.a.client.core.buildWebSocketUrl
import com.storyteller_f.a.client.core.createUserSessionManager
import com.storyteller_f.a.client.core.defaultClientConfigure
import com.storyteller_f.a.client.core.getClient
import com.storyteller_f.a.client.core.processEncryptedTopic
import com.storyteller_f.a.client.core.startBackgroundTask
import com.storyteller_f.a.client.room.getRoomModelStorage
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun getAsyncImageLoader(context: PlatformContext) =
    ImageLoader.Builder(context).crossfade(true).logger(DebugLogger()).build()

/**
 * 可以通过lazy 的模式获取Downloader 和 Uploader
 */
interface ClientFileProvider {
    suspend fun getDownloader(): Downloader?
    suspend fun getUploader(): Uploader?

    companion object {
        val EMPTY = object : ClientFileProvider {
            override suspend fun getDownloader(): Downloader {
                TODO("Not yet implemented")
            }

            override suspend fun getUploader(): Uploader {
                TODO("Not yet implemented")
            }
        }
    }
}

val LocalAppNavFactory = compositionLocalOf {
    AppNavFactory.EMPTY
}

val LocalSessionManager = compositionLocalOf {
    CustomUserSessionManager.EMPTY
}

val LocalUserInfo = compositionLocalOf<UserInfo?> {
    null
}

val LocalAccountSwitcher = compositionLocalOf {
    AccountSwitcher()
}

val LocalClientFileProvider = compositionLocalOf {
    ClientFileProvider.EMPTY
}

@OptIn(DelicateCoroutinesApi::class)
val LocalUiViewModel = compositionLocalOf<UIViewModel> {
    error("LocalUiViewModel must be provided")
}
typealias AppGlobalDialogController = GlobalDialogController<GlobalDialogContext<CustomUserSessionManager>>

val LocalGlobalDialog = compositionLocalOf<AppGlobalDialogController> {
    error("LocalGlobalDialog must be provided")
}

val LocalGlobalTask = compositionLocalOf<GlobalTask<CustomUserSessionManager>> {
    error("LocalGlobalTask must be provided")
}

@Composable
fun App() {
    CommonEntry {
        val mediaPlayerService = LocalMediaPlayerService.current
        val playerSession by mediaPlayerService.state.collectAsState()
        AppInternal(playerSession)
    }
}

@Composable
fun AppInternal(mediaPlaySession: MediaPlaySession?) {
    val isPip = rememberIsInPipMode()
    val config = remember {
        SavedStateConfiguration {
            serializersModule = appNavSerializersModule
        }
    }
    val backStack = rememberNavBackStack(config, HomeScreen)
    val appNav = remember {
        object : AppNavFactory {
            val appNav = newAppNav(backStack)
            override fun newAppNav() = appNav
        }
    }
    if (!isPip || mediaPlaySession == null) {
        MainAppPage(appNav, backStack)
    } else {
        val remoteMediaItem = mediaPlaySession.remoteMediaItem
        if (remoteMediaItem.contentType.startsWith("video")) {
            VideoViewFilled(remoteMediaItem)
        } else {
            AudioViewFilled(remoteMediaItem)
        }
    }
}

@Composable
private fun MainAppPage(
    appNav: AppNavFactory,
    backStack: NavBackStack<NavKey>
) {
    DisposableEffect(Unit) {
        ExternalUriHandler.listener = { _ ->
        }
        // Removes the listener when the composable is no longer active
        onDispose {
            ExternalUriHandler.listener = null
        }
    }
    CompositionLocalProvider(
        LocalAppNavFactory provides appNav,
    ) {
        ObserveMessage()
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
            entryProvider = rootEntryProvider(appNav.newAppNav())
        )
    }
}

@Composable
fun CommonEntry(content: @Composable () -> Unit) {
    AppTheme(dynamicColor = true) {
        setSingletonImageLoaderFactory {
            getAsyncImageLoader(it)
        }
        val uiViewModel = LocalUiViewModel.current
        val instance by uiViewModel.instance.collectAsState()
        val currentUserSessionManager = instance.sessionManager
        val user by currentUserSessionManager.model.userHandler.data.collectAsState()

        val toasterState = rememberToasterState()
        Toaster(toasterState, alignment = Alignment.TopCenter)
        GlobalDialog(instance.controller)
        CompositionLocalProvider(
            LocalSessionManager provides currentUserSessionManager,
            LocalClient provides currentUserSessionManager.client,
            LocalToaster provides Sonner(toasterState),
            LocalGlobalDialog provides instance.controller,
            LocalGlobalTask provides instance.task,
            LocalUserInfo provides user
        ) {
            ProvideFontIcon {
                val dataStoreManager = createCustomDataStoreManager()
                ProvideDataStoreManager(dataStoreManager) {
                    setPreferences {
                        "gpt_model" defaultValue ""
                        HOME_START_DESTINATION_PREFERENCE_KEY defaultValue HOME_START_DESTINATION_WORLD
                    }
                    content()
                    AccountSwitch()
                }
            }
        }
    }
}

class AccountInstance(scope: CoroutineScope, name: String, wsServerUrl: String, httpUrl: String) {
    val events = MutableSharedFlow<Any>()
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
    val task = CustomGlobalTask(scope, GlobalTaskContext(events, sessionManager))
    val controller = CustomGlobalDialogController(GlobalDialogContext(events, sessionManager))
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
                    it.cancelAndJoin()
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
                it.sessionManager.proxy.startBackgroundTask().joinAll()
            }
        }
    }
}

@Composable
fun ProvideFontIcon(block: @Composable () -> Unit) {
    ProvideIconParameters(
        iconFont = MaterialSymbolsOutlined.rememberIconFont(),
        size = 20.dp,
        tintProvider = LocalContentColor,
        weight = FontWeight.Normal
    ) {
        block()
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
private fun ObserveMessage() {
    val appNavFactory = LocalAppNavFactory.current
    val sessionManager = LocalSessionManager.current
    val messageToasterState = rememberToasterState()
    Toaster(messageToasterState, alignment = Alignment.TopCenter)
    val notificationProvider = getNotificationProvider()
    val hasPermission by notificationProvider.hasPermissionState
    LaunchedEffect(sessionManager) {
        sessionManager.webSocketClient.frameFlow.collect { frame ->
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
                        "ws listener ${appPlatform.isActive} $hasPermission"
                    }
                    if (appPlatform.isActive) {
                        val roomId = appNavFactory.newAppNav().toRoute<RoomScreen>()?.roomId
                        val topicId = appNavFactory.newAppNav().toRoute<TopicScreen>()?.topicId
                        if (roomId != topicInfo.parentId &&
                            topicId != topicInfo.parentId
                        ) {
                            val nickname = topicInfo.extension?.authorInfo?.nickname
                            messageToasterState.show("$nickname: ${message.plain}")
                        }
                    } else if (hasPermission) {
                        sendTopicNotification(message, topicInfo) {
                            appNavFactory.newAppNav().gotoTopic(it.id)
                        }
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
                Napier.d(message = "Notification 1 activated", tag = "NotificationLog")
                onClickTopic(topicInfo)
            },
            onDismissed = { reason ->
                Napier.d(message = "Notification 1 dismissed: $reason", tag = "NotificationLog")
            },
            onFailed = {
                Napier.d(tag = "NotificationLog", message = "Notification 1 failed")
            }
        ) {
        }
    }
}

class CustomUserSessionManager(
    val proxy: UserSessionManager,
    val sessionHistoryManager: SessionHistoryManager,
) : UserSessionManager by proxy {
    companion object {
        val EMPTY = CustomUserSessionManager(object : UserSessionManager {
            override val webSocketClient: WebSocketClientImpl
                get() = TODO("Not yet implemented")
            override val client: HttpClient
                get() = TODO("Not yet implemented")
            override val model: SessionModel<UserInfo>
                get() = TODO("Not yet implemented")
            override val isAlreadySignIn: StateFlow<Boolean> = MutableStateFlow(true)
            override val address: StateFlow<String?>
                get() = TODO("Not yet implemented")

            override suspend fun updateAddress(clientSessionState: ClientSessionState) {
                TODO("Not yet implemented")
            }
        }, object : SessionHistoryManager {
            override fun getSavedSession(): SavedSession {
                TODO("Not yet implemented")
            }

            override suspend fun addSession(userPassInfo: RawUserPassInfo): UserPass {
                TODO("Not yet implemented")
            }

            override fun buildSession(alias: String): UserPass {
                TODO("Not yet implemented")
            }

            override fun removeSession(alias: String) {
                TODO("Not yet implemented")
            }

            override fun exitSession(alias: String) {
                TODO("Not yet implemented")
            }

            override fun logSession(alias: String) {
                TODO("Not yet implemented")
            }
        })
    }

    fun clearSession() {
        val alias = address.value ?: return
        sessionHistoryManager.exitSession(alias)
        model.clear()
    }
}

fun createCustomUserSessionManager(
    settingsName: String,
    webSocketUrl: String,
    createClient: (UserSessionModel, CookiesStorage) -> HttpClient,
    onReceiveFrame: suspend (RoomFrame, UserSessionModel, DefaultClientWebSocketSession) -> Unit,
): CustomUserSessionManager {
    val settings = createSettings(settingsName)
    val historyManager = buildSessionHistoryFactory(settings)
    val customSessionManager = createUserSessionManager(webSocketUrl, createClient, onReceiveFrame)
    customSessionManager.restoreFromStorage(settings)
    return CustomUserSessionManager(customSessionManager, historyManager)
}

@Composable
fun MediaPlayerPage(remoteMediaItem: RemoteMediaItem) {
    CommonEntry({
        FileViewPage(FileViewData.Player(remoteMediaItem))
    })
}

@Composable
fun BubblePage(roomId: Long) {
    CommonEntry({
        val appNav = remember {
            createAppNavFactoryForBubble()
        }
        CompositionLocalProvider(
            LocalAppNavFactory provides appNav,
        ) {
            RoomPage(roomId, false)
        }
    })
}

private fun createAppNavFactoryForBubble(): AppNavFactory = object : AppNavFactory {
    override fun newAppNav() = object : AppNav {
        override val backStack: NavBackStack<NavKey>
            get() = TODO("Not yet implemented")

        override fun gotoSignIn() = Unit

        override fun gotoRoom(roomId: PrimaryKey, showDialog: Boolean) = Unit

        override fun gotoCommunity(communityId: PrimaryKey, showDialog: Boolean) = Unit

        override fun gotoTopic(topicId: PrimaryKey) = Unit

        override fun gotoHome() = Unit

        override fun gotoTopicCompose(data: TopicComposeData) = Unit

        override fun gotoMemberPage(objectId: PrimaryKey, objectType: ObjectType) = Unit

        override fun gotoAbout() = Unit

        override fun gotoUser(uid: PrimaryKey) = Unit

        override fun back() = Unit

        override fun gotoUserSetting() = Unit

        override fun gotoPreference() = Unit

        override fun gotoMedia(info: FileInfo) = Unit

        override fun gotoLocalImage(url: String) = Unit

        override fun gotoTitleCompose() = Unit

        override fun gotoCommunityTitleCompose(communityId: PrimaryKey) = Unit

        override fun gotoRoomTitleCompose(roomId: PrimaryKey) = Unit

        override fun gotoCommunityCompose() = Unit

        override fun gotoRoomCompose() = Unit

        override fun gotoSettingPage(objectId: PrimaryKey, objectType: ObjectType) = Unit

        override fun gotoFontSettingsPage(communityId: PrimaryKey) = Unit

        override fun gotoReactionListPage(topicId: PrimaryKey) = Unit

        override fun gotoFavoritePage() = Unit

        override fun gotoSubscriptionPage() = Unit
        override fun gotoUserReactionRecordsPage() = Unit

        override fun gotoUserCommentsPage() = Unit

        override fun gotoFileExplorer(objectTuple: ObjectTuple?) = Unit

        override fun gotoFileRefs(fileId: PrimaryKey) = Unit
    }
}

@Composable
fun UploadPage() {
    val userInfo = LocalUserInfo.current ?: return
    CommonEntry {
        FileExplorerPage(mediaTarget = userInfo.id ob ObjectType.USER)
    }
}
