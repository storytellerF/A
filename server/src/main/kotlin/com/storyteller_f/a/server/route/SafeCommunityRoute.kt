package com.storyteller_f.a.server.route

import com.storyteller_f.Backend
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.auth.usePrincipalOrNull
import com.storyteller_f.a.server.common.pagination
import com.storyteller_f.a.server.service.*
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.tables.searchMembers
import io.ktor.server.resources.*
import io.ktor.server.routing.Route

fun Route.bindSafeCommunityRoute(backend: Backend) {
    get<RouteCommunities.Search> {
        usePrincipalOrNull { id ->
            pagination(PrimaryKey::class, {
                it.id.toString()
            }) { p, n, s ->
                searchCommunities(backend, p, n, s, id, it)
            }
        }
    }

    get<RouteCommunities.Id.Members> {
        usePrincipalOrNull { _ ->
            pagination(PrimaryKey::class, {
                it.id.toString()
            }) { p, n, s ->
                searchMembers(it.parent.id, backend, p, n, s, it.word)
            }
        }
    }
    get<RouteCommunities.Id> {
        usePrincipalOrNull { id ->
            getCommunity(it.id, null, backend, id, it.parent.fillJoinInfo)
        }
    }
    get<RouteCommunities> {
        usePrincipalOrNull { id ->
            getCommunity(null, it.aid, backend, id, it.fillJoinInfo)
        }
    }
}

fun Route.bindProtectedSafeCommunityRoute(backend: Backend) {
    post<RouteCommunities.Id.Join> {
        usePrincipal { id ->
            joinCommunity(id, it.parent.id, backend)
        }
    }

    post<RouteCommunities.Id.Exit> {
        usePrincipal { uid ->
            exitCommunity(it.parent.id, uid, backend)
        }
    }
}
