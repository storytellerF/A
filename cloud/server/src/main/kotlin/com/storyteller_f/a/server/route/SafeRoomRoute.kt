package com.storyteller_f.a.server.route

import com.maxmind.geoip2.DatabaseReader
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.auth.usePrincipalOrNull
import com.storyteller_f.a.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.server.common.PrimaryKeyPagingGenerator
import com.storyteller_f.a.server.common.pagination
import com.storyteller_f.a.server.service.*
import com.storyteller_f.backend.service.Backend
import com.storyteller_f.backend.service.ObjectFetch
import com.storyteller_f.backend.service.searchMembers
import com.storyteller_f.backend.service.searchRoomPaginationResult
import com.storyteller_f.shared.model.UserPubKeyInfo
import com.storyteller_f.shared.obj.NewRoom
import com.storyteller_f.shared.obj.UpdateRoomBody
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.UnauthorizedException
import com.storyteller_f.shared.utils.mapResultIfNotNull
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.routing.Route

fun Route.bindSafeRoomRoute(reader: DatabaseReader, backend: Backend) {
    get<RouteRooms.Search> {
        usePrincipalOrNull(reader) { uid ->
            pagination(IdentifiablePagingGenerator) { f ->
                backend.searchRoomPaginationResult(uid, it.joinStatus, it.word, it.community, f)
            }
        }
    }

    get<RouteRooms.Id.Members> {
        usePrincipalOrNull(reader) { uid ->
            pagination(IdentifiablePagingGenerator) { f ->
                backend.checkRootReadPermission(ObjectType.ROOM, it.parent.id, uid).mapResultIfNotNull { permission ->
                    if (permission.hasRead) {
                        backend.searchMembers(it.parent.id, it.word, f)
                    } else {
                        Result.failure(UnauthorizedException())
                    }
                }
            }
        }
    }

    get<RouteRooms.Aid> {
        usePrincipalOrNull(reader) { uid ->
            it.aid?.let { aid ->
                backend.getRoomInfo(ObjectFetch.AidFetch(aid), uid, it.parent.fillJoinInfo)
            } ?: Result.success(null)
        }
    }

    get<RouteRooms.Id> {
        usePrincipalOrNull(reader) { uid ->
            backend.getRoomInfo(ObjectFetch.IdFetch(it.id), uid, it.parent.fillJoinInfo)
        }
    }

    get<RouteRooms.Id.Topics> {
        usePrincipalOrNull(reader) { uid ->
            pagination(IdentifiablePagingGenerator) { f ->
                backend.getTopLevelTopicsInObject(
                    it.parent.id,
                    ObjectType.ROOM,
                    uid,
                    it.fillHasCommented,
                    f,
                    it.pinType
                )
            }
        }
    }
}

fun Route.bindProtectedSafeRoomRoute(reader: DatabaseReader, backend: Backend) {
    post<RouteRooms.Id.Join> {
        usePrincipal(reader) { uid ->
            backend.joinRoom(it.parent.id, uid)
        }
    }
    get<RouteRooms.Id.PubKeys> {
        usePrincipal(reader) { uid ->
            pagination(object : PrimaryKeyPagingGenerator<UserPubKeyInfo>(UserPubKeyInfo::id) {}) { f ->
                backend.getRoomPubKeys(it.parent.id, uid, f)
            }
        }
    }
    post<RouteRooms.Id.Exit> {
        usePrincipal(reader) { uid ->
            backend.exitRoom(it.parent.id, uid)
        }
    }
    post<RouteRooms> {
        val newRoom = call.receive<NewRoom>()
        usePrincipal(reader) { uid ->
            backend.createRoom(newRoom, uid)
        }
    }
    post<RouteRooms.Id> {
        val newRoom = call.receive<UpdateRoomBody>()
        usePrincipal(reader) { uid ->
            backend.updateRoom(it.id, newRoom, uid)
        }
    }
}
