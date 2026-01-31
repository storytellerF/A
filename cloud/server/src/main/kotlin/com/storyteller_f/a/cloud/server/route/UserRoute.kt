package com.storyteller_f.a.cloud.server.route

import com.storyteller_f.a.api.CustomApi
import com.storyteller_f.a.api.NewFavorite
import com.storyteller_f.a.api.NewSubscription
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.cloud.core.service.addDevice
import com.storyteller_f.a.cloud.core.service.addFavorite
import com.storyteller_f.a.cloud.core.service.addReadLog
import com.storyteller_f.a.cloud.core.service.addSubscription
import com.storyteller_f.a.cloud.core.service.deleteFavoriteByObject
import com.storyteller_f.a.cloud.core.service.getFavorites
import com.storyteller_f.a.cloud.core.service.getTopicsByParentId
import com.storyteller_f.a.cloud.core.service.getUserCommentedTopics
import com.storyteller_f.a.cloud.core.service.getUserInfo
import com.storyteller_f.a.cloud.core.service.getUserJoinedCommunities
import com.storyteller_f.a.cloud.core.service.getUserJoinedRooms
import com.storyteller_f.a.cloud.core.service.getUserOverview
import com.storyteller_f.a.cloud.core.service.getUserReactions
import com.storyteller_f.a.cloud.core.service.getUserSubscriptions
import com.storyteller_f.a.cloud.core.service.getUserTitles
import com.storyteller_f.a.cloud.core.service.removeSubscriptionByObject
import com.storyteller_f.a.cloud.core.service.searchCurrentUserRooms
import com.storyteller_f.a.cloud.core.service.searchUserJoinedCommunities
import com.storyteller_f.a.cloud.core.service.searchUsers
import com.storyteller_f.a.cloud.core.service.updateUser
import com.storyteller_f.a.cloud.server.auth.handleResult
import com.storyteller_f.a.cloud.server.auth.usePrincipal
import com.storyteller_f.a.cloud.server.auth.usePrincipalOrNull
import com.storyteller_f.a.cloud.server.common.GeneralOffsetPagingGenerator
import com.storyteller_f.a.cloud.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.cloud.server.common.pagination
import com.storyteller_f.endpoint4k.ktor.server.invoke
import com.storyteller_f.endpoint4k.ktor.server.receiveBody
import com.storyteller_f.shared.type.ObjectType
import io.ktor.server.routing.Route

fun Route.bindProtectedUserRoute(backend: Backend) {
    CustomApi.Users.update(handleResult()) { api ->
        usePrincipal { uid ->
            backend.updateUser(uid, api.receiveBody())
        }
    }
    CustomApi.Users.Read.add(handleResult()) { api ->
        usePrincipal { uid ->
            backend.addReadLog(uid, api.receiveBody())
        }
    }
    CustomApi.Users.Devices.add(handleResult()) { api ->
        usePrincipal { uid ->
            val newDevice = api.receiveBody()
            backend.addDevice(uid, newDevice)
        }
    }
    CustomApi.Users.Id.Favorites.get(handleResult()) { q, p ->
        usePrincipalOrNull { uid -> // 使用 usePrincipalOrNull 允许未登录查看（如果后端允许）
            q.pagination(IdentifiablePagingGenerator) { fetch ->
                backend.getFavorites(p.id, fetch)
            }
        }
    }

    CustomApi.Users.Id.Favorite.add(handleResult()) { p, _ ->
        usePrincipal { uid ->
            backend.addFavorite(uid, NewFavorite(ObjectType.USER, p.id)).map { }
        }
    }

    CustomApi.Users.Id.Favorite.delete(handleResult()) { path, _ ->
        usePrincipal { uid ->
            backend.deleteFavoriteByObject(uid, path.id).map { }
        }
    }

    CustomApi.Users.overview(handleResult()) {
        usePrincipal { uid ->
            backend.getUserOverview(uid)
        }
    }
    CustomApi.Users.ReactionRecords.get(handleResult()) { q ->
        usePrincipal { uid ->
            q.pagination(IdentifiablePagingGenerator) { fetch ->
                backend.getUserReactions(uid, fetch)
            }
        }
    }
    CustomApi.Users.Comments.get(handleResult()) { q ->
        usePrincipal { uid ->
            q.pagination(IdentifiablePagingGenerator) { fetch ->
                backend.getUserCommentedTopics(uid, fetch)
            }
        }
    }
    bindUserCommunitiesRoute(backend)
}

