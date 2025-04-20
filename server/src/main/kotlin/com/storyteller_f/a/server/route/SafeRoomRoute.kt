package com.storyteller_f.a.server.route

import com.maxmind.geoip2.DatabaseReader
import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.UnauthorizedException
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.auth.usePrincipalOrNull
import com.storyteller_f.a.server.common.CustomPagingGenerator
import com.storyteller_f.a.server.common.IdentityPagingGenerator
import com.storyteller_f.a.server.common.pagination
import com.storyteller_f.a.server.service.*
import com.storyteller_f.shared.obj.NewRoom
import com.storyteller_f.shared.obj.UpdateRoomBody
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResultNotNull
import com.storyteller_f.tables.PagingFetch
import com.storyteller_f.tables.searchMembers
import com.storyteller_f.tables.searchRooms
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.routing.Route

fun Route.bindSafeRoomRoute(reader: DatabaseReader, backend: Backend) {
    get<RouteRooms.Search> {
        usePrincipalOrNull(reader) { uid ->
            pagination(IdentityPagingGenerator) { p, n, size ->
                searchRooms(backend, uid, it.joinStatus, it.word, it.community, PagingFetch(p, n, size))
            }
        }
    }

    get<RouteRooms.Id.Members> {
        usePrincipalOrNull(reader) { uid ->
            pagination(IdentityPagingGenerator) { p, n, s ->
                checkRootReadPermission(backend, ObjectType.ROOM, it.parent.id, uid).mapResultNotNull { permission ->
                    if (permission.hasRead) {
                        DatabaseFactory.searchMembers(backend, it.parent.id, it.word, PagingFetch(p, n, s))
                    } else {
                        Result.failure(UnauthorizedException())
                    }
                }
            }
        }
    }

    get<RouteRooms> {
        usePrincipalOrNull(reader) { uid ->
            it.aid?.let { aid ->
                getRoom(backend, null, aid, uid, it.fillJoinInfo)
            } ?: Result.success(null)
        }
    }

    get<RouteRooms.Id> {
        usePrincipalOrNull(reader) { uid ->
            getRoom(backend, it.id, null, uid, it.parent.fillJoinInfo)
        }
    }

    get<RouteRooms.Id.Topics> {
        usePrincipalOrNull(reader) { uid ->
            pagination(IdentityPagingGenerator) { pre, next, size ->
                getTopLevelTopicsInObject(
                    backend,
                    it.parent.id,
                    ObjectType.ROOM,
                    uid,
                    it.fillHasCommented,
                    PagingFetch(pre, next, size),
                    it.pinType
                )
            }
        }
    }
}

fun Route.bindProtectedSafeRoomRoute(reader: DatabaseReader, backend: Backend) {
    post<RouteRooms.Id.Join> {
        usePrincipal(reader) { uid ->
            joinRoom(backend, it.parent.id, uid)
        }
    }
    get<RouteRooms.Id.PubKeys> {
        usePrincipal(reader) { uid ->
            pagination(object : CustomPagingGenerator<Pair<PrimaryKey, String>>({
                it.first.toString()
            }) {}) { pre, next, size ->
                getRoomPubKeys(backend, it.parent.id, uid, PagingFetch(pre, next, size))
            }
        }
    }
    post<RouteRooms.Id.Exit> {
        usePrincipal(reader) { uid ->
            exitRoom(backend, it.parent.id, uid)
        }
    }
    post<RouteRooms> {
        val newRoom = call.receive<NewRoom>()
        usePrincipal(reader) { uid ->
            createRoom(backend, newRoom, uid)
        }
    }
    post<RouteRooms.Id> {
        val newRoom = call.receive<UpdateRoomBody>()
        usePrincipal(reader) { uid ->
            updateRoom(backend, it.id, newRoom, uid)
        }
    }
}
