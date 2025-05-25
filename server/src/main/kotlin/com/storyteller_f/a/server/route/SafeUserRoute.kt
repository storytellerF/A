package com.storyteller_f.a.server.route

import com.maxmind.geoip2.DatabaseReader
import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.a.server.auth.omitPrincipal
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.auth.usePrincipalOrNull
import com.storyteller_f.a.server.common.IdentityPagingGenerator
import com.storyteller_f.a.server.common.pagination
import com.storyteller_f.a.server.service.*
import com.storyteller_f.shared.obj.NewDevice
import com.storyteller_f.shared.obj.UpdateUserBody
import com.storyteller_f.shared.obj.UpdateUserRead
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.tables.ObjectFetch
import com.storyteller_f.tables.getUserAndRelatedMedia
import com.storyteller_f.tables.searchMembers
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.routing.Route

fun Route.bindProtectedSafeUserRoute(reader: DatabaseReader, backend: Backend) {
    post<RouteUsers.Update> {
        usePrincipal(reader) { uid ->
            updateUser(backend, uid, call.receive<UpdateUserBody>())
        }
    }
    post<RouteUsers.Read> {
        usePrincipal(reader) { uid ->
            val tuple = call.receive<UpdateUserRead>()
            addReadLog(backend, uid, tuple)
        }
    }
    post<RouteUsers.Device> {
        usePrincipal(reader) { uid ->
            val newDevice = call.receive<NewDevice>()
            addDevice(backend, uid, newDevice.endpointUrl)
        }
    }
}

fun Route.bindSafeUserRoute(reader: DatabaseReader, backend: Backend) {
    get<RouteUsers.Aid> { value ->
        omitPrincipal(reader) {
            value.aid?.let { DatabaseFactory.getUserAndRelatedMedia(backend, ObjectFetch.AidFetch(it)) }
                ?: Result.success(
                    null
                )
        }
    }
    get<RouteUsers.Id> {
        omitPrincipal(reader) {
            DatabaseFactory.getUserAndRelatedMedia(backend, ObjectFetch.IdFetch(it.id))
        }
    }

    get<RouteUsers.Id.Topics> {
        usePrincipalOrNull(reader) { uid ->
            pagination(IdentityPagingGenerator) { f ->
                getTopLevelTopicsInObject(
                    backend,
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
            pagination(IdentityPagingGenerator) { f ->
                getUserTitles(backend, r.parent.id, r.searchType, r.type, r.scopeId, f)
            }
        }
    }

    get<RouteUsers.Search> {
        omitPrincipal(reader) {
            pagination(IdentityPagingGenerator) { f ->
                DatabaseFactory.searchMembers(backend, null, it.word, f)
            }
        }
    }
}
