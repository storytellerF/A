package com.storyteller_f.a.server.route

import com.maxmind.geoip2.DatabaseReader
import com.storyteller_f.Backend
import com.storyteller_f.a.server.auth.bindProtectedAccountRoute
import com.storyteller_f.a.server.auth.bindUnprotectedAccountRoute
import com.storyteller_f.a.server.auth.omitPrincipal
import com.storyteller_f.a.server.common.PathResponse
import com.storyteller_f.a.server.common.checkParameter
import com.storyteller_f.a.server.webSocketContent
import com.storyteller_f.media.FileSystemMediaService
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PosterSearch
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.TitleSearchType
import com.storyteller_f.shared.type.TitleType
import com.storyteller_f.shared.type.TopicPinSearch
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlin.io.path.exists

@Resource("/communities")
class RouteCommunities(val fillJoinInfo: Boolean? = null) {
    @Resource("search")
    class Search(
        @Suppress("unused") val parent: RouteCommunities = RouteCommunities(),
        val joinStatus: JoinStatusSearch? = null,
        val word: String? = null,
        val target: PrimaryKey? = null,
        val hasPoster: PosterSearch? = null,
    )

    @Resource("aid")
    class Aid(val parent: RouteCommunities = RouteCommunities(), val aid: String? = null)

    @Resource("{id}")
    class Id(val parent: RouteCommunities = RouteCommunities(), val id: PrimaryKey) {
        @Resource("members")
        class Members(val parent: Id, val word: String? = null)

        @Resource("join")
        class Join(val parent: Id)

        @Resource("exit")
        class Exit(val parent: Id)

        @Resource("topics")
        class Topics(val parent: Id, val fillHasCommented: Boolean? = null, val pinType: TopicPinSearch? = null)
    }
}

@Resource("/amedia")
class RouteMedia(val objectId: PrimaryKey? = null, val objectType: ObjectType? = null) {
    @Resource("upload")
    class Upload(@Suppress("unused") val parent: RouteMedia)

    @Resource("copy")
    class Copy(val parent: RouteMedia)

    @Resource("all")
    class All(val parent: RouteMedia)

    @Resource("{id}")
    class Id(@Suppress("unused") val parent: RouteMedia = RouteMedia(), val id: PrimaryKey) {
        @Resource("extract-album")
        class ExtractAlbum(val parent: Id)
    }
}

@Resource("/rooms")
class RouteRooms(val fillJoinInfo: Boolean? = null) {
    @Resource("search")
    class Search(
        @Suppress("unused") val parent: RouteRooms = RouteRooms(),
        val joinStatus: JoinStatusSearch? = null,
        val word: String? = null,
        val community: PrimaryKey? = null,
    )

    @Resource("aid")
    class Aid(val parent: RouteRooms = RouteRooms(), val aid: String? = null)

    @Resource("{id}")
    class Id(val parent: RouteRooms = RouteRooms(), val id: PrimaryKey) {
        @Resource("members")
        class Members(val parent: Id, val word: String? = null)

        @Resource("join")
        class Join(val parent: Id)

        @Resource("pub-keys")
        class PubKeys(val parent: Id)

        @Resource("topics")
        class Topics(val parent: Id, val fillHasCommented: Boolean? = null, val pinType: TopicPinSearch? = null)

        @Resource("exit")
        class Exit(val parent: Id)
    }
}

@Resource("/topics")
class RouteTopics(val fillHasCommented: Boolean? = null, val aid: String? = null) {
    @Resource("search")
    class Search(
        @Suppress("unused") val parent: RouteTopics = RouteTopics(),
        val word: List<String>? = null,
        val parentId: PrimaryKey? = null,
        val parentType: ObjectType? = null,
    )

    @Resource("aid")
    class Aid(val parent: RouteTopics = RouteTopics(), val aid: String? = null)

    @Resource("recommend")
    class Recommend(val parent: RouteTopics)

