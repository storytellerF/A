package com.storyteller_f.a.cloud.server.route

import com.storyteller_f.a.api.CommunityInfoListResponse
import com.storyteller_f.a.api.CustomApi
import com.storyteller_f.a.api.NewFavorite
import com.storyteller_f.a.api.NewSubscription
import com.storyteller_f.a.api.ReactionRecordInfoListResponse
import com.storyteller_f.a.api.RoomInfoListResponse
import com.storyteller_f.a.api.TitleInfoListResponse
import com.storyteller_f.a.api.TopicInfoListResponse
import com.storyteller_f.a.api.UserFavoriteInfoListResponse
import com.storyteller_f.a.api.UserInfoListResponse
import com.storyteller_f.a.api.UserSubscriptionInfoListResponse
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
    bindUserFavoriteRoute(backend)

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
            q.pagination(IdentifiablePagingGenerator, { l, p ->
                ReactionRecordInfoListResponse(l, p)
            }) { fetch ->
                backend.getUserReactions(uid, fetch)
            }
        }
    }
    CustomApi.Users.Comments.get(handleResult()) { q ->
        usePrincipal { uid ->
            q.pagination(IdentifiablePagingGenerator, { l, p ->
                TopicInfoListResponse(l, p)
            }) { fetch ->
                backend.getUserCommentedTopics(uid, fetch)
            }
        }
    }
    bindUserCommunitiesRoute(backend)
}

private fun Route.bindUserFavoriteRoute(backend: Backend) {
    CustomApi.Users.Id.Favorites.get(handleResult()) { q, p ->
        usePrincipal {
            q.pagination(IdentifiablePagingGenerator, { l, p ->
                UserFavoriteInfoListResponse(l, p)
            }) { fetch ->
                backend.getFavorites(p.id, fetch)
            }
        }
    }

    CustomApi.Users.Id.Favorite.add(handleResult()) { p, _ ->
        usePrincipal { uid ->
            backend.addFavorite(uid, NewFavorite(ObjectType.USER, p.id)).map { }
        }
    }
}

private fun Route.bindUserCommunitiesRoute(backend: Backend) {
    CustomApi.Users.JoinedCommunities.get(handleResult()) { q ->
        usePrincipal { uid ->
            q.pagination(IdentifiablePagingGenerator, { l, p ->
                CommunityInfoListResponse(l, p)
            }) { fetch ->
                backend.getUserJoinedCommunities(uid, uid, fetch, q.hasPoster)
            }
        }
    }
    CustomApi.Users.JoinedCommunities.search(handleResult()) { q ->
        usePrincipal { uid ->
            q.pagination(GeneralOffsetPagingGenerator, { l, p ->
                CommunityInfoListResponse(l, p)
            }) { fetch ->
                backend.searchUserJoinedCommunities(uid, q, fetch)
            }
        }
    }
    CustomApi.Users.JoinedRooms.get(handleResult()) { q ->
        usePrincipal { uid ->
            q.pagination(IdentifiablePagingGenerator, { l, p ->
                RoomInfoListResponse(l, p)
            }) { fetch ->
                backend.getUserJoinedRooms(uid, fetch)
            }
        }
    }
    CustomApi.Users.JoinedRooms.search(handleResult()) { q ->
        usePrincipal { uid ->
            q.pagination(GeneralOffsetPagingGenerator, { l, p ->
                RoomInfoListResponse(l, p)
            }) { fetch ->
                backend.searchCurrentUserRooms(uid, fetch, q)
            }
        }
    }
}

fun Route.bindProtectedUserSubscriptionRoute(backend: Backend) {
    CustomApi.Users.Id.Subscriptions.get(handleResult()) { q, p ->
        usePrincipal {
            q.pagination(IdentifiablePagingGenerator, { l, p ->
                UserSubscriptionInfoListResponse(l, p)
            }) { fetch ->
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
        usePrincipalOrNull { uid ->
            backend.getUserInfo(ObjectFetch.AidFetch(q.aid), uid)
        }
    }
    CustomApi.Users.Id.get(handleResult()) {
        usePrincipalOrNull { uid ->
            backend.getUserInfo(ObjectFetch.IdFetch(it.id), uid)
        }
    }

    CustomApi.Users.Id.Topics.get(handleResult()) { q, p ->
        usePrincipalOrNull { uid ->
            q.pagination(IdentifiablePagingGenerator, { l, p ->
                TopicInfoListResponse(l, p)
            }) { f ->
                backend.getTopicsByParentId(p.id, ObjectType.USER, uid, q.fillHasCommented, f, q.pinType)
            }
        }
    }

    CustomApi.Users.Id.Communities.get(handleResult()) { q, p ->
        usePrincipalOrNull { uid ->
            q.pagination(IdentifiablePagingGenerator, { l, p ->
                CommunityInfoListResponse(l, p)
            }) { f ->
                backend.getUserJoinedCommunities(uid, p.id, f, q.hasPoster)
            }
        }
    }

    CustomApi.Users.Id.Titles.get(handleResult()) { q, p ->
        q.pagination(IdentifiablePagingGenerator, { l, p ->
            TitleInfoListResponse(l, p)
        }) { f ->
            backend.getUserTitles(p.id, q.searchType, q.type, q.scopeId, q.titleStatus, f)
        }
    }

    CustomApi.Users.search(handleResult()) { q ->
        usePrincipalOrNull { uid ->
            q.pagination(GeneralOffsetPagingGenerator, { l, p ->
                UserInfoListResponse(l, p)
            }) { f ->
                backend.searchUsers(q.word, uid, f)
            }
        }
    }
}
