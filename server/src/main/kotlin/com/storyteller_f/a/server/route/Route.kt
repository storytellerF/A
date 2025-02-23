package com.storyteller_f.a.server.route

import com.maxmind.geoip2.DatabaseReader
import com.storyteller_f.Backend
import com.storyteller_f.a.server.auth.*
import com.storyteller_f.a.server.common.checkParameter
import com.storyteller_f.a.server.route.RouteTopics.Id
import com.storyteller_f.a.server.webSocketContent
import com.storyteller_f.media.FileSystemMediaService
import com.storyteller_f.shared.model.MediaResponse
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.obj.TitleSearchType
import com.storyteller_f.shared.obj.TopicPinSearch
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.TitleType
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*

@Resource("/communities")
class RouteCommunities(val aid: String? = null, val fillJoinInfo: Boolean? = null) {
    @Resource("search")
    class Search(
        @Suppress("unused") val parent: RouteCommunities = RouteCommunities(),
        val joinStatus: JoinStatusSearch? = null,
        val word: String? = null,
        val target: PrimaryKey? = null,
    )

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
class RouteMedia(val objectId: PrimaryKey, val objectType: ObjectType) {
    @Resource("upload")
    class Upload(@Suppress("unused") val parent: RouteMedia)
}

@Resource("/rooms")
class RouteRooms(val aid: String? = null, val fillJoinInfo: Boolean? = null) {
    @Resource("search")
    class Search(
        @Suppress("unused") val parent: RouteRooms = RouteRooms(),
        val joinStatus: JoinStatusSearch? = null,
        val word: String? = null,
        val community: PrimaryKey? = null,
    )

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
        val parentId: List<PrimaryKey>? = null,
        val parentType: ObjectType? = null,
        val rootId: List<PrimaryKey>? = null,
        val rootType: ObjectType? = null,
        val author: PrimaryKey? = null,
    )

    @Resource("recommend")
    class Recommend(val parent: RouteTopics)

    @Resource("{id}")
    class Id(@Suppress("unused") val parent: RouteTopics, val id: PrimaryKey) {
        @Resource("snapshot")
        class Snapshot(val parent: Id)

        @Resource("topics")
        class Topics(val parent: Id, val pinType: TopicPinSearch?  = null)

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
class RouteUsers(val aid: String? = null) {
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

    @Resource("update")
    class Update(@Suppress("unused") val parent: RouteUsers = RouteUsers())

    @Resource("search")
    class Search(@Suppress("unused") val parent: RouteUsers, val word: String? = null)
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

fun Application.commonRoute(backend: Backend, reader: DatabaseReader) {
    routing {
        authenticate {
            bindProtectedSafeRoomRoute(backend, reader)
            bindProtectedSafeTopicRoute(backend, reader)
            bindProtectedSafeCommunityRoute(backend, reader)
            bindProtectedSafeUserRoute(reader, backend)
            webSocket("/link") {
                webSocketContent(backend, reader)
            }
            bindProtectedAccountRoute(reader)
            bindProtectedSafeMediaRoute(backend, reader)
            bindProtectedTitleRoute(backend, reader)
        }
        authenticate(optional = true) {
            bindSafeRoomRoute(backend, reader)
            bindSafeTopicRoute(backend, reader)
            bindSafeCommunityRoute(backend, reader)
            bindSafeUserRoute(backend, reader)
        }
        bindUnprotectedAccountRoute(backend, reader)
        bindUnauthenticatedRoute(backend, reader)
    }
}

fun Routing.bindUnauthenticatedRoute(backend: Backend, reader: DatabaseReader) {
    get("/ping") {
        call.respondText("pong")
    }

    get("/amedia/{path...}") {
        omitPrincipal(reader) {
            checkParameter<List<String>, MediaResponse>("path") { paths ->
                val service = backend.mediaService
                if (service is FileSystemMediaService) {
                    val file = service.getResponse(paths)
                    if (file?.exists() == true) {
                        val value = MediaResponse(file.path, ContentType.defaultForFile(file).toString())
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
        call.respondText("${backend.config.flavor} ${backend.config.isProd}")
    }
}
