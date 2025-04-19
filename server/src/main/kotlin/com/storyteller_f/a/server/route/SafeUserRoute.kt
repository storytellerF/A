package com.storyteller_f.a.server.route

import com.maxmind.geoip2.DatabaseReader
import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.server.auth.omitPrincipal
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.auth.usePrincipalOrNull
import com.storyteller_f.a.server.common.pagination
import com.storyteller_f.a.server.service.getTopLevelTopicsInObject
import com.storyteller_f.a.server.service.getUserTitles
import com.storyteller_f.a.server.service.updateUser
import com.storyteller_f.shared.obj.UpdateUserBody
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.tables.ObjectFetch
import com.storyteller_f.tables.PagingFetch
import com.storyteller_f.tables.getUser
import com.storyteller_f.tables.searchMembers
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.routing.Route

fun Route.bindProtectedSafeUserRoute(reader: DatabaseReader, backend: Backend) {
    post<RouteUsers.Update> {
        usePrincipal(reader) { uid ->
            updateUser(uid, backend, call.receive<UpdateUserBody>())
        }
    }
}

fun Route.bindSafeUserRoute(backend: Backend, reader: DatabaseReader) {
    get<RouteUsers> { value ->
        omitPrincipal(reader) {
            value.aid?.let { DatabaseFactory.getUser(ObjectFetch.AidFetch(it), backend) } ?: Result.success(null)
        }
    }
    get<RouteUsers.Id> {
        omitPrincipal(reader) {
            DatabaseFactory.getUser(ObjectFetch.IdFetch(it.id), backend = backend)
        }
    }

    get<RouteUsers.Id.Topics> {
        usePrincipalOrNull(reader) { uid ->
            pagination(PrimaryKey::class, {
                it.id.toString()
            }) { p, n, s ->
                getTopLevelTopicsInObject(
                    it.parent.id,
                    ObjectType.USER,
                    uid,
                    backend,
                    it.fillHasCommented,
                    PagingFetch(p, n, s),
                    it.pinType
                )
            }
        }
    }

    get<RouteUsers.Id.Titles> { r ->
        omitPrincipal(reader) {
            pagination(PrimaryKey::class, {
                it.id.toString()
            }) { p, n, s ->
                getUserTitles(backend, r.parent.id, r.searchType, r.type, r.scopeId, n, s, p)
            }
        }
    }

    get<RouteUsers.Search> {
        omitPrincipal(reader) {
            pagination(PrimaryKey::class, {
                it.id.toString()
            }) { p, n, s ->
                DatabaseFactory.searchMembers(null, backend, it.word, PagingFetch(p, n, s))
            }
        }
    }
}
