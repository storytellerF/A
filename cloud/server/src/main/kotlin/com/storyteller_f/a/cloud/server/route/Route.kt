package com.storyteller_f.a.cloud.server.route

import com.maxmind.geoip2.DatabaseReader
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.cloud.server.auth.bindAccountRoute
import com.storyteller_f.a.cloud.server.auth.bindProtectedAccountRoute
import com.storyteller_f.a.cloud.server.auth.bindUnprotectedAccountRoute
import com.storyteller_f.a.cloud.server.webSocketContent
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*

fun Application.configureRoute(reader: DatabaseReader, backend: Backend) {
    routing {
        authenticate {
            bindProtectedRoomRoute(backend)
            bindProtectedTopicRoute(backend)
            bindProtectedCommunityRoute(backend)
            bindProtectedUserRoute(backend)
            webSocket("/link") {
                webSocketContent(reader, backend)
            }
            bindProtectedMediaRoute(backend)
            bindProtectedTitleRoute(backend)
            bindProtectedAccountRoute(backend)
        }
        authenticate(optional = true) {
            bindAccountRoute()
            bindRoomRoute(backend)
            bindTopicRoute(backend)
            bindCommunityRoute(backend)
            bindUserRoute(backend)
        }
        bindUnprotectedAccountRoute(backend)
        bindUnauthenticatedRoute(backend)
    }
}
