package com.storyteller_f.a.server.route

import com.maxmind.geoip2.DatabaseReader
import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.UnauthorizedException
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.auth.usePrincipalOrNull
import com.storyteller_f.a.server.common.pagination
import com.storyteller_f.a.server.service.*
import com.storyteller_f.shared.obj.NewRoom
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResultNotNull
import com.storyteller_f.tables.searchMembers
import com.storyteller_f.tables.searchRooms
import io.ktor.server.request.receive
import io.ktor.server.resources.*
import io.ktor.server.routing.Route

fun Route.bindSafeRoomRoute(backend: Backend, reader: DatabaseReader) {
    get<RouteRooms.Search> {
        usePrincipalOrNull(reader) { uid ->
            pagination(PrimaryKey::class, {
                it.id.toString()
            }) { p, n, size ->
                searchRooms(uid, backend, p, n, size, it.joinStatus, it.word, it.community)
            }
        }
    }

    get<RouteRooms.Id.Members> {
        usePrincipalOrNull(reader) { uid ->
            pagination(PrimaryKey::class, {
                it.id.toString()
            }) { p, n, s ->
                checkRootReadPermission(ObjectType.ROOM, it.parent.id, uid).mapResultNotNull { permission ->
                    if (permission.hasRead) {
                        DatabaseFactory.searchMembers(it.parent.id, backend, p, n, s, it.word)
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
                getRoom(null, aid, uid, backend, it.fillJoinInfo)
            } ?: Result.success(null)
        }
    }

    get<RouteRooms.Id> {
        usePrincipalOrNull(reader) { uid ->
            getRoom(it.id, null, uid, backend, it.parent.fillJoinInfo)
        }
    }

    get<RouteRooms.Id.Topics> {
        usePrincipalOrNull(reader) { uid ->
            pagination(PrimaryKey::class, {
                it.id.toString()
            }) { pre, next, size ->
                getTopLevelTopicsInObject(
                    it.parent.id,
                    ObjectType.ROOM,
                    uid,
                    backend,
                    pre,
                    next,
                    size,
                    it.fillHasCommented,
                    it.pinType
                )
            }
        }
    }
}

fun Route.bindProtectedSafeRoomRoute(backend: Backend, reader: DatabaseReader) {
    post<RouteRooms.Id.Join> {
        usePrincipal(reader) { uid ->
            joinRoom(it.parent.id, uid, backend)
        }
    }
    get<RouteRooms.Id.PubKeys> {
        usePrincipal(reader) { uid ->
            pagination(PrimaryKey::class, {
                it.first.toString()
            }) { pre, next, size ->
                getRoomPubKeys(it.parent.id, uid, pre, next, size)
            }
        }
    }
    post<RouteRooms.Id.Exit> {
        usePrincipal(reader) { uid ->
            exitRoom(it.parent.id, uid, backend)
        }
    }
    post<RouteRooms> {
        val newRoom = call.receive<NewRoom>()
        usePrincipal(reader) { uid ->
            createRoom(newRoom, uid, backend)
        }
    }
}
