package com.storyteller_f.a.server.route

import com.storyteller_f.Backend
import com.storyteller_f.a.server.auth.*
import com.storyteller_f.a.server.common.checkParameter
import com.storyteller_f.a.server.webSocketContent
import com.storyteller_f.shared.model.MediaResponse
import com.storyteller_f.shared.obj.JoinStatusSearch
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import java.io.File

@Resource("/communities")
class RouteCommunities(val aid: String? = null, val fillJoinInfo: Boolean? = null) {
    @Resource("search")
    class Search(
        @Suppress("unused") val parent: RouteCommunities = RouteCommunities(),
        val joinStatus: JoinStatusSearch? = null,
        val word: String? = null
    )

    @Resource("{id}")
    class Id(val parent: RouteCommunities = RouteCommunities(), val id: PrimaryKey) {
        @Resource("members")
        class Members(val parent: Id, val word: String? = null)

        @Resource("join")
        class Join(val parent: Id)

        @Resource("exit")
        class Exit(val parent: Id)
    }
}

@Resource("/amedia")
class RouteMedia(val objectId: PrimaryKey? = null, val objectType: ObjectType? = null) {
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
        class Topics(val parent: Id, val fillHasCommented: Boolean? = null)

        @Resource("exit")
        class Exit(val parent: Id)
    }
}

@Resource("/topics")
class RouteTopics(val fillHasCommented: Boolean? = null) {
    @Resource("search")
    class Search(
        @Suppress("unused") val parent: RouteTopics = RouteTopics(),
        val word: List<String>? = null,
        val parentId: PrimaryKey? = null,
        val parentType: ObjectType? = null,
        val rootId: PrimaryKey? = null,
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
        class Topics(val parent: Id)

        @Resource("reactions")
        class Reactions(val parent: Id)
    }
}

@Resource("reactions")
class RouteReactions {
    @Resource("delete")
    class Delete(val parent: RouteReactions)
}

@Resource("/users")
class RouteUsers(val aid: String? = null) {
    @Resource("{id}")
    class Id(@Suppress("unused") val parent: RouteUsers = RouteUsers(), val id: PrimaryKey)

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
    class SignIn(@Suppress("unused")val parent: RouteAccounts)

    @Resource("sign_out")
    class SignOut(@Suppress("unused")val parent: RouteAccounts)

    @Resource("get_data")
    class GetData(@Suppress("unused")val parent: RouteAccounts)
}

fun Application.commonRoute(backend: Backend) {
    routing {
        authenticate {
            bindProtectedSafeRoomRoute(backend)
            bindProtectedSafeTopicRoute(backend)
            bindProtectedSafeCommunityRoute(backend)
            bindProtectedSafeUserRoute()
            webSocket("/link") {
                webSocketContent(backend)
            }
            bindProtectedAccountRoute()
            bindProtectedSafeMediaRoute(backend)
        }
        authenticate(optional = true) {
            bindSafeRoomRoute(backend)
            bindSafeTopicRoute(backend)
            bindSafeCommunityRoute(backend)
            bindSafeUserRoute(backend)
        }
        bindUnprotectedAccountRoute(backend)
        bindUnauthenticatedRoute(backend)
    }
}

fun Routing.bindUnauthenticatedRoute(backend: Backend) {
    get("/ping") {
        call.respondText("pong")
    }

    get("/amedia/{path...}") {
        omitPrincipal {
            checkParameter<List<String>, MediaResponse>("path") {
                val userHome = System.getProperty("user.home")
                val file = File(userHome, "a/amedia/${it.joinToString("/")}")
                val value = MediaResponse(file.path, ContentType.defaultForFile(file).toString())
                Result.success(value)
            }
        }
    }

    get {
        call.respondText("${backend.config.flavor} ${backend.config.isProd}")
    }
}
