package com.storyteller_f.a.server

import com.storyteller_f.Backend
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.auth.usePrincipalOrNull
import com.storyteller_f.a.server.common.pagination
import com.storyteller_f.a.server.service.RouteRooms
import com.storyteller_f.a.server.service.exitRoom
import com.storyteller_f.a.server.service.getRoom
import com.storyteller_f.a.server.service.getRoomPubKeys
import com.storyteller_f.a.server.service.getTopics
import com.storyteller_f.a.server.service.joinRoom
import com.storyteller_f.a.server.service.searchMembers
import com.storyteller_f.a.server.service.searchRooms
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import io.ktor.server.resources.*
import io.ktor.server.routing.Route

fun Route.bindSafeRoomRoute(backend: Backend) {
    get<RouteRooms.Search> {
        usePrincipalOrNull { id ->
            pagination<RoomInfo, PrimaryKey>(PrimaryKey::class, {
                it.id.toString()
            }) { p, n, size ->
                searchRooms(id, backend, p, n, size, it)
            }
        }
    }

    get<RouteRooms.Id.Members> {
        usePrincipalOrNull { id ->
            pagination<UserInfo, PrimaryKey>(PrimaryKey::class, {
                it.id.toString()
            }) { p, n, s ->
                searchMembers(it.parent.id, backend, p, n, s, it.word)
            }
        }
    }

    get<RouteRooms> {
        usePrincipalOrNull { id ->
            it.aid?.let { aid ->
                getRoom(null, aid, id, backend, it.fillJoinInfo)
            } ?: Result.success(null)
        }
    }

    get<RouteRooms.Id> {
        usePrincipalOrNull { id ->
            getRoom(it.id, null, id, backend, it.parent.fillJoinInfo)
        }
    }

    get<RouteRooms.Id.Topics> {
        usePrincipalOrNull { uid ->
            pagination<TopicInfo, PrimaryKey>(PrimaryKey::class, {
                it.id.toString()
            }) { pre, next, size ->
                getTopics(it.parent.id, ObjectType.ROOM, uid, backend, pre, next, size)
            }
        }
    }
}

fun Route.bindProtectedSafeRoomRoute(backend: Backend) {
    post<RouteRooms.Id.Join> {
        usePrincipal { id ->
            joinRoom(it.parent.id, id, backend)
        }
    }
    get<RouteRooms.Id.PubKeys> {
        usePrincipal { id ->
            pagination<Pair<PrimaryKey, String>, PrimaryKey>(PrimaryKey::class, {
                it.first.toString()
            }) { pre, next, size ->
                getRoomPubKeys(it.parent.id, id, pre, next, size)
            }
        }
    }
    post<RouteRooms.Id.Exit> {
        usePrincipal { uid ->
            exitRoom(it.parent.id, uid, backend)
        }
    }
}
