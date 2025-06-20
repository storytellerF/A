package com.storyteller_f.a.server.route

import com.storyteller_f.a.api.core.Api
import com.storyteller_f.a.api.server.invoke
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.exposed.isDup
import com.storyteller_f.a.server.auth.handleResult
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.auth.usePrincipalOrNull1
import com.storyteller_f.a.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.server.common.pagination
import com.storyteller_f.a.server.service.*
import com.storyteller_f.backend.service.Backend
import com.storyteller_f.backend.service.getUserInfo
import com.storyteller_f.backend.service.searchMembers
import com.storyteller_f.shared.obj.NewDevice
import com.storyteller_f.shared.obj.UpdateUserBody
import com.storyteller_f.shared.obj.UpdateUserRead
import com.storyteller_f.shared.type.ObjectType
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext

fun Route.bindProtectedSafeUserRoute(backend: Backend) {
    post<RouteUsers.Update> {
        usePrincipal { uid ->
            backend.updateUser(uid, call.receive<UpdateUserBody>())
        }
    }
    post<RouteUsers.Read> {
        usePrincipal { uid ->
            val tuple = call.receive<UpdateUserRead>()
            backend.addReadLog(uid, tuple)
        }
    }
    post<RouteUsers.Device> {
        usePrincipal { uid ->
            val newDevice = call.receive<NewDevice>()
            backend.exposedDatabase.userDatabase.addDevice(uid, newDevice.endpointUrl).recover {
                if (it.isDup()) {
                    Result.success(Unit)
                } else {
                    Result.failure(it)
                }
            }
        }
    }
}

fun Route.bindSafeUserRoute(backend: Backend) {
    Api.Users.Aid.get.invoke(RoutingContext::handleResult) {
        backend.getUserInfo(ObjectFetch.AidFetch(it.aid))
    }
    Api.Users.Id.get.invoke(RoutingContext::handleResult) {
        backend.getUserInfo(ObjectFetch.IdFetch(it.id))
    }

    Api.Users.Id.Topics.get.invoke(RoutingContext::handleResult) { q, p ->
        usePrincipalOrNull1 { uid ->
            q.pagination(IdentifiablePagingGenerator) { f ->
                backend.getTopLevelTopicsInObject(
                    p.id,
                    ObjectType.USER,
                    uid,
                    q.fillHasCommented,
                    f,
                    q.pinType
                )
            }
        }
    }

    Api.Users.Id.Titles.get.invoke(RoutingContext::handleResult) { q, p ->
        q.pagination(IdentifiablePagingGenerator) { f ->
            backend.getUserTitles(p.id, q.searchType, q.type, q.scopeId, f)
        }
    }

    Api.Users.Search.get.invoke(RoutingContext::handleResult) {
        it.pagination(IdentifiablePagingGenerator) { f ->
            backend.searchMembers(null, it.word, f)
        }
    }
}