    @Resource("{id}")
    class Id(@Suppress("unused") val parent: RouteTopics = RouteTopics(), val id: PrimaryKey) {
        @Resource("snapshot")
        class Snapshot(val parent: Id)

        @Resource("topics")
        class Topics(val parent: Id, val pinType: TopicPinSearch? = null)

        @Resource("reactions")
        class Reactions(val parent: Id, val fillHasReacted: Boolean? = null)

        @Resource("pin")
        class Pin(val parent: Id)

        @Resource("unpin")
        class Unpin(val parent: Id)
    }
}

@Resource("reactions")
class RouteReactions {
    @Resource("delete")
    class Delete(@Suppress("unused") val parent: RouteReactions)
}

@Resource("/users")
class RouteUsers {
    @Resource("{id}")
    class Id(@Suppress("unused") val parent: RouteUsers = RouteUsers(), val id: PrimaryKey) {
        @Resource("topics")
        class Topics(val parent: Id, val fillHasCommented: Boolean? = null, val pinType: TopicPinSearch? = null)

        @Resource("titles")
        class Titles(
            val parent: Id,
            val searchType: TitleSearchType,
            val type: TitleType? = null,
            val scopeId: PrimaryKey? = null,
            val status: PrimaryKey? = null
        )
    }

    @Resource("aid")
    class Aid(@Suppress("unused") val parent: RouteUsers = RouteUsers(), val aid: String? = null)

    @Resource("update")
    class Update(@Suppress("unused") val parent: RouteUsers = RouteUsers())

    @Resource("search")
    class Search(@Suppress("unused") val parent: RouteUsers, val word: String? = null)

    @Resource("read")
    class Read(val parent: RouteUsers)

    @Resource("devices")
    class Device(val parent: RouteUsers)
}

@Resource("/accounts")
class RouteAccounts {
    @Resource("sign_up")
    class SignUp(@Suppress("unused") val parent: RouteAccounts)

    @Resource("sign_in")
    class SignIn(@Suppress("unused") val parent: RouteAccounts)

    @Resource("sign_out")
    class SignOut(@Suppress("unused") val parent: RouteAccounts)

    @Resource("get_data")
    class GetData(@Suppress("unused") val parent: RouteAccounts)
}

@Resource("/titles")
class RouteTitles

fun Application.configureRoute(reader: DatabaseReader, backend: Backend) {
    routing {
        authenticate {
            bindProtectedSafeRoomRoute(reader, backend)
            bindProtectedSafeTopicRoute(reader, backend)
            bindProtectedSafeCommunityRoute(reader, backend)
            bindProtectedSafeUserRoute(reader, backend)
            webSocket("/link") {
                webSocketContent(reader, backend)
            }
            bindProtectedAccountRoute(reader)
            bindProtectedSafeMediaRoute(reader, backend)
            bindProtectedTitleRoute(reader, backend)
        }
        authenticate(optional = true) {
            bindSafeRoomRoute(reader, backend)
            bindSafeTopicRoute(reader, backend)
            bindSafeCommunityRoute(reader, backend)
            bindSafeUserRoute(reader, backend)
        }
        bindUnprotectedAccountRoute(reader, backend)
        bindUnauthenticatedRoute(reader, backend)
    }
}

fun Routing.bindUnauthenticatedRoute(reader: DatabaseReader, backend: Backend) {
    get("/ping") {
        call.respondText("pong")
    }

    get("/amedia/{path...}") {
        omitPrincipal(reader) {
            checkParameter<List<String>, PathResponse>("path") { paths ->
                val service = backend.mediaService
                if (service is FileSystemMediaService) {
                    val path = service.getResponse(paths)
                    if (path?.exists() == true) {
                        val value = PathResponse(path)
                        Result.success(value)
                    } else {
                        Result.success(null)
                    }
                } else {
                    Result.failure(BadRequestException("can't find file"))
                }
            }
        }
    }

    get {
        call.respondText("${backend.config.flavor} ${backend.config.buildType}")
    }
}
