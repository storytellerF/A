package com.storyteller_f.a.server.route

import com.maxmind.geoip2.DatabaseReader
import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.server.auth.omitPrincipal
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.auth.usePrincipalOrNull
import com.storyteller_f.a.server.common.pagination
import com.storyteller_f.a.server.service.*
import com.storyteller_f.shared.obj.NewCommunity
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.tables.searchMembers
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.routing.Route

fun Route.bindSafeCommunityRoute(backend: Backend, reader: DatabaseReader) {
    get<RouteCommunities.Search> {
        usePrincipalOrNull(reader) { uid ->
            pagination(PrimaryKey::class, {
                it.id.toString()
            }) { p, n, s ->
                searchCommunities(backend, p, n, s, uid, it)
            }
        }
    }

    get<RouteCommunities.Id.Members> {
        omitPrincipal(reader) {
            pagination(PrimaryKey::class, {
                it.id.toString()
            }) { p, n, s ->
                DatabaseFactory.searchMembers(it.parent.id, backend, p, n, s, it.word)
            }
        }
    }
    get<RouteCommunities.Id> {
        usePrincipalOrNull(reader) { uid ->
            getCommunity(it.id, null, backend, uid, it.parent.fillJoinInfo)
        }
    }
    get<RouteCommunities> {
        usePrincipalOrNull(reader) { uid ->
            getCommunity(null, it.aid, backend, uid, it.fillJoinInfo)
        }
    }

    get<RouteCommunities.Id.Topics> {
        usePrincipalOrNull(reader) { uid ->
            pagination(PrimaryKey::class, {
                it.id.toString()
            }) { p, n, s ->
                getTopLevelTopicsInObject(
                    it.parent.id,
                    ObjectType.COMMUNITY,
                    uid,
                    backend,
                    p,
                    n,
                    s,
                    it.fillHasCommented,
                    it.pinType
                )
            }
        }
    }
}

fun Route.bindProtectedSafeCommunityRoute(backend: Backend, reader: DatabaseReader) {
    post<RouteCommunities.Id.Join> {
        usePrincipal(reader) { uid ->
            doUserJoinCommunity(uid, it.parent.id, backend)
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
