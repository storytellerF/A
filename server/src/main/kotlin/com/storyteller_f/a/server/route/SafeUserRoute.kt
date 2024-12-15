package com.storyteller_f.a.server.route

import com.storyteller_f.Backend
import com.storyteller_f.a.server.auth.omitPrincipal
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.common.pagination
import com.storyteller_f.a.server.service.RouteUsers
import com.storyteller_f.a.server.service.updateUser
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.tables.getUser
import com.storyteller_f.tables.getUserByAid
import com.storyteller_f.tables.searchMembers
import io.ktor.server.resources.*
import io.ktor.server.routing.Route

fun Route.bindProtectedSafeUserRoute() {
    post<RouteUsers.Update> {
        usePrincipal { id ->
            updateUser(id)
        }
    }
}

fun Route.bindSafeUserRoute(backend: Backend) {
    get<RouteUsers> { value ->
        omitPrincipal {
            value.aid?.let { getUserByAid(it, backend) } ?: Result.success(null)
        }
    }
    get<RouteUsers.Id> {
        omitPrincipal {
            getUser(it.id, backend = backend)
        }
    }

    get<RouteUsers.Search> {
        omitPrincipal {
            pagination(PrimaryKey::class, {
                it.id.toString()
            }) { p, n, s ->
                searchMembers(null, backend, p, n, s, it.word)
            }
        }
    }
}
