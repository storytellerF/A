package com.storyteller_f.a.cloud.server.route

import com.storyteller_f.a.api.core.CustomApi
import com.storyteller_f.a.api.server.invoke
import com.storyteller_f.a.api.server.receiveBody
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.core.UnauthorizedException
import com.storyteller_f.a.backend.exposed.toJoinSearch
import com.storyteller_f.a.backend.service.Backend
import com.storyteller_f.a.backend.service.searchMembers
import com.storyteller_f.a.backend.service.searchRoomPaginationResult
import com.storyteller_f.a.cloud.server.auth.handleResult
import com.storyteller_f.a.cloud.server.auth.usePrincipal
import com.storyteller_f.a.cloud.server.auth.usePrincipalOrNull
import com.storyteller_f.a.cloud.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.cloud.server.common.PrimaryKeyPagingGenerator
import com.storyteller_f.a.cloud.server.common.pagination
import com.storyteller_f.a.cloud.server.service.*
import com.storyteller_f.shared.model.UserPubKeyInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.utils.mapResultIfNotNull
import io.ktor.server.routing.*

fun Route.bindRoomRoute(backend: Backend) {
    CustomApi.Rooms.search(RoutingContext::handleResult) {
        usePrincipalOrNull { uid ->
            pagination(IdentifiablePagingGenerator) { f ->
                backend.searchRoomPaginationResult(uid, it.word, it.community, f, it.joinStatus.toJoinSearch(uid))
            }
        }
    }

    CustomApi.Rooms.Id.Members.get.invoke(RoutingContext::handleResult) { q, p ->
        usePrincipalOrNull { uid ->
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
    CustomApi.Rooms.Aid.get.invoke(RoutingContext::handleResult) {
        usePrincipalOrNull { uid ->
            backend.getRoomInfo(ObjectFetch.AidFetch(it.aid), uid, it.fillJoinInfo)
        }
    }

    CustomApi.Rooms.Id.get.invoke(RoutingContext::handleResult) { q, p ->
        usePrincipalOrNull { uid ->
            backend.getRoomInfo(ObjectFetch.IdFetch(p.id), uid, q.fillJoinInfo)
        }
    }

    CustomApi.Rooms.Id.Topics.get.invoke(RoutingContext::handleResult) { q, p ->
        usePrincipalOrNull { uid ->
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

fun Route.bindProtectedRoomRoute(backend: Backend) {
    CustomApi.Rooms.Id.Members.join.invoke(RoutingContext::handleResult) { p, api ->
        usePrincipal { uid ->
            backend.joinRoom(p.id, uid)
        }
    }
    CustomApi.Rooms.Id.Members.leave.invoke(RoutingContext::handleResult) { p, api ->
        usePrincipal { uid ->
            backend.exitRoom(p.id, uid)
        }
    }
    CustomApi.Rooms.Id.Members.publicKeys.invoke(RoutingContext::handleResult) { q, p ->
        usePrincipal { uid ->
            pagination(object : PrimaryKeyPagingGenerator<UserPubKeyInfo>(UserPubKeyInfo::id) {}) { f ->
                backend.getRoomPubKeys(p.id, uid, f)
            }
        }
    }
    CustomApi.Rooms.add.invoke(RoutingContext::handleResult) { api ->
        val newRoom = with(api) { receiveBody() }
        usePrincipal { uid ->
            backend.createRoom(newRoom, uid)
        }
    }
    CustomApi.Rooms.Id.update.invoke(RoutingContext::handleResult) { p, api ->
        val newRoom = with(api) { receiveBody() }
        usePrincipal { uid ->
            backend.updateRoom(p.id, newRoom, uid)
        }
    }
}
