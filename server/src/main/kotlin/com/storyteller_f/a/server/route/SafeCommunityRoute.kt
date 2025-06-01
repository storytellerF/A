package com.storyteller_f.a.server.route

import com.maxmind.geoip2.DatabaseReader
import com.storyteller_f.Backend
import com.storyteller_f.a.server.auth.omitPrincipal
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.auth.usePrincipalOrNull
import com.storyteller_f.a.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.server.common.pagination
import com.storyteller_f.a.server.service.*
import com.storyteller_f.shared.obj.NewCommunity
import com.storyteller_f.shared.obj.UpdateCommunityBody
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.tables.ObjectFetch
import com.storyteller_f.tables.searchMembers
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.routing.Route

fun Route.bindSafeCommunityRoute(reader: DatabaseReader, backend: Backend) {
    get<RouteCommunities.Search> {
        usePrincipalOrNull(reader) { uid ->
            pagination(IdentifiablePagingGenerator) { f ->
                backend.searchCommunities(uid, it, f)
            }
        }
    }

    get<RouteCommunities.Id.Members> {
        omitPrincipal(reader) {
            pagination(IdentifiablePagingGenerator) { f ->
                backend.searchMembers(it.parent.id, it.word, f)
            }
        }
    }
    get<RouteCommunities.Id> {
        usePrincipalOrNull(reader) { uid ->
            backend.getCommunity(ObjectFetch.IdFetch(it.id), uid, it.parent.fillJoinInfo)
        }
    }

    get<RouteCommunities.Aid> {
        usePrincipalOrNull(reader) { uid ->
            it.aid?.let { aid ->
                backend.getCommunity(ObjectFetch.AidFetch(aid), uid, it.parent.fillJoinInfo)
            }
        }
    }

    get<RouteCommunities.Id.Topics> {
        usePrincipalOrNull(reader) { uid ->
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

fun Route.bindProtectedSafeCommunityRoute(reader: DatabaseReader, backend: Backend) {
    post<RouteCommunities.Id.Join> {
        usePrincipal(reader) { uid ->
            backend.doUserJoinCommunity(uid, it.parent.id)
        }
    }

    post<RouteCommunities.Id.Exit> {
        usePrincipal(reader) { uid ->
            backend.exitCommunity(it.parent.id, uid)
        }
    }
    post<RouteCommunities> {
        val newCommunity = call.receive<NewCommunity>()
        usePrincipal(reader) { uid ->
            backend.createCommunity(newCommunity, uid)
        }
    }

    post<RouteCommunities.Id> {
        usePrincipal(reader) { uid ->
            backend.updateCommunity(it.id, call.receive<UpdateCommunityBody>(), uid)
        }
    }
}
