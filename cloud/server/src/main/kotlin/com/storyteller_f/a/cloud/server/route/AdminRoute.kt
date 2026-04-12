package com.storyteller_f.a.cloud.server.route

import com.storyteller_f.a.api.AdminApi
import com.storyteller_f.a.api.CommunityInfoListResponse
import com.storyteller_f.a.api.FileInfoListResponse
import com.storyteller_f.a.api.FileRefInfoListResponse
import com.storyteller_f.a.api.MemberInfoListResponse
import com.storyteller_f.a.api.ReactionRecordInfoListResponse
import com.storyteller_f.a.api.RoomInfoListResponse
import com.storyteller_f.a.api.TitleInfoListResponse
import com.storyteller_f.a.api.TopicInfoListResponse
import com.storyteller_f.a.api.UploadRecordInfoListResponse
import com.storyteller_f.a.api.UserFavoriteInfoListResponse
import com.storyteller_f.a.api.UserInfoListResponse
import com.storyteller_f.a.api.UserLogInfoListResponse
import com.storyteller_f.a.api.UserSubscriptionInfoListResponse
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
import com.storyteller_f.a.cloud.core.service.getFavorites
import com.storyteller_f.a.cloud.core.service.getFileInfoById
import com.storyteller_f.a.cloud.core.service.getFileInfoPaginationResult
import com.storyteller_f.a.cloud.core.service.getOverview
import com.storyteller_f.a.cloud.core.service.getRoomInfo
import com.storyteller_f.a.cloud.core.service.getTitleInfo
import com.storyteller_f.a.cloud.core.service.getUserById
import com.storyteller_f.a.cloud.core.service.getUserCommentedTopics
import com.storyteller_f.a.cloud.core.service.getUserJoinedCommunities
import com.storyteller_f.a.cloud.core.service.getUserJoinedRooms
import com.storyteller_f.a.cloud.core.service.getUserLogs
import com.storyteller_f.a.cloud.core.service.getUserOverview
import com.storyteller_f.a.cloud.core.service.getUserReactions
import com.storyteller_f.a.cloud.core.service.getUserSubscriptions
import com.storyteller_f.a.cloud.core.service.getUserTitles
import com.storyteller_f.a.cloud.core.service.getUserUploadRecords
import com.storyteller_f.a.cloud.core.service.uncheckGetTopicById
import com.storyteller_f.a.cloud.core.service.uncheckGetTopicsByParentId
import com.storyteller_f.a.cloud.core.service.uncheckedGetFileRefsByFileId
import com.storyteller_f.a.cloud.core.service.uncheckedGetRoomMemberInfos
import com.storyteller_f.a.cloud.core.service.uncheckedSearchFiles
import com.storyteller_f.a.cloud.core.service.updateCommunityStatus
import com.storyteller_f.a.cloud.core.service.updateFileStatus
import com.storyteller_f.a.cloud.core.service.updateRoomStatus
import com.storyteller_f.a.cloud.core.service.updateTitleStatus
import com.storyteller_f.a.cloud.core.service.updateTopicStatus
import com.storyteller_f.a.cloud.core.service.updateUserStatus
import com.storyteller_f.a.cloud.server.auth.UserSession
import com.storyteller_f.a.cloud.server.auth.getData
import com.storyteller_f.a.cloud.server.auth.handleResult
import com.storyteller_f.a.cloud.server.auth.usePrincipal
import com.storyteller_f.a.cloud.server.common.GeneralOffsetPagingGenerator
import com.storyteller_f.a.cloud.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.cloud.server.common.pagination
import com.storyteller_f.endpoint4k.ktor.server.invoke
import com.storyteller_f.endpoint4k.ktor.server.receiveBody
import com.storyteller_f.shared.utils.UNIT_RESULT
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.sessions

fun Route.bindProtectedAdminRoute(backend: Backend) {
    bindAdminUserRoutes(backend)
    bindAdminCommunityRoutes(backend)
    bindAdminRoomRoutes(backend)
    bindAdminTopicRoutes(backend)
    bindAdminTitleRoutes(backend)
    bindAdminFileRoutes(backend)
    AdminApi.signOut(handleResult()) {
        call.sessions.clear(UserSession::class)
        UNIT_RESULT
    }
    AdminApi.overview(handleResult()) {
        backend.getOverview()
    }
}

