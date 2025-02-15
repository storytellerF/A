package com.storyteller_f.a.server.route

import com.maxmind.geoip2.DatabaseReader
import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.auth.usePrincipalOrNull
import com.storyteller_f.a.server.common.pagination
import com.storyteller_f.a.server.service.*
import com.storyteller_f.shared.obj.NewCommunity
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.tables.searchMembers
import io.ktor.server.request.receive
import io.ktor.server.resources.*
import io.ktor.server.routing.Route

fun Route.bindSafeCommunityRoute(backend: Backend, reader: DatabaseReader) {
    get<RouteCommunities.Search> {
        usePrincipalOrNull(reader) { id ->
            pagination(PrimaryKey::class, {
                it.id.toString()
            }) { p, n, s ->
                searchCommunities(backend, p, n, s, id, it)
            }
        }
    }

    get<RouteCommunities.Id.Members> {
        usePrincipalOrNull(reader) { _ ->
            pagination(PrimaryKey::class, {
                it.id.toString()
            }) { p, n, s ->
                DatabaseFactory.searchMembers(it.parent.id, backend, p, n, s, it.word)
            }
        }
    }
    get<RouteCommunities.Id> {
        usePrincipalOrNull(reader) { id ->
            getCommunity(it.id, null, backend, id, it.parent.fillJoinInfo)
        }
    }
    get<RouteCommunities> {
        usePrincipalOrNull(reader) { id ->
            getCommunity(null, it.aid, backend, id, it.fillJoinInfo)
        }
    }

    get<RouteCommunities.Id.Topics> {
        usePrincipalOrNull(reader) { id ->
            pagination(PrimaryKey::class, {
                it.id.toString()
            }) { _, n, s ->
                getCommunityTopicList(it, id, n, s, backend, it)
            }
        }
    }
}

fun Route.bindProtectedSafeCommunityRoute(backend: Backend, reader: DatabaseReader) {
    post<RouteCommunities.Id.Join> {
        usePrincipal(reader) { id ->
            doUserJoinCommunity(id, it.parent.id, backend)
        }
    }

    post<RouteCommunities.Id.Exit> {
        usePrincipal(reader) { uid ->
            exitCommunity(it.parent.id, uid, backend)
        }
    }
    post<RouteCommunities> {
        val newCommunity = call.receive<NewCommunity>()
        usePrincipal(reader) { uid ->
            createCommunity(newCommunity, uid, backend)
        }
    }
}
