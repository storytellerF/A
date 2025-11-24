package com.storyteller_f.a.cloud.server.route

import com.storyteller_f.a.api.AdminApi
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.ObjectFetch
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
import com.storyteller_f.a.cloud.core.service.getCommunity
import com.storyteller_f.a.cloud.core.service.getCommunityMemberInfos
import com.storyteller_f.a.cloud.core.service.getFileInfoById
import com.storyteller_f.a.cloud.core.service.getFileInfoPaginationResult
import com.storyteller_f.a.cloud.core.service.getOverview
import com.storyteller_f.a.cloud.core.service.getRoomInfo
import com.storyteller_f.a.cloud.core.service.getRoomMemberInfos
import com.storyteller_f.a.cloud.core.service.getTitleInfo
import com.storyteller_f.a.cloud.core.service.getUserById
import com.storyteller_f.a.cloud.core.service.getUserCommentedTopics
import com.storyteller_f.a.cloud.core.service.getUserJoinedCommunities
import com.storyteller_f.a.cloud.core.service.getUserJoinedRooms
import com.storyteller_f.a.cloud.core.service.getUserLogs
import com.storyteller_f.a.cloud.core.service.getUserOverview
import com.storyteller_f.a.cloud.core.service.getUserReactions
import com.storyteller_f.a.cloud.core.service.getUserTitles
import com.storyteller_f.a.cloud.core.service.getUserUploadRecords
import com.storyteller_f.a.cloud.core.service.uncheckGetTopicById
import com.storyteller_f.a.cloud.core.service.uncheckGetTopicsByParentId
import com.storyteller_f.a.cloud.server.auth.UserSession
import com.storyteller_f.a.cloud.server.auth.getData
import com.storyteller_f.a.cloud.server.auth.handleResult
import com.storyteller_f.a.cloud.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.cloud.server.common.pagination
import com.storyteller_f.a.cloud.server.common.pagingGenerator
import com.storyteller_f.endpoint4k.ktor.server.invoke
import com.storyteller_f.endpoint4k.ktor.server.receiveBody
import com.storyteller_f.shared.utils.UNIT_RESULT
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Routing
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.sessions

fun Routing.bindProtectedAdminRoute(backend: Backend) {
    authenticate("admin") {
        bindAdminUserRoutes(backend)
        AdminApi.Communities.get(handleResult()) {
            it.pagination(IdentifiablePagingGenerator) { fetch ->
                backend.getAllCommunities(fetch)
            }
        }
        AdminApi.Communities.Id.get(handleResult()) { p ->
            backend.getCommunity(ObjectFetch.IdFetch(p.id), null, null)
        }
        AdminApi.Communities.Id.Members.get(handleResult()) { q, p ->
            q.pagination(IdentifiablePagingGenerator) { f ->
                backend.getCommunityMemberInfos(p.id, f)
            }
        }
        bindAdminRoomRoutes(backend)
        AdminApi.Topics.get(handleResult()) {
            it.pagination(IdentifiablePagingGenerator) { fetch ->
                backend.getAllTopics(fetch)
            }
        }
        AdminApi.Topics.Id.get(handleResult()) { p ->
            backend.uncheckGetTopicById(p.id, null)
        }
        AdminApi.Topics.Id.Topics.get(handleResult()) { q, p ->
            q.pagination(IdentifiablePagingGenerator) { f ->
                backend.uncheckGetTopicsByParentId(null, p.id, f, q.pinType)
            }
        }
        AdminApi.Titles.get(handleResult()) {
            it.pagination(IdentifiablePagingGenerator) { fetch ->
                backend.getAllTitles(fetch)
            }
        }
        AdminApi.Titles.Id.get(handleResult()) { p ->
            backend.getTitleInfo(p.id)
        }
        AdminApi.Files.get(handleResult()) {
            it.pagination(IdentifiablePagingGenerator) { fetch ->
                backend.getAllFileInfos(fetch)
            }
        }
        AdminApi.Files.Id.get(handleResult()) { p ->
            backend.getFileInfoById(p.id)
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

private fun Routing.bindAdminRoomRoutes(backend: Backend) {
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
    AdminApi.Rooms.Id.get(handleResult()) { p ->
        backend.getRoomInfo(ObjectFetch.IdFetch(p.id), null, null)
    }
    AdminApi.Rooms.Id.Members.get(handleResult()) { q, p ->
        q.pagination(IdentifiablePagingGenerator) { f ->
            backend.getRoomMemberInfos(p.id, f)
        }
    }
    AdminApi.Rooms.Id.Files.get(handleResult()) { q, p ->
        q.pagination(IdentifiablePagingGenerator) { f ->
            backend.getFileInfoPaginationResult(p.id, f)
        }
    }
}

private fun Routing.bindAdminUserRoutes(backend: Backend) {
    AdminApi.Users.get(handleResult()) {
        it.pagination(IdentifiablePagingGenerator) { fetch ->
            backend.getAllUsers(fetch)
        }
    }
    AdminApi.Users.Id.get(handleResult()) { p ->
        backend.getUserById(p.id)
    }
    AdminApi.Users.Id.Overview.get(handleResult()) { p ->
        backend.getUserOverview(p.id)
    }
    AdminApi.Users.Id.Communities.get(handleResult()) { q, p ->
        q.pagination(IdentifiablePagingGenerator) { f ->
            backend.getUserJoinedCommunities(p.id, f)
        }
    }
    AdminApi.Users.Id.Rooms.get(handleResult()) { q, p ->
        q.pagination(IdentifiablePagingGenerator) { f ->
            backend.getUserJoinedRooms(p.id, f)
        }
    }
    AdminApi.Users.Id.Titles.get(handleResult()) { q, p ->
        q.pagination(IdentifiablePagingGenerator) { f ->
            backend.getUserTitles(p.id, q.searchType, q.type, q.scopeId, f)
        }
    }
    AdminApi.Users.Id.Files.get(handleResult()) { q, p ->
        q.pagination(IdentifiablePagingGenerator) { f ->
            backend.getFileInfoPaginationResult(p.id, f)
        }
    }
    AdminApi.Users.Id.Logs.get(handleResult()) { q, p ->
        q.pagination(pagingGenerator {
            it.id
        }) { f ->
            backend.getUserLogs(p.id, f)
        }
    }
    AdminApi.Users.Id.UploadRecords.get(handleResult()) { q, p ->
        q.pagination(pagingGenerator {
            it.id
        }) { f ->
            backend.getUserUploadRecords(p.id, f)
        }
    }
    AdminApi.Users.Id.Reactions.get(handleResult()) { q, p ->
        q.pagination(pagingGenerator {
            it.id
        }) { f ->
            backend.getUserReactions(p.id, f)
        }
    }
    AdminApi.Users.Id.Comments.get(handleResult()) { q, p ->
        q.pagination(IdentifiablePagingGenerator) { f ->
            backend.getUserCommentedTopics(p.id, f)
        }
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
