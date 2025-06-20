package com.storyteller_f.a.server.route

import com.storyteller_f.a.api.core.Api
import com.storyteller_f.a.api.server.invoke
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.server.auth.handleResult
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.auth.usePrincipalOrNull1
import com.storyteller_f.a.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.server.common.pagination
import com.storyteller_f.a.server.service.*
import com.storyteller_f.backend.service.Backend
import com.storyteller_f.backend.service.searchMembers
import com.storyteller_f.shared.obj.NewCommunity
import com.storyteller_f.shared.obj.UpdateCommunityBody
import com.storyteller_f.shared.type.ObjectType
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext

fun Route.bindSafeCommunityRoute(backend: Backend) {
    Api.Communities.Search.get(RoutingContext::handleResult) {
        usePrincipalOrNull1 { uid ->
            it.pagination(IdentifiablePagingGenerator) { f ->
                backend.searchCommunities(uid, it, f)
            }
        }
    }

    Api.Communities.Id.Members.get(RoutingContext::handleResult) { q, p ->
        pagination(IdentifiablePagingGenerator) { f ->
            backend.searchMembers(p.id, q.word, f)
        }
    }
    Api.Communities.Id.get(RoutingContext::handleResult) { q, p ->
        usePrincipalOrNull1 { uid ->
            backend.getCommunity(ObjectFetch.IdFetch(p.id), uid, q.fillJoinInfo)
        }
    }
    Api.Communities.Aid.get(RoutingContext::handleResult) {
        usePrincipalOrNull1 { uid ->
            backend.getCommunity(ObjectFetch.AidFetch(it.aid), uid, it.fillJoinInfo)
        }
    }

    Api.Communities.Id.Topics.get(RoutingContext::handleResult) { q, p ->
        usePrincipalOrNull1 { uid ->
            q.pagination(IdentifiablePagingGenerator) { f ->
                backend.getTopLevelTopicsInObject(
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

fun Route.bindProtectedSafeCommunityRoute(backend: Backend) {
    post<RouteCommunities.Id.Join> {
        usePrincipal { uid ->
            backend.doUserJoinCommunity(uid, it.parent.id)
        }
    }

    post<RouteCommunities.Id.Exit> {
        usePrincipal { uid ->
            backend.exitCommunity(it.parent.id, uid)
        }
    }
    post<RouteCommunities> {
        val newCommunity = call.receive<NewCommunity>()
        usePrincipal { uid ->
            backend.createCommunity(newCommunity, uid)
        }
    }

    post<RouteCommunities.Id> {
        usePrincipal { uid ->
            backend.updateCommunity(it.id, call.receive<UpdateCommunityBody>(), uid)
        }
    }
}
