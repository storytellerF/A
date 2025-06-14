package com.storyteller_f.a.server.route

import com.maxmind.geoip2.DatabaseReader
import com.storyteller_f.a.server.auth.omitPrincipal
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.auth.usePrincipalOrNull
import com.storyteller_f.a.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.server.common.pagination
import com.storyteller_f.a.server.service.*
import com.storyteller_f.backend.service.Backend
import com.storyteller_f.backend.service.ObjectFetch
import com.storyteller_f.backend.service.getUserInfo
import com.storyteller_f.backend.service.isDup
import com.storyteller_f.backend.service.query.addDevice
import com.storyteller_f.backend.service.searchMembers
import com.storyteller_f.shared.obj.NewDevice
import com.storyteller_f.shared.obj.UpdateUserBody
import com.storyteller_f.shared.obj.UpdateUserRead
import com.storyteller_f.shared.type.ObjectType
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.routing.Route

fun Route.bindProtectedSafeUserRoute(reader: DatabaseReader, backend: Backend) {
    post<RouteUsers.Update> {
        usePrincipal(reader) { uid ->
            backend.updateUser(uid, call.receive<UpdateUserBody>())
        }
    }
    post<RouteUsers.Read> {
        usePrincipal(reader) { uid ->
            val tuple = call.receive<UpdateUserRead>()
            backend.addReadLog(uid, tuple)
        }
    }
    post<RouteUsers.Device> {
        usePrincipal(reader) { uid ->
            val newDevice = call.receive<NewDevice>()
            backend.databaseSession.addDevice(uid, newDevice.endpointUrl).recover {
                if (it.isDup()) {
                    Result.success(Unit)
                } else {
                    Result.failure(it)
                }
            }
        }
    }
}

fun Route.bindSafeUserRoute(reader: DatabaseReader, backend: Backend) {
    get<RouteUsers.Aid> { value ->
        omitPrincipal(reader) {
            value.aid?.let { backend.getUserInfo(ObjectFetch.AidFetch(it)) }
                ?: Result.success(
                    null
                )
        }
    }
    get<RouteUsers.Id> {
        omitPrincipal(reader) {
            backend.getUserInfo(ObjectFetch.IdFetch(it.id))
        }
    }

    get<RouteUsers.Id.Topics> {
        usePrincipalOrNull(reader) { uid ->
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
        omitPrincipal(reader) {
            pagination(IdentifiablePagingGenerator) { f ->
                backend.getUserTitles(r.parent.id, r.searchType, r.type, r.scopeId, f)
            }
        }
    }

    get<RouteUsers.Search> {
        omitPrincipal(reader) {
            pagination(IdentifiablePagingGenerator) { f ->
                backend.searchMembers(null, it.word, f)
            }
        }
    }
}
