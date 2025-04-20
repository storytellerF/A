package com.storyteller_f.a.server.route

import com.maxmind.geoip2.DatabaseReader
import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.server.auth.omitPrincipal
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.auth.usePrincipalOrNull
import com.storyteller_f.a.server.common.IdentityPagingGenerator
import com.storyteller_f.a.server.common.pagination
import com.storyteller_f.a.server.service.*
import com.storyteller_f.shared.obj.NewCommunity
import com.storyteller_f.shared.obj.UpdateCommunityBody
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.tables.ObjectFetch
import com.storyteller_f.tables.PagingFetch
import com.storyteller_f.tables.searchMembers
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.routing.Route

fun Route.bindSafeCommunityRoute(reader: DatabaseReader, backend: Backend) {
    get<RouteCommunities.Search> {
        usePrincipalOrNull(reader) { uid ->
            pagination(IdentityPagingGenerator) { p, n, s ->
                searchCommunities(backend, uid, it, PagingFetch(p, n, s))
            }
        }
    }

    get<RouteCommunities.Id.Members> {
        omitPrincipal(reader) {
            pagination(IdentityPagingGenerator) { p, n, s ->
                DatabaseFactory.searchMembers(backend, it.parent.id, it.word, PagingFetch(p, n, s))
            }
        }
    }
    get<RouteCommunities.Id> {
        usePrincipalOrNull(reader) { uid ->
            getCommunity(backend, ObjectFetch.IdFetch(it.id), uid, it.parent.fillJoinInfo)
        }
    }

    get<RouteCommunities> {
        usePrincipalOrNull(reader) { uid ->
            it.aid?.let { aid ->
                getCommunity(backend, ObjectFetch.AidFetch(aid), uid, it.fillJoinInfo)
            }
        }
    }

    get<RouteCommunities.Id.Topics> {
        usePrincipalOrNull(reader) { uid ->
            pagination(IdentityPagingGenerator) { p, n, s ->
                getTopLevelTopicsInObject(
                    backend,
                    it.parent.id,
                    ObjectType.COMMUNITY,
                    uid,
                    it.fillHasCommented,
                    PagingFetch(p, n, s),
                    it.pinType
                )
            }
        }
    }
}

fun Route.bindProtectedSafeCommunityRoute(reader: DatabaseReader, backend: Backend) {
    post<RouteCommunities.Id.Join> {
        usePrincipal(reader) { uid ->
            doUserJoinCommunity(backend, uid, it.parent.id)
        }
    }

    post<RouteCommunities.Id.Exit> {
        usePrincipal(reader) { uid ->
            exitCommunity(backend, it.parent.id, uid)
        }
    }
    post<RouteCommunities> {
        val newCommunity = call.receive<NewCommunity>()
        usePrincipal(reader) { uid ->
            createCommunity(backend, newCommunity, uid)
        }
    }

    post<RouteCommunities.Id> {
        usePrincipal(reader) { uid ->
            updateCommunity(backend, it.id, call.receive<UpdateCommunityBody>(), uid)
        }
    }
}
