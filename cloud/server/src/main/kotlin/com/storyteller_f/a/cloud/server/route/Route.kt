package com.storyteller_f.a.cloud.server.route

import com.maxmind.geoip2.DatabaseReader
import com.storyteller_f.a.api.core.AdminApi
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.cloud.core.service.adminSignIn
import com.storyteller_f.a.cloud.core.service.adminSignUp
import com.storyteller_f.a.cloud.core.service.getAllUsers
import com.storyteller_f.a.cloud.core.service.getOverview
import com.storyteller_f.a.cloud.server.auth.UserSession
import com.storyteller_f.a.cloud.server.auth.getData
import com.storyteller_f.a.cloud.server.auth.handleResult
import com.storyteller_f.a.cloud.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.cloud.server.common.pagination
import com.storyteller_f.a.cloud.server.webSocketContent
import com.storyteller_f.route4k.ktor.server.invoke
import com.storyteller_f.route4k.ktor.server.receiveBody
import com.storyteller_f.shared.utils.UNIT_RESULT
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.sessions
import io.ktor.server.websocket.*

fun Application.configureRoute(reader: DatabaseReader, backend: Backend) {
    routing {
        authenticate("user") {
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
        authenticate("user", optional = true) {
            bindAccountRoute()
            bindRoomRoute(backend)
            bindTopicRoute(backend)
            bindCommunityRoute(backend)
            bindUserRoute(backend)
        }
        bindUnprotectedAccountRoute(backend)
        bindUnauthenticatedRoute(backend)
        bindProtectedAdminRoute(backend)
        bindUnauthenticatedPanelRoute(backend)
    }
}

private fun Routing.bindProtectedAdminRoute(backend: Backend) {
    authenticate("admin") {
        AdminApi.Users.get.invoke(RoutingContext::handleResult) {
            it.pagination(IdentifiablePagingGenerator) { fetch ->
                backend.getAllUsers(fetch)
            }
        }
        AdminApi.signOut(RoutingContext::handleResult) {
            call.sessions.clear(UserSession::class)
            UNIT_RESULT
        }
        AdminApi.overview(RoutingContext::handleResult) {
            backend.getOverview()
        }
    }
}

private fun Routing.bindUnauthenticatedPanelRoute(backend: Backend) {
    AdminApi.signIn.invoke(RoutingContext::handleResult) { api ->
        backend.adminSignIn(call.getData(), api.receiveBody()).onSuccess {
            saveSuccessSessionOnFirst(it.id)
        }
    }
    AdminApi.signUp.invoke(RoutingContext::handleResult) {
        backend.adminSignUp(call.getData(), it.receiveBody())
    }
    AdminApi.getData.invoke(RoutingContext::handleResult) {
        Result.success(call.getData())
    }
}
