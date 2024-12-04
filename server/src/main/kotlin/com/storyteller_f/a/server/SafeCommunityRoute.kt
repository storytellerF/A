package com.storyteller_f.a.server

import com.storyteller_f.Backend
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.auth.usePrincipalOrNull
import com.storyteller_f.a.server.common.pagination
import com.storyteller_f.a.server.service.RouteCommunities
import com.storyteller_f.a.server.service.exitCommunity
import com.storyteller_f.a.server.service.getCommunity
import com.storyteller_f.a.server.service.joinCommunity
import com.storyteller_f.a.server.service.searchCommunities
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.tables.searchMembers
import io.ktor.server.resources.*
import io.ktor.server.routing.Route

fun Route.bindSafeCommunityRoute(backend: Backend) {
    get<RouteCommunities.Search> {
        usePrincipalOrNull { id ->
            pagination<CommunityInfo, PrimaryKey>(PrimaryKey::class, {
                it.id.toString()
            }) { p, n, s ->
                searchCommunities(backend, p, n, s, id, it)
            }
        }
    }

    get<RouteCommunities.Id.Members> {
        usePrincipalOrNull { id ->
            pagination<UserInfo, PrimaryKey>(PrimaryKey::class, {
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
