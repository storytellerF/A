package com.storyteller_f.a.server

import com.storyteller_f.Backend
import com.storyteller_f.a.server.auth.omitPrincipal
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.common.pagination
import com.storyteller_f.a.server.service.RouteUsers
import com.storyteller_f.a.server.service.getUser
import com.storyteller_f.a.server.service.getUserByAid
import com.storyteller_f.a.server.service.searchMembers
import com.storyteller_f.a.server.service.updateUser
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.PrimaryKey
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
            pagination<UserInfo, PrimaryKey>(PrimaryKey::class, {
                it.id.toString()
            }) { p, n, s ->
                searchMembers(null, backend, p, n, s, it.word)
            }
        }
    }
}