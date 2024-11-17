package com.storyteller_f.a.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade
import coil3.util.DebugLogger
import com.russhwolf.settings.Settings
import com.storyteller_f.a.app.common.getOrCreateCollection
import com.storyteller_f.a.app.community.CommunityPage
import com.storyteller_f.a.app.compontents.EventDialog
import com.storyteller_f.a.app.compontents.EventState
import com.storyteller_f.a.app.room.RoomPage
import com.storyteller_f.a.app.topic.TopicComposePage
import com.storyteller_f.a.app.topic.TopicPage
import com.storyteller_f.a.app.topic.processEncryptedTopic
import com.storyteller_f.a.app.ui.theme.AppTheme
import com.storyteller_f.a.client_lib.ClientWebSocket
import com.storyteller_f.a.client_lib.LoginViewModel
import com.storyteller_f.a.client_lib.addRequestHeaders
import com.storyteller_f.a.client_lib.defaultClientConfigure
import com.storyteller_f.a.client_lib.getClient
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.ObjectType.*
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.toPrimaryKey
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import kotbase.MutableDocument
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.tlaster.precompose.PreComposeApp
import moe.tlaster.precompose.navigation.*
import moe.tlaster.precompose.navigation.path

object StaticObj {
    init {
        Napier.base(DebugAntilog())
    }
}

val globalDialogState = EventState()

val LocalAppNav = compositionLocalOf {
    AppNav.EMPTY
}

@OptIn(ExperimentalCoilApi::class)
@Composable
fun App() {
    StaticObj
    AppTheme(dynamicColor = false) {
        setSingletonImageLoaderFactory {
            getAsyncImageLoader(it)
        }
        val navigator = rememberNavigator()
        val appNav = remember {
            newAppNav(navigator)
        }
        CompositionLocalProvider(LocalAppNav provides appNav) {
            EventDialog(globalDialogState)
            PreComposeApp {
                NavHost(navigator, initialRoute = "/home") {
                    buildRootNav(navigator)
                }
            }
        }
    }
}

private fun RouteBuilder.buildRootNav(
    navigator: Navigator
) {
    scene("/home") {
        HomePage()
    }
    scene("/login") {
        LoginPage()
    }
    scene("/community/{communityId}") {
        val communityId = it.path2<PrimaryKey>("communityId", null)
        if (communityId != null) {
            CommunityPage(communityId)
        }
    }
    scene("/room/{roomId}") {
        val roomId = it.path2<PrimaryKey>("roomId", null)
        if (roomId != null) {
            RoomPage(roomId)
        }
    }
    scene("/topic/{topicId}") {
        val topicId = it.path2<PrimaryKey>("topicId", null)
        if (topicId != null) {
            TopicPage(topicId)
        }
    }
    scene("/topic-compose/{objectType}/{objectId}") {
        val objectType = it.path<Int>("objectType")?.let {
            ObjectType.entries.first { t ->
                t.ordinal == it
            }
        }
        val objectId = it.path2<PrimaryKey>("objectId")
        if (objectType != null && objectId != null) {
            TopicComposePage(objectType, objectId) {
                navigator.goBack()
            }
        }
    }
}

private fun newAppNav(navigator: Navigator) = object : AppNav {
    override fun gotoLogin() {
        navigator.navigate("/login")
    }

    override fun gotoRoom(roomId: PrimaryKey) {
        navigator.navigate("/room/$roomId")
    }

    override fun gotoCommunity(communityId: PrimaryKey) {
        navigator.navigate("/community/$communityId")
    }

    override fun gotoTopic(topicId: PrimaryKey) {
        navigator.navigate("/topic/$topicId")
    }

    override fun gotoHome() {
        navigator.navigate("/home")
    }

    override fun gotoTopicCompose(objectType: ObjectType, objectId: PrimaryKey) {
        navigator.navigate("/topic-compose/${objectType.ordinal}/$objectId")
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

val bus = Channel<Any> {
}

val settings = Settings()

interface AppNav {
    fun gotoLogin()

    fun gotoRoom(roomId: PrimaryKey)

    fun gotoCommunity(communityId: PrimaryKey)

    fun gotoTopic(topicId: PrimaryKey)

    fun gotoHome()

    fun gotoTopicCompose(objectType: ObjectType, objectId: PrimaryKey)

    fun goto(id: PrimaryKey, type: ObjectType) {
        when (type) {
            COMMUNITY -> gotoCommunity(id)
            ROOM -> gotoRoom(id)
            TOPIC -> gotoTopic(id)
            USER -> {
            }
        }
    }

    companion object {
        val EMPTY = object : AppNav {
            override fun gotoLogin() {
                TODO("Not yet implemented")
            }

            override fun gotoRoom(roomId: PrimaryKey) {
                TODO("Not yet implemented")
            }

            override fun gotoCommunity(communityId: PrimaryKey) {
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
        }
    }
}

inline fun <reified T> BackStackEntry.path2(path: String, default: T? = null): T? {
    val value = pathMap[path] ?: return default
    return if (T::class == PrimaryKey::class) value.toPrimaryKey() as T else convertValue(value)
}