private fun Route.bindAdminTitleRoutes(backend: Backend) {
    AdminApi.Titles.get(handleResult()) {
        it.pagination(IdentifiablePagingGenerator, { l, p ->
            TitleInfoListResponse(l, p)
        }) { fetch ->
            backend.getAllTitles(fetch)
        }
    }
    AdminApi.Titles.Id.get(handleResult()) { p ->
        backend.getTitleInfo(p.id)
    }
    AdminApi.Titles.Id.Status.update(handleResult()) { p, api ->
        usePrincipal { uid ->
            backend.updateTitleStatus(p.id, api.receiveBody(), uid)
        }
    }
}

private fun Route.bindAdminCommunityRoutes(backend: Backend) {
    AdminApi.Communities.get(handleResult()) {
        it.pagination(IdentifiablePagingGenerator, { l, p ->
            CommunityInfoListResponse(l, p)
        }) { fetch ->
            backend.getAllCommunities(fetch)
        }
    }
    AdminApi.Communities.Id.get(handleResult()) { p ->
        backend.getCommunity(ObjectFetch.IdFetch(p.id), null, null)
    }
    AdminApi.Communities.Id.Status.update(handleResult()) { p, api ->
        usePrincipal { uid ->
            backend.updateCommunityStatus(p.id, api.receiveBody(), uid)
        }
    }
    AdminApi.Communities.Id.Members.get(handleResult()) { q, p ->
        q.pagination(IdentifiablePagingGenerator, { l, p ->
            MemberInfoListResponse(l, p)
        }) { f ->
            backend.getCommunityMemberInfos(p.id, f)
        }
    }
}

private fun Route.bindAdminFileRoutes(backend: Backend) {
    AdminApi.Files.get(handleResult()) {
        it.pagination(IdentifiablePagingGenerator, { l, p ->
            FileInfoListResponse(l, p)
        }) { fetch ->
            backend.getAllFileInfos(fetch)
        }
    }
    AdminApi.Files.search(handleResult()) {
        it.pagination(GeneralOffsetPagingGenerator, { l, p ->
            FileInfoListResponse(l, p)
        }) { fetch ->
            backend.uncheckedSearchFiles(it, fetch)
        }
    }
    AdminApi.Files.Id.get(handleResult()) { p ->
        backend.getFileInfoById(p.id)
    }
    AdminApi.Files.Id.Status.update(handleResult()) { p, api ->
        usePrincipal { uid ->
            backend.updateFileStatus(p.id, api.receiveBody(), uid)
        }
    }
    AdminApi.Files.Id.Refs.get(handleResult()) { q, p ->
        q.pagination(IdentifiablePagingGenerator, { l, p ->
            FileRefInfoListResponse(l, p)
        }) { f ->
            backend.uncheckedGetFileRefsByFileId(p.id, f)
        }
    }
}

private fun Route.bindAdminTopicRoutes(backend: Backend) {
    AdminApi.Topics.get(handleResult()) {
        it.pagination(IdentifiablePagingGenerator, { l, p ->
            TopicInfoListResponse(l, p)
        }) { fetch ->
            backend.getAllTopics(fetch)
        }
    }
    AdminApi.Topics.Id.get(handleResult()) { p ->
        backend.uncheckGetTopicById(p.id, null)
    }
    AdminApi.Topics.Id.Status.update(handleResult()) { p, api ->
        usePrincipal { uid ->
            backend.updateTopicStatus(p.id, api.receiveBody(), uid)
        }
    }
    AdminApi.Topics.Id.Topics.get(handleResult()) { q, p ->
        q.pagination(IdentifiablePagingGenerator, { l, p ->
            TopicInfoListResponse(l, p)
        }) { f ->
            backend.uncheckGetTopicsByParentId(null, p.id, f, q.pinType)
        }
    }
}

private fun Route.bindAdminRoomRoutes(backend: Backend) {
    AdminApi.Rooms.getPublic(handleResult()) {
        it.pagination(IdentifiablePagingGenerator, { l, p ->
            RoomInfoListResponse(l, p)
        }) { fetch ->
            backend.getAllPublicRooms(fetch)
        }
    }
    AdminApi.Rooms.getPrivate(handleResult()) {
        it.pagination(IdentifiablePagingGenerator, { l, p ->
            RoomInfoListResponse(l, p)
        }) { fetch ->
            backend.getAllPrivateRooms(fetch)
        }
    }
    AdminApi.Rooms.Id.get(handleResult()) { p ->
        backend.getRoomInfo(ObjectFetch.IdFetch(p.id), null, null)
    }
    AdminApi.Rooms.Id.Status.update(handleResult()) { p, api ->
        usePrincipal { uid ->
            backend.updateRoomStatus(p.id, api.receiveBody(), uid)
        }
    }
    AdminApi.Rooms.Id.Members.get(handleResult()) { q, p ->
        q.pagination(IdentifiablePagingGenerator, { l, p ->
            MemberInfoListResponse(l, p)
        }) { f ->
            backend.uncheckedGetRoomMemberInfos(p.id, f)
        }
    }
    AdminApi.Rooms.Id.Files.get(handleResult()) { q, p ->
        q.pagination(IdentifiablePagingGenerator, { l, p ->
            FileInfoListResponse(l, p)
        }) { f ->
            backend.getFileInfoPaginationResult(p.id, f)
        }
    }
}

