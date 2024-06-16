package com.storyteller_f.a.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade
import coil3.util.DebugLogger
import com.russhwolf.settings.Settings
import com.storyteller_f.a.client_lib.ClientWebSocket
import com.storyteller_f.a.app.common.getOrCreateCollection
import com.storyteller_f.a.app.community.CommunityPage
import com.storyteller_f.a.app.room.RoomPage
import com.storyteller_f.a.app.topic.TopicComposePage
import com.storyteller_f.a.app.topic.TopicPage
import com.storyteller_f.a.app.ui.theme.AppTheme
import com.storyteller_f.a.client_lib.LoginViewModel
import com.storyteller_f.a.client_lib.addRequestHeaders
import com.storyteller_f.a.client_lib.defaultClientConfigure
import com.storyteller_f.a.client_lib.getClient
import com.storyteller_f.shared.obj.RoomFrame
import com.storyteller_f.shared.type.OKey
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.ObjectType.COMMUNITY
import com.storyteller_f.shared.type.ObjectType.ROOM
import com.storyteller_f.shared.type.ObjectType.TOPIC
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
import moe.tlaster.precompose.navigation.NavHost
import moe.tlaster.precompose.navigation.path
import moe.tlaster.precompose.navigation.rememberNavigator

@OptIn(ExperimentalCoilApi::class)
@Composable
fun App() {
    AppTheme(dynamicColor = false) {
        setSingletonImageLoaderFactory {
            getAsyncImageLoader(it)
        }
        PreComposeApp {
            val navigator = rememberNavigator()
            val appNav = remember {
                object : AppNav {
                    override fun gotoLogin() {
                        navigator.navigate("/login")
                    }

                    override fun gotoRoom(roomId: OKey) {
                        navigator.navigate("/room/$roomId")
                    }

                    override fun gotoCommunity(communityId: OKey) {
                        navigator.navigate("/community/$communityId")
                    }

                    override fun gotoTopic(topicId: OKey) {
                        navigator.navigate("/topic/$topicId")
                    }

                    override fun gotoHome() {
                        navigator.navigate("/home")
                    }

                    override fun gotoTopicCompose(objectType: ObjectType, objectId: OKey) {
                        navigator.navigate("/topic-compose/${objectType.ordinal}/$objectId")
                    }

                }
            }
            val onClick = { id: OKey, type: ObjectType ->
                when (type) {
                    COMMUNITY -> appNav.gotoCommunity(id)
                    ROOM -> appNav.gotoRoom(id)
                    TOPIC -> appNav.gotoTopic(id)
                }
            }
            NavHost(navigator, initialRoute = "/home") {
                scene("/home") {
                    HomePage(appNav, onClick)
                }
                scene("/login") {
                    LoginPage(appNav::gotoHome)
                }
                scene("/community/{communityId}") {
                    val communityId = it.path<OKey>("communityId", null)
                    if (communityId != null)
                        CommunityPage(communityId, {
                            appNav.gotoTopicCompose(COMMUNITY, communityId)
                        }, onClick)
                }
                scene("/room/{roomId}") {
                    val roomId = it.path<OKey>("roomId", null)
                    if (roomId != null)
                        RoomPage(roomId, onClick)
                }
                scene("/topic/{topicId}") {
                    val topicId = it.path<OKey>("topicId", null)
                    if (topicId != null)
                        TopicPage(topicId, onClick)
                }
                scene("/topic-compose/{objectType}/{objectId}") {
                    val objectType = it.path<Int>("objectType")?.let {
                        ObjectType.entries.first { t ->
                            t.ordinal == it
                        }
                    }
                    val objectId = it.path<OKey>("objectId")
                    if (objectType != null && objectId != null) {
                        TopicComposePage(objectType, objectId) {
                            navigator.goBack()
                        }
                    }
                }
            }

        }

    }
}

fun getAsyncImageLoader(context: PlatformContext) =
    ImageLoader.Builder(context).crossfade(true).logger(DebugLogger()).build()

val client by lazy {
    getClient {
        defaultClientConfigure()
        setupRequest()
    }.also {
        Napier.base(DebugAntilog())
    }
}

val clientWs by lazy {
    ClientWebSocket({
        client.webSocketSession("/link") {
            addRequestHeaders(LoginViewModel.session?.first)
        }
    }) {
        if (it is RoomFrame.NewTopicInfo) {
            val topicInfo = it.topicInfo
            getOrCreateCollection("topics").save(
                MutableDocument(
                    topicInfo.id.toString(),
                    Json.encodeToString(topicInfo)
                )
            )
            Napier.v(tag = "pagination") {
                "save document $topicInfo"
            }
        }
    }
}

const val BASE_URL = BuildKonfig.SERVER_URL
const val WEB_SOCKET_URL = BuildKonfig.WS_SERVER_URL

fun HttpClientConfig<*>.setupRequest() {
    defaultRequest {
        url(BASE_URL)
    }
}

val bus = Channel<Any> {

}

val settings = Settings()

interface AppNav {
    fun gotoLogin()

    fun gotoRoom(roomId: OKey)

    fun gotoCommunity(communityId: OKey)

    fun gotoTopic(topicId: OKey)

    fun gotoHome()

    fun gotoTopicCompose(objectType: ObjectType, objectId: OKey)
}
