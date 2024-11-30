package com.storyteller_f.a.server

import com.storyteller_f.Backend
import io.ktor.server.routing.*
import io.ktor.server.websocket.*

fun Route.protectedContent(backend: Backend) {
    bindProtectedSafeTopicRoute(backend)
    bindProtectedSafeCommunityRoute(backend)
    bindProtectedSafeRoomRoute(backend)
    bindProtectedSafeUserRoute()
    webSocket("/link") {
        webSocketContent(backend)
    }
}
