package com.storyteller_f.a.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade
import coil3.util.DebugLogger
import com.russhwolf.settings.Settings
import com.storyteller_f.a.app.common.getOrCreateCollection
import com.storyteller_f.a.app.community.CommunityPage
import com.storyteller_f.a.app.compontents.GlobalDialog
import com.storyteller_f.a.app.compontents.GlobalDialogController
import com.storyteller_f.a.app.room.RoomPage
import com.storyteller_f.a.app.topic.TopicComposePage
import com.storyteller_f.a.app.topic.TopicPage
import com.storyteller_f.a.app.topic.processEncryptedTopic
import com.storyteller_f.a.app.ui.theme.AppTheme
import com.storyteller_f.a.app.user.MemberPage
import com.storyteller_f.a.client_lib.ClientWebSocket
import com.storyteller_f.a.client_lib.LoginViewModel
import com.storyteller_f.a.client_lib.addRequestHeaders
import com.storyteller_f.a.client_lib.defaultClientConfigure
import com.storyteller_f.a.client_lib.getClient
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.ObjectType.*
import com.storyteller_f.shared.type.PrimaryKey
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import kotbase.MutableDocument
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.internal.ChannelFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object StaticObj {
    init {
        Napier.base(DebugAntilog())
    }
}

val globalDialogState = GlobalDialogController()

val LocalAppNav = compositionLocalOf {
    AppNav.EMPTY
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
data class TopicComposeScreen(val objectType: ObjectType, val objectId: PrimaryKey)

@Serializable
data class MemberScreen(val objectType: ObjectType, val objectId: PrimaryKey)

@OptIn(ExperimentalCoilApi::class)
@Composable
fun App() {
    StaticObj
    AppTheme(dynamicColor = false) {
        setSingletonImageLoaderFactory {
            getAsyncImageLoader(it)
        }
        val navigator = rememberNavController()
        val appNav = remember {
            newAppNav(navigator)
        }
        CompositionLocalProvider(LocalAppNav provides appNav) {
            GlobalDialog(globalDialogState)
            NavHost(navigator, startDestination = HomeScreen) {
                buildRootNav(navigator)
            }
        }
    }
}

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
        val (objectType, objectId) = it.toRoute<TopicComposeScreen>()
        TopicComposePage(objectType, objectId) {
            navigator.popBackStack()
        }
    }
    composable<MemberScreen> {
        val (objectType, objectId) = it.toRoute<MemberScreen>()
        MemberPage(objectId, objectType)
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

    override fun gotoTopicCompose(objectType: ObjectType, objectId: PrimaryKey) {
        navigator.navigate(route = TopicComposeScreen(objectType, objectId))
    }

    override fun gotoMemberPage(
        objectId: PrimaryKey,
        objectType: ObjectType
    ) {
        navigator.navigate(route = MemberScreen(objectType, objectId))
    }
}

fun getAsyncImageLoader(context: PlatformContext) =
    ImageLoader.Builder(context).crossfade(true).logger(DebugLogger()).build()

val client by lazy {
    getClient {
        defaultClientConfigure()
        setupRequest()
    }
}

val clientWs by lazy {
    ClientWebSocket({
        client.webSocketSession(BuildKonfig.WS_SERVER_URL + "link") {
            addRequestHeaders(LoginViewModel.session?.first)
        }
    }) {
        if (it is RoomFrame.NewTopicInfo) {
            val info = processEncryptedTopic(listOf(it.topicInfo)).first()
            getOrCreateCollection("topics${info.parentId}").save(
                MutableDocument(
                    info.id.toString(),
                    Json.encodeToString(info)
                )
            )
            Napier.v(tag = "pagination") {
                "save document $info"
            }
        }
    }
}

fun HttpClientConfig<*>.setupRequest() {
    defaultRequest {
        url(BuildKonfig.SERVER_URL)
    }
}

val bus = MutableSharedFlow<Any>()

val settings = Settings()

interface AppNav {
    val currentDestination: NavBackStackEntry?
    fun gotoLogin()

    fun gotoRoom(roomId: PrimaryKey, showDialog: Boolean)

    fun gotoCommunity(communityId: PrimaryKey, showDialog: Boolean)

    fun gotoTopic(topicId: PrimaryKey)

    fun gotoHome()

    fun gotoTopicCompose(objectType: ObjectType, objectId: PrimaryKey)

    fun gotoMemberPage(objectId: PrimaryKey, objectType: ObjectType)

    fun goto(id: PrimaryKey, type: ObjectType) {
        when (type) {
            COMMUNITY -> gotoCommunity(id, false)
            ROOM -> gotoRoom(id, false)
            TOPIC -> gotoTopic(id)
            USER -> {
            }
        }
    }

    companion object {
        val EMPTY = object : AppNav {
            override val currentDestination: NavBackStackEntry?
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
                objectId: PrimaryKey
            ) {
                TODO("Not yet implemented")
            }

            override fun gotoMemberPage(
                objectId: PrimaryKey,
                objectType: ObjectType
            ) {
                TODO("Not yet implemented")
            }
        }
    }
}
