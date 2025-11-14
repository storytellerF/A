package com.storyteller_f.a.cloud.server.route

import com.storyteller_f.a.api.AdminApi
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.cloud.core.service.addUser
import com.storyteller_f.a.cloud.core.service.adminSignIn
import com.storyteller_f.a.cloud.core.service.adminSignUp
import com.storyteller_f.a.cloud.core.service.getAllCommunities
import com.storyteller_f.a.cloud.core.service.getAllFileInfos
import com.storyteller_f.a.cloud.core.service.getAllPrivateRooms
import com.storyteller_f.a.cloud.core.service.getAllPublicRooms
import com.storyteller_f.a.cloud.core.service.getAllTitles
import com.storyteller_f.a.cloud.core.service.getAllTopics
import com.storyteller_f.a.cloud.core.service.getAllUsers
import com.storyteller_f.a.cloud.core.service.getOverview
import com.storyteller_f.a.cloud.server.auth.UserSession
import com.storyteller_f.a.cloud.server.auth.getData
import com.storyteller_f.a.cloud.server.auth.handleResult
import com.storyteller_f.a.cloud.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.cloud.server.common.pagination
import com.storyteller_f.route4k.ktor.server.invoke
import com.storyteller_f.route4k.ktor.server.receiveBody
import com.storyteller_f.shared.utils.UNIT_RESULT
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Routing
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.sessions

fun Routing.bindProtectedAdminRoute(backend: Backend) {
    authenticate("admin") {
        AdminApi.Users.get(handleResult()) {
            it.pagination(IdentifiablePagingGenerator) { fetch ->
                backend.getAllUsers(fetch)
            }
        }
        AdminApi.Communities.get(handleResult()) {
            it.pagination(IdentifiablePagingGenerator) { fetch ->
                backend.getAllCommunities(fetch)
            }
        }
        AdminApi.Rooms.getPublic(handleResult()) {
            it.pagination(IdentifiablePagingGenerator) { fetch ->
                backend.getAllPublicRooms(fetch)
            }
        }
        AdminApi.Rooms.getPrivate(handleResult()) {
            it.pagination(IdentifiablePagingGenerator) { fetch ->
                backend.getAllPrivateRooms(fetch)
            }
        }
        AdminApi.Topics.get(handleResult()) {
            it.pagination(IdentifiablePagingGenerator) { fetch ->
                backend.getAllTopics(fetch)
            }
        }
        AdminApi.Titles.get(handleResult()) {
            it.pagination(IdentifiablePagingGenerator) { fetch ->
                backend.getAllTitles(fetch)
            }
        }
        AdminApi.Files.get(handleResult()) {
            it.pagination(IdentifiablePagingGenerator) { fetch ->
                backend.getAllFileInfos(fetch)
            }
        }
        AdminApi.signOut(handleResult()) {
            call.sessions.clear(UserSession::class)
            UNIT_RESULT
        }
        AdminApi.overview(handleResult()) {
            backend.getOverview()
        }
        AdminApi.Users.add(handleResult(), { api ->
            backend.addUser(api.receiveBody())
        })
    }
}

fun Routing.bindUnauthenticatedPanelRoute(backend: Backend) {
    AdminApi.signIn.invoke(handleResult()) { api ->
        backend.adminSignIn(call.getData(), api.receiveBody()).onSuccess {
            saveSuccessSessionOnFirst(it.id)
        }
    }
    AdminApi.signUp.invoke(handleResult()) {
        backend.adminSignUp(call.getData(), it.receiveBody())
    }
    AdminApi.getData.invoke(handleResult()) {
        Result.success(call.getData())
    }
}
