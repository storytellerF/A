package com.storyteller_f.a.server.route

import com.storyteller_f.Backend
import com.storyteller_f.a.server.auth.UserSession
import com.storyteller_f.a.server.auth.bindUnauthenticatedRoute
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.webSocketContent
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import io.ktor.utils.io.*

fun Application.commonRoute(backend: Backend) {
    routing {
        authenticate {
            bindProtectedSafeTopicRoute(backend)
            bindProtectedSafeCommunityRoute(backend)
            bindProtectedSafeRoomRoute(backend)
            bindProtectedSafeUserRoute()
            webSocket("/link") {
                webSocketContent(backend)
            }
            post("/sign_out") {
                usePrincipal { _ ->
                    call.sessions.clear(UserSession::class)
                    Result.success(Unit)
                }
            }
            bindSafeMediaRoute(backend)
        }
        authenticate(optional = true) {
            bindSafeRoomRoute(backend)
            bindSafeTopicRoute(backend)
            bindSafeCommunityRoute(backend)
            bindSafeUserRoute(backend)
            bindProtectedSafeUserRoute()
        }
        bindUnauthenticatedRoute(backend)
    }
}
