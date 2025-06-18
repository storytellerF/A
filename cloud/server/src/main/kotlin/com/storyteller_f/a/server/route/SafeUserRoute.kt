package com.storyteller_f.a.server.route

import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.exposed.isDup
import com.storyteller_f.a.server.auth.omitPrincipal
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.auth.usePrincipalOrNull
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
    get<RouteUsers.Aid> { value ->
        omitPrincipal {
            value.aid?.let { backend.getUserInfo(ObjectFetch.AidFetch(it)) }
                ?: Result.success(
                    null
                )
        }
    }
    get<RouteUsers.Id> {
        omitPrincipal {
            backend.getUserInfo(ObjectFetch.IdFetch(it.id))
        }
    }

    get<RouteUsers.Id.Topics> {
        usePrincipalOrNull { uid ->
            pagination(IdentifiablePagingGenerator) { f ->
                backend.getTopLevelTopicsInObject(
                    it.parent.id,
                    ObjectType.USER,
                    uid,
                    it.fillHasCommented,
                    f,
                    it.pinType
                )
            }
        }
    }

    get<RouteUsers.Id.Titles> { r ->
        omitPrincipal {
            pagination(IdentifiablePagingGenerator) { f ->
                backend.getUserTitles(r.parent.id, r.searchType, r.type, r.scopeId, f)
            }
        }
    }

    get<RouteUsers.Search> {
        omitPrincipal {
            pagination(IdentifiablePagingGenerator) { f ->
                backend.searchMembers(null, it.word, f)
            }
        }
    }
}
