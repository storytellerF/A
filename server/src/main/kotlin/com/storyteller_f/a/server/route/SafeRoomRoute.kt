package com.storyteller_f.a.server.route

import com.storyteller_f.Backend
import com.storyteller_f.UnauthorizedException
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.auth.usePrincipalOrNull
import com.storyteller_f.a.server.common.pagination
import com.storyteller_f.a.server.service.*
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResultNotNull
import com.storyteller_f.tables.searchMembers
import com.storyteller_f.tables.searchRooms
import io.ktor.server.resources.*
import io.ktor.server.routing.Route

fun Route.bindSafeRoomRoute(backend: Backend) {
    get<RouteRooms.Search> {
        usePrincipalOrNull { id ->
            pagination(PrimaryKey::class, {
                it.id.toString()
            }) { p, n, size ->
                searchRooms(id, backend, p, n, size, it.joinStatus, it.word, it.community)
            }
        }
    }

    get<RouteRooms.Id.Members> {
        usePrincipalOrNull { id ->
            pagination(PrimaryKey::class, {
                it.id.toString()
            }) { p, n, s ->
                checkRootReadPermission(ObjectType.ROOM, it.parent.id, id).mapResultNotNull { permission ->
                    if (permission.hasRead) {
                        searchMembers(it.parent.id, backend, p, n, s, it.word)
                    } else {
                        Result.failure(UnauthorizedException())
                    }
                }
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
            pagination(PrimaryKey::class, {
                it.id.toString()
            }) { pre, next, size ->
                getTopics(it.parent.id, ObjectType.ROOM, uid, backend, pre, next, size, it.fillHasCommented)
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
            pagination(PrimaryKey::class, {
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
