package com.storyteller_f.a.server.route

import com.storyteller_f.a.api.core.Api
import com.storyteller_f.a.api.server.invoke
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.server.auth.*
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
    Api.Communities.Search.getting(RoutingContext::handleResult) {
        usePrincipalOrNull1 { uid ->
            pagination(IdentifiablePagingGenerator) { f ->
                backend.searchCommunities(uid, it, f)
            }
        }
    }

    get<RouteCommunities.Id.Members> {
        omitPrincipal {
            pagination(IdentifiablePagingGenerator) { f ->
                backend.searchMembers(it.parent.id, it.word, f)
            }
        }
    }
    get<RouteCommunities.Id> {
        usePrincipalOrNull { uid ->
            backend.getCommunity(ObjectFetch.IdFetch(it.id), uid, it.parent.fillJoinInfo)
        }
    }

    get<RouteCommunities.Aid> {
        usePrincipalOrNull { uid ->
            it.aid?.let { aid ->
                backend.getCommunity(ObjectFetch.AidFetch(aid), uid, it.parent.fillJoinInfo)
            }
        }
    }

    get<RouteCommunities.Id.Topics> {
        usePrincipalOrNull { uid ->
            pagination(IdentifiablePagingGenerator) { f ->
                backend.getTopLevelTopicsInObject(
                    it.parent.id,
                    ObjectType.COMMUNITY,
                    uid,
                    it.fillHasCommented,
                    f,
                    it.pinType
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
