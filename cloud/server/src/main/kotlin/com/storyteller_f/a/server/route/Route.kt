package com.storyteller_f.a.server.route

import com.maxmind.geoip2.DatabaseReader
import com.storyteller_f.a.server.auth.bindProtectedAccountRoute
import com.storyteller_f.a.server.auth.bindSafeAccountRoute
import com.storyteller_f.a.server.auth.bindUnprotectedAccountRoute
import com.storyteller_f.a.server.webSocketContent
import com.storyteller_f.backend.service.Backend
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*

fun Application.configureRoute(reader: DatabaseReader, backend: Backend) {
    routing {
        authenticate {
            bindProtectedSafeRoomRoute(backend)
            bindProtectedSafeTopicRoute(backend)
            bindProtectedSafeCommunityRoute(backend)
            bindProtectedSafeUserRoute(backend)
            webSocket("/link") {
                webSocketContent(reader, backend)
            }
            bindProtectedSafeMediaRoute(backend)
            bindProtectedTitleRoute(backend)
            bindProtectedAccountRoute(backend)
        }
        authenticate(optional = true) {
            bindSafeAccountRoute(backend)
            bindSafeRoomRoute(backend)
            bindSafeTopicRoute(backend)
            bindSafeCommunityRoute(backend)
            bindSafeUserRoute(backend)
        }
        bindUnprotectedAccountRoute(backend)
        bindUnauthenticatedRoute(backend)
    }
}
