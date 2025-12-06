package com.storyteller_f.a.cloud.server.route

import com.storyteller_f.a.api.CustomApi
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.cloud.core.service.createCommunity
import com.storyteller_f.a.cloud.core.service.exitCommunity
import com.storyteller_f.a.cloud.core.service.getCommunity
import com.storyteller_f.a.cloud.core.service.getCommunityRooms
import com.storyteller_f.a.cloud.core.service.getTopicsByParentId
import com.storyteller_f.a.cloud.core.service.joinCommunity
import com.storyteller_f.a.cloud.core.service.searchCommunities
import com.storyteller_f.a.cloud.core.service.searchCommunityRooms
import com.storyteller_f.a.cloud.core.service.searchContainerMembers
import com.storyteller_f.a.cloud.core.service.updateCommunity
import com.storyteller_f.a.cloud.server.auth.handleResult
import com.storyteller_f.a.cloud.server.auth.usePrincipal
import com.storyteller_f.a.cloud.server.auth.usePrincipalOrNull
import com.storyteller_f.a.cloud.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.cloud.server.common.pagination
import com.storyteller_f.a.cloud.server.common.pagingGenerator
import com.storyteller_f.endpoint4k.ktor.server.invoke
import com.storyteller_f.endpoint4k.ktor.server.receiveBody
import com.storyteller_f.shared.type.ObjectType
import io.ktor.server.routing.Route

fun Route.bindCommunityRoute(backend: Backend) {
    CustomApi.Communities.search(handleResult()) {
        usePrincipalOrNull { uid ->
            it.pagination(IdentifiablePagingGenerator) { f ->
                backend.searchCommunities(uid, it, f)
            }
        }
    }

    CustomApi.Communities.Id.Members.get(handleResult()) { q, p ->
        q.pagination(pagingGenerator {
            it.id
        }) { f ->
            backend.searchContainerMembers(p.id, q.word, f)
        }
    }
    CustomApi.Communities.Id.get(handleResult()) { q, p ->
        usePrincipalOrNull { uid ->
            backend.getCommunity(ObjectFetch.IdFetch(p.id), uid, q.fillJoinInfo)
        }
    }
    CustomApi.Communities.Aid.get(handleResult()) {
        usePrincipalOrNull { uid ->
            backend.getCommunity(ObjectFetch.AidFetch(it.aid), uid, it.fillJoinInfo)
        }
    }

    CustomApi.Communities.Id.Topics.get(handleResult()) { q, p ->
        usePrincipalOrNull { uid ->
            q.pagination(IdentifiablePagingGenerator) { f ->
                backend.getTopicsByParentId(p.id, ObjectType.COMMUNITY, uid, q.fillHasCommented, f, q.pinType)
            }
        }
    }

    CustomApi.Communities.Id.Rooms.get(handleResult()) { q, p ->
        usePrincipalOrNull {
            q.pagination(IdentifiablePagingGenerator) { f ->
                backend.getCommunityRooms(p.id, f, it, q.joinStatus)
            }
        }
    }

    CustomApi.Communities.Id.Rooms.search(handleResult()) { q, p ->
        usePrincipalOrNull { uid ->
            q.pagination(IdentifiablePagingGenerator) { f ->
                backend.searchCommunityRooms(uid, p.id, f, q)
            }
        }
    }
}

fun Route.bindProtectedCommunityRoute(backend: Backend) {
    CustomApi.Communities.Id.Members.join(handleResult()) { p, api ->
        usePrincipal { uid ->
            backend.joinCommunity(uid, p.id)
        }
    }

    CustomApi.Communities.Id.Members.leave(handleResult()) { p, api ->
        usePrincipal { uid ->
            backend.exitCommunity(p.id, uid)
        }
    }
    CustomApi.Communities.add(handleResult()) { api ->
        val newCommunity = api.receiveBody()
        usePrincipal { uid ->
            backend.createCommunity(newCommunity, uid)
        }
    }

    CustomApi.Communities.Id.update(handleResult()) { p, api ->
        val newCommunity = api.receiveBody()
        usePrincipal { uid ->
            backend.updateCommunity(p.id, newCommunity, uid)
        }
    }
}
