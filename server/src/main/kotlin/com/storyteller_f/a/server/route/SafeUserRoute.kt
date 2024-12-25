package com.storyteller_f.a.server.route

import com.maxmind.geoip2.DatabaseReader
import com.storyteller_f.Backend
import com.storyteller_f.a.server.auth.omitPrincipal
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.common.pagination
import com.storyteller_f.a.server.service.updateUser
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.tables.getUser
import com.storyteller_f.tables.getUserByAid
import com.storyteller_f.tables.searchMembers
import io.ktor.server.resources.*
import io.ktor.server.routing.Route

fun Route.bindProtectedSafeUserRoute(reader: DatabaseReader) {
    post<RouteUsers.Update> {
        usePrincipal(reader) { id ->
            updateUser(id)
        }
    }
}

fun Route.bindSafeUserRoute(backend: Backend, reader: DatabaseReader) {
    get<RouteUsers> { value ->
        omitPrincipal(reader) {
            value.aid?.let { getUserByAid(it, backend) } ?: Result.success(null)
        }
    }
    get<RouteUsers.Id> {
        omitPrincipal(reader) {
            getUser(it.id, backend = backend)
        }
    }

    get<RouteUsers.Search> {
        omitPrincipal(reader) {
            pagination(PrimaryKey::class, {
                it.id.toString()
            }) { p, n, s ->
                searchMembers(null, backend, p, n, s, it.word)
            }
        }
    }
}
