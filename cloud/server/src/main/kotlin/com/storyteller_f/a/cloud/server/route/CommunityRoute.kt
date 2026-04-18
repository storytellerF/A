package com.storyteller_f.a.cloud.server.route

import com.storyteller_f.a.api.CommunityInfoListResponse
import com.storyteller_f.a.api.CustomApi
import com.storyteller_f.a.api.MemberInfoListResponse
import com.storyteller_f.a.api.NewFavorite
import com.storyteller_f.a.api.NewSubscription
import com.storyteller_f.a.api.RoomInfoListResponse
import com.storyteller_f.a.api.TopicInfoListResponse
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.cloud.core.service.addFavorite
import com.storyteller_f.a.cloud.core.service.addSubscription
import com.storyteller_f.a.cloud.core.service.createCommunity
import com.storyteller_f.a.cloud.core.service.deleteFavoriteByObject
import com.storyteller_f.a.cloud.core.service.exitCommunity
import com.storyteller_f.a.cloud.core.service.getCommunity
import com.storyteller_f.a.cloud.core.service.getCommunityMemberInfos
import com.storyteller_f.a.cloud.core.service.getCommunityRooms
import com.storyteller_f.a.cloud.core.service.getTopicsByParentId
import com.storyteller_f.a.cloud.core.service.joinCommunity
import com.storyteller_f.a.cloud.core.service.removeSubscriptionByObject
import com.storyteller_f.a.cloud.core.service.searchCommunities
import com.storyteller_f.a.cloud.core.service.searchCommunityRooms
import com.storyteller_f.a.cloud.core.service.searchContainerMembers
import com.storyteller_f.a.cloud.core.service.updateCommunity
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

fun Route.bindCommunityRoute(backend: Backend) {
    CustomApi.Communities.search(handleResult(backend)) {
        usePrincipalOrNull { uid ->
            it.pagination(GeneralOffsetPagingGenerator, { l, p ->
                CommunityInfoListResponse(l, p)
            }) { f ->
                backend.searchCommunities(uid, it, f)
            }
        }
    }

    bindCommunityMemberRoute(backend)

    CustomApi.Communities.Id.get(handleResult(backend)) { q, p ->
        usePrincipalOrNull { uid ->
            backend.getCommunity(ObjectFetch.IdFetch(p.id), uid, q.fillJoinInfo)
        }
    }

    CustomApi.Communities.Aid.get(handleResult(backend)) {
        usePrincipalOrNull { uid ->
            backend.getCommunity(ObjectFetch.AidFetch(it.aid), uid, it.fillJoinInfo)
        }
    }

    CustomApi.Communities.Id.Topics.get(handleResult(backend)) { q, p ->
        usePrincipalOrNull { uid ->
            q.pagination(IdentifiablePagingGenerator, { l, p ->
                TopicInfoListResponse(l, p)
            }) { f ->
                backend.getTopicsByParentId(p.id, ObjectType.COMMUNITY, uid, q.fillHasCommented, f, q.pinType)
            }
        }
    }

    CustomApi.Communities.Id.Rooms.get(handleResult(backend)) { q, p ->
        usePrincipalOrNull {
            q.pagination(IdentifiablePagingGenerator, { l, p ->
                RoomInfoListResponse(l, p)
            }) { f ->
                backend.getCommunityRooms(p.id, f, it, q.joinStatus)
            }
        }
    }

    CustomApi.Communities.Id.Rooms.search(handleResult(backend)) { q, p ->
        usePrincipalOrNull { uid ->
            q.pagination(GeneralOffsetPagingGenerator, { l, p ->
                RoomInfoListResponse(l, p)
            }) { f ->
                backend.searchCommunityRooms(uid, p.id, f, q)
            }
        }
    }
}

private fun Route.bindCommunityMemberRoute(backend: Backend) {
    CustomApi.Communities.Id.Members.get(handleResult(backend)) { q, p ->
        q.pagination(IdentifiablePagingGenerator, { l, p ->
            MemberInfoListResponse(l, p)
        }) { f ->
            backend.getCommunityMemberInfos(p.id, f)
        }
    }

    CustomApi.Communities.Id.Members.search(handleResult(backend)) { q, p ->
        q.pagination(GeneralOffsetPagingGenerator, { l, p ->
            MemberInfoListResponse(l, p)
        }) { f ->
            backend.searchContainerMembers(p.id, q.word, f)
        }
    }
}

fun Route.bindProtectedCommunityRoute(backend: Backend) {
    CustomApi.Communities.Id.Members.join(handleResult(backend)) { p, _ ->
        usePrincipal { uid ->
            backend.joinCommunity(uid, p.id)
        }
    }

    CustomApi.Communities.Id.Members.leave(handleResult(backend)) { p, _ ->
        usePrincipal { uid ->
            backend.exitCommunity(p.id, uid)
        }
    }
    CustomApi.Communities.add(handleResult(backend)) { api ->
        val newCommunity = api.receiveBody()
        usePrincipal { uid ->
            backend.createCommunity(newCommunity, uid)
        }
    }

    CustomApi.Communities.Id.update(handleResult(backend)) { p, api ->
        val newCommunity = api.receiveBody()
        usePrincipal { uid ->
            backend.updateCommunity(p.id, newCommunity, uid)
        }
    }

    CustomApi.Communities.Id.Favorite.add(handleResult(backend)) { p, _ ->
        usePrincipal { uid ->
            backend.addFavorite(uid, NewFavorite(ObjectType.COMMUNITY, p.id)).map { }
        }
    }
    CustomApi.Communities.Id.Favorite.delete(handleResult(backend)) { p, _ ->
        usePrincipal { uid ->
            backend.deleteFavoriteByObject(uid, p.id).map { }
        }
    }
    CustomApi.Communities.Id.Subscription.add(handleResult(backend)) { p, _ ->
        usePrincipal { uid ->
            backend.addSubscription(uid, NewSubscription(p.id, ObjectType.COMMUNITY)).map { }
        }
    }
    CustomApi.Communities.Id.Subscription.delete(handleResult(backend)) { p, _ ->
        usePrincipal { uid ->
            backend.removeSubscriptionByObject(uid, p.id).map { }
        }
    }
}