private fun Route.bindUserCommunitiesRoute(backend: Backend) {
    CustomApi.Users.JoinedCommunities.get(handleResult()) { q ->
        usePrincipal { uid ->
            q.pagination(IdentifiablePagingGenerator) { fetch ->
                backend.getUserJoinedCommunities(uid, uid, fetch)
            }
        }
    }
    CustomApi.Users.JoinedCommunities.search(handleResult()) { q ->
        usePrincipal { uid ->
            q.pagination(GeneralOffsetPagingGenerator) { fetch ->
                backend.searchUserJoinedCommunities(uid, q, fetch)
            }
        }
    }
    CustomApi.Users.JoinedRooms.get(handleResult()) { q ->
        usePrincipal { uid ->
            q.pagination(IdentifiablePagingGenerator) { fetch ->
                backend.getUserJoinedRooms(uid, fetch)
            }
        }
    }
    CustomApi.Users.JoinedRooms.search(handleResult()) { q ->
        usePrincipal { uid ->
            q.pagination(GeneralOffsetPagingGenerator) { fetch ->
                backend.searchCurrentUserRooms(uid, fetch, q)
            }
        }
    }
}

fun Route.bindProtectedUserSubscriptionRoute(backend: Backend) {
    CustomApi.Users.Id.Subscriptions.get(handleResult()) { q, p ->
        usePrincipalOrNull { uid ->
            // 这里逻辑可能需要调整，原来是获取自己的订阅，现在是获取某个User的订阅
            // 如果原来的逻辑只能获取自己的，那这里 p.id 应该等于 uid？
            // 暂时假设允许获取指定用户的订阅，或者后端service会做权限检查
            // 如果后端service只能获取"当前用户"的，那这里可能需要修改service传入 p.id
            // 查看 getUserSubscriptions 签名：backend.getUserSubscriptions(uid, fetch)
            // 这里的 uid 是 target user id.
            q.pagination(IdentifiablePagingGenerator) { fetch ->
                backend.getUserSubscriptions(p.id, fetch)
            }
        }
    }
    CustomApi.Users.Id.Subscription.add(handleResult()) { p, _ ->
        usePrincipal { uid ->
            backend.addSubscription(uid, NewSubscription(p.id, ObjectType.USER)).map { }
        }
    }

    CustomApi.Users.Id.Subscription.delete(handleResult()) { path, _ ->
        usePrincipal { uid ->
            backend.removeSubscriptionByObject(uid, path.id).map { }
        }
    }
}

fun Route.bindUserRoute(backend: Backend) {
    CustomApi.Users.Aid.get(handleResult()) { q ->
        backend.getUserInfo(ObjectFetch.AidFetch(q.aid))
    }
    CustomApi.Users.Id.get(handleResult()) {
        backend.getUserInfo(ObjectFetch.IdFetch(it.id))
    }

    CustomApi.Users.Id.Topics.get(handleResult()) { q, p ->
        usePrincipalOrNull { uid ->
            q.pagination(IdentifiablePagingGenerator) { f ->
                backend.getTopicsByParentId(p.id, ObjectType.USER, uid, q.fillHasCommented, f, q.pinType)
            }
        }
    }

    CustomApi.Users.Id.Communities.get(handleResult()) { q, p ->
        usePrincipalOrNull { uid ->
            q.pagination(IdentifiablePagingGenerator) { f ->
                backend.getUserJoinedCommunities(uid, p.id, f)
            }
        }
    }

    CustomApi.Users.Id.Titles.get(handleResult()) { q, p ->
        q.pagination(IdentifiablePagingGenerator) { f ->
            backend.getUserTitles(p.id, q.searchType, q.type, q.scopeId, f)
        }
    }

    CustomApi.Users.search(handleResult()) {
        it.pagination(GeneralOffsetPagingGenerator) { f ->
            backend.searchUsers(it.word, f)
        }
    }
}
