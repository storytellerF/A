package com.storyteller_f.a.server.route

import com.storyteller_f.a.api.core.Api
import com.storyteller_f.a.api.server.invoke
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.UnauthorizedException
import com.storyteller_f.a.exposed.toJoinSearch
import com.storyteller_f.a.server.auth.handleResult
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.auth.usePrincipalOrNull1
import com.storyteller_f.a.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.server.common.PrimaryKeyPagingGenerator
import com.storyteller_f.a.server.common.pagination
import com.storyteller_f.a.server.service.*
import com.storyteller_f.backend.service.Backend
import com.storyteller_f.backend.service.searchMembers
import com.storyteller_f.backend.service.searchRoomPaginationResult
import com.storyteller_f.shared.model.UserPubKeyInfo
import com.storyteller_f.shared.obj.NewRoom
import com.storyteller_f.shared.obj.UpdateRoomBody
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.utils.mapResultIfNotNull
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext

fun Route.bindSafeRoomRoute(backend: Backend) {
    Api.Rooms.Search.get(RoutingContext::handleResult) {
        usePrincipalOrNull1 { uid ->
            pagination(IdentifiablePagingGenerator) { f ->
                backend.searchRoomPaginationResult(uid, it.word, it.community, f, it.joinStatus.toJoinSearch(uid))
            }
        }
    }

    Api.Rooms.Id.Members.get.invoke(RoutingContext::handleResult) { q, p ->
        usePrincipalOrNull1 { uid ->
            pagination(IdentifiablePagingGenerator) { f ->
                backend.checkRootReadPermission(ObjectType.ROOM, p.id, uid).mapResultIfNotNull { permission ->
                    if (permission.hasRead) {
                        backend.searchMembers(p.id, q.word, f)
                    } else {
                        Result.failure(UnauthorizedException())
                    }
                }
            }
        }
    }
    Api.Rooms.Aid.get.invoke(RoutingContext::handleResult) {
        usePrincipalOrNull1 { uid ->
            backend.getRoomInfo(ObjectFetch.AidFetch(it.aid), uid, it.fillJoinInfo)
        }
    }

    Api.Rooms.Id.get.invoke(RoutingContext::handleResult) { q, p ->
        usePrincipalOrNull1 { uid ->
            backend.getRoomInfo(ObjectFetch.IdFetch(p.id), uid, q.fillJoinInfo)
        }
    }

    Api.Rooms.Id.Topics.get.invoke(RoutingContext::handleResult) { q, p ->
        usePrincipalOrNull1 { uid ->
            pagination(IdentifiablePagingGenerator) { f ->
                backend.getTopLevelTopicsInObject(
                    p.id,
                    ObjectType.ROOM,
                    uid,
                    q.fillHasCommented,
                    f,
                    q.pinType
                )
            }
        }
    }
}

fun Route.bindProtectedSafeRoomRoute(backend: Backend) {
    post<RouteRooms.Id.Join> {
        usePrincipal { uid ->
            backend.joinRoom(it.parent.id, uid)
        }
    }
    get<RouteRooms.Id.PubKeys> {
        usePrincipal { uid ->
            pagination(object : PrimaryKeyPagingGenerator<UserPubKeyInfo>(UserPubKeyInfo::id) {}) { f ->
                backend.getRoomPubKeys(it.parent.id, uid, f)
            }
        }
    }
    post<RouteRooms.Id.Exit> {
        usePrincipal { uid ->
            backend.exitRoom(it.parent.id, uid)
        }
    }
    post<RouteRooms> {
        val newRoom = call.receive<NewRoom>()
        usePrincipal { uid ->
            backend.createRoom(newRoom, uid)
        }
    }
    post<RouteRooms.Id> {
        val newRoom = call.receive<UpdateRoomBody>()
        usePrincipal { uid ->
            backend.updateRoom(it.id, newRoom, uid)
        }
    }
}
