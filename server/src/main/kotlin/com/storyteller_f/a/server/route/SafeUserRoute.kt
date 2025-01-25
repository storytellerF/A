package com.storyteller_f.a.server.route

import com.maxmind.geoip2.DatabaseReader
import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.server.auth.omitPrincipal
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.auth.usePrincipalOrNull
import com.storyteller_f.a.server.common.pagination
import com.storyteller_f.a.server.service.getTopics
import com.storyteller_f.a.server.service.updateUser
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.tables.getUser
import com.storyteller_f.tables.getUserByAid
import com.storyteller_f.tables.searchMembers
import io.ktor.server.resources.*
import io.ktor.server.routing.Route

fun Route.bindProtectedSafeUserRoute(reader: DatabaseReader, backend: Backend) {
    post<RouteUsers.Update> {
        usePrincipal(reader) { id ->
            updateUser(id, backend)
        }
    }
}

fun Route.bindSafeUserRoute(backend: Backend, reader: DatabaseReader) {
    get<RouteUsers> { value ->
        omitPrincipal(reader) {
            value.aid?.let { DatabaseFactory.getUserByAid(it, backend) } ?: Result.success(null)
        }
    }
    get<RouteUsers.Id> {
        omitPrincipal(reader) {
            DatabaseFactory.getUser(it.id, backend = backend)
        }
    }

    get<RouteUsers.Id.Topics> {
        usePrincipalOrNull(reader) { id ->
            pagination(PrimaryKey::class, {
                it.id.toString()
            }) { p, n, s ->
                getTopics(it.parent.id, ObjectType.USER, id, backend, p, n, s, it.fillHasCommented)
            }
        }
    }

    get<RouteUsers.Search> {
        omitPrincipal(reader) {
            pagination(PrimaryKey::class, {
                it.id.toString()
            }) { p, n, s ->
                DatabaseFactory.searchMembers(null, backend, p, n, s, it.word)
            }
        }
    }
}
