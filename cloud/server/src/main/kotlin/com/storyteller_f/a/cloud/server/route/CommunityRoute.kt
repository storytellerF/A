package com.storyteller_f.a.cloud.server.route

import com.storyteller_f.a.api.core.CustomApi
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.cloud.core.service.createCommunity
import com.storyteller_f.a.cloud.core.service.doUserJoinCommunity
import com.storyteller_f.a.cloud.core.service.exitCommunity
import com.storyteller_f.a.cloud.core.service.getCommunity
import com.storyteller_f.a.cloud.core.service.getTopicsByParentId
import com.storyteller_f.a.cloud.core.service.searchCommunities
import com.storyteller_f.a.cloud.core.service.searchMembers
import com.storyteller_f.a.cloud.core.service.updateCommunity
import com.storyteller_f.a.cloud.server.auth.handleResult
import com.storyteller_f.a.cloud.server.auth.usePrincipal
import com.storyteller_f.a.cloud.server.auth.usePrincipalOrNull
import com.storyteller_f.a.cloud.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.cloud.server.common.pagination
import com.storyteller_f.route4k.ktor.server.invoke
import com.storyteller_f.route4k.ktor.server.receiveBody
import com.storyteller_f.shared.type.ObjectType
import io.ktor.server.routing.*

fun Route.bindCommunityRoute(backend: Backend) {
    CustomApi.Communities.search(RoutingContext::handleResult) {
        usePrincipalOrNull { uid ->
            it.pagination(IdentifiablePagingGenerator) { f ->
                backend.searchCommunities(uid, it, f)
            }
        }
    }

    CustomApi.Communities.Id.Members.get(RoutingContext::handleResult) { q, p ->
        q.pagination(IdentifiablePagingGenerator) { f ->
            backend.searchMembers(p.id, q.word, f)
        }
    }
    CustomApi.Communities.Id.get(RoutingContext::handleResult) { q, p ->
        usePrincipalOrNull { uid ->
            backend.getCommunity(ObjectFetch.IdFetch(p.id), uid, q.fillJoinInfo)
        }
    }
    CustomApi.Communities.Aid.get(RoutingContext::handleResult) {
        usePrincipalOrNull { uid ->
            backend.getCommunity(ObjectFetch.AidFetch(it.aid), uid, it.fillJoinInfo)
        }
    }

    CustomApi.Communities.Id.Topics.get(RoutingContext::handleResult) { q, p ->
        usePrincipalOrNull { uid ->
            q.pagination(IdentifiablePagingGenerator) { f ->
                backend.getTopicsByParentId(
                    p.id,
                    ObjectType.COMMUNITY,
                    uid,
                    q.fillHasCommented,
                    f,
                    q.pinType
                )
            }
        }
    }
}

fun Route.bindProtectedCommunityRoute(backend: Backend) {
    CustomApi.Communities.Id.Members.join(RoutingContext::handleResult) { p, api ->
        usePrincipal { uid ->
            backend.doUserJoinCommunity(uid, p.id)
        }
    }

    CustomApi.Communities.Id.Members.leave(RoutingContext::handleResult) { p, api ->
        usePrincipal { uid ->
            backend.exitCommunity(p.id, uid)
        }
    }
    CustomApi.Communities.add(RoutingContext::handleResult) { api ->
        val newCommunity = api.receiveBody()
        usePrincipal { uid ->
            backend.createCommunity(newCommunity, uid)
        }
    }

    CustomApi.Communities.Id.update(RoutingContext::handleResult) { p, api ->
        val newCommunity = api.receiveBody()
        usePrincipal { uid ->
            backend.updateCommunity(p.id, newCommunity, uid)
        }
    }
}
