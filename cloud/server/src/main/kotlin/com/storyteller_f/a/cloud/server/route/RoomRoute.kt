package com.storyteller_f.a.cloud.server.route

import com.storyteller_f.a.api.core.CustomApi
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.backend.service.Backend
import com.storyteller_f.a.cloud.core.service.createRoom
import com.storyteller_f.a.cloud.core.service.exitRoom
import com.storyteller_f.a.cloud.core.service.getRoomInfo
import com.storyteller_f.a.cloud.core.service.getRoomPubKeys
import com.storyteller_f.a.cloud.core.service.getTopLevelTopicsInObject
import com.storyteller_f.a.cloud.core.service.joinRoom
import com.storyteller_f.a.cloud.core.service.searchRoomMembers
import com.storyteller_f.a.cloud.core.service.searchRoomPaginationResult
import com.storyteller_f.a.cloud.core.service.updateRoom
import com.storyteller_f.a.cloud.server.auth.handleResult
import com.storyteller_f.a.cloud.server.auth.usePrincipal
import com.storyteller_f.a.cloud.server.auth.usePrincipalOrNull
import com.storyteller_f.a.cloud.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.cloud.server.common.PrimaryKeyPagingGenerator
import com.storyteller_f.a.cloud.server.common.pagination
import com.storyteller_f.route4k.ktor.server.invoke
import com.storyteller_f.route4k.ktor.server.receiveBody
import com.storyteller_f.shared.model.UserPubKeyInfo
import com.storyteller_f.shared.type.ObjectType
import io.ktor.server.routing.*

fun Route.bindRoomRoute(backend: Backend) {
    CustomApi.Rooms.search.invoke(RoutingContext::handleResult) {
        usePrincipalOrNull { uid ->
            pagination(IdentifiablePagingGenerator) { f ->
                backend.searchRoomPaginationResult(
                    uid,
                    f,
                    it
                )
            }
        }
    }

    CustomApi.Rooms.Id.Members.get.invoke(RoutingContext::handleResult) { q, p ->
        usePrincipalOrNull { uid ->
            pagination(IdentifiablePagingGenerator) { f ->
                searchRoomMembers(backend, p, uid, q, f)
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
        val newRoom = api.receiveBody()
        usePrincipal { uid ->
            backend.createRoom(newRoom, uid)
        }
    }
    CustomApi.Rooms.Id.update.invoke(RoutingContext::handleResult) { p, api ->
        val newRoom = api.receiveBody()
        usePrincipal { uid ->
            backend.updateRoom(p.id, newRoom, uid)
        }
    }
}