private fun Route.bindAdminUserRoutes(backend: Backend) {
    bindAdminUserBasicRoutes(backend)
    bindAdminUserRelationRoutes(backend)
    bindAdminUserActivityRoutes(backend)
}

private fun Route.bindAdminUserBasicRoutes(backend: Backend) {
    AdminApi.Users.get(handleResult()) {
        it.pagination(IdentifiablePagingGenerator, { l, p ->
            UserInfoListResponse(l, p)
        }) { fetch ->
            backend.getAllUsers(fetch)
        }
    }
    AdminApi.Users.Id.get(handleResult()) { p ->
        backend.getUserById(p.id)
    }
    AdminApi.Users.Id.Overview.get(handleResult()) { p ->
        backend.getUserOverview(p.id)
    }
    AdminApi.Users.add(handleResult()) { api ->
        backend.addUser(api.receiveBody())
    }
    AdminApi.Users.Id.Status.update(handleResult()) { p, api ->
        usePrincipal { uid ->
            backend.updateUserStatus(p.id, api.receiveBody(), uid)
        }
    }
}

private fun Route.bindAdminUserRelationRoutes(backend: Backend) {
    AdminApi.Users.Id.Communities.get(handleResult()) { q, p ->
        q.pagination(IdentifiablePagingGenerator, { l, p ->
            CommunityInfoListResponse(l, p)
        }) { f ->
            backend.getUserJoinedCommunities(null, p.id, f)
        }
    }
    AdminApi.Users.Id.Rooms.get(handleResult()) { q, p ->
        q.pagination(IdentifiablePagingGenerator, { l, p ->
            RoomInfoListResponse(l, p)
        }) { f ->
            backend.getUserJoinedRooms(p.id, f)
        }
    }
    AdminApi.Users.Id.Titles.get(handleResult()) { q, p ->
        q.pagination(IdentifiablePagingGenerator, { l, p ->
            TitleInfoListResponse(l, p)
        }) { f ->
            backend.getUserTitles(p.id, q.searchType, q.type, q.scopeId, q.titleStatus, f)
        }
    }
    AdminApi.Users.Id.Files.get(handleResult()) { q, p ->
        q.pagination(IdentifiablePagingGenerator, { l, p ->
            FileInfoListResponse(l, p)
        }) { f ->
            backend.getFileInfoPaginationResult(p.id, f)
        }
    }
}

private fun Route.bindAdminUserActivityRoutes(backend: Backend) {
    AdminApi.Users.Id.Logs.get(handleResult()) { q, p ->
        q.pagination(IdentifiablePagingGenerator, { l, p ->
            UserLogInfoListResponse(l, p)
        }) { f ->
            backend.getUserLogs(p.id, f)
        }
    }
    AdminApi.Users.Id.UploadRecords.get(handleResult()) { q, p ->
        q.pagination(IdentifiablePagingGenerator, { l, p ->
            UploadRecordInfoListResponse(l, p)
        }) { f ->
            backend.getUserUploadRecords(p.id, f)
        }
    }
    AdminApi.Users.Id.Reactions.get(handleResult()) { q, p ->
        q.pagination(IdentifiablePagingGenerator, { l, p ->
            ReactionRecordInfoListResponse(l, p)
        }) { f ->
            backend.getUserReactions(p.id, f)
        }
    }
    AdminApi.Users.Id.Comments.get(handleResult()) { q, p ->
        q.pagination(IdentifiablePagingGenerator, { l, p ->
            TopicInfoListResponse(l, p)
        }) { f ->
            backend.getUserCommentedTopics(p.id, f)
        }
    }
    AdminApi.Users.Id.Favorites.get(handleResult()) { q, p ->
        q.pagination(IdentifiablePagingGenerator, { l, p ->
            UserFavoriteInfoListResponse(l, p)
        }) { f ->
            backend.getFavorites(p.id, f)
        }
    }
    AdminApi.Users.Id.Subscriptions.get(handleResult()) { q, p ->
        q.pagination(IdentifiablePagingGenerator, { l, p ->
            UserSubscriptionInfoListResponse(l, p)
        }) { f ->
            backend.getUserSubscriptions(p.id, f)
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
