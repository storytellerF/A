package com.storyteller_f.a.cloud.server.route

import com.storyteller_f.a.api.CustomApi
import com.storyteller_f.a.api.MemberInfoListResponse
import com.storyteller_f.a.api.NewFavorite
import com.storyteller_f.a.api.NewSubscription
import com.storyteller_f.a.api.TopicInfoListResponse
import com.storyteller_f.a.api.UserPubKeyInfoListResponse
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.ObjectFetch
import com.storyteller_f.a.cloud.core.service.addFavorite
import com.storyteller_f.a.cloud.core.service.addSubscription
import com.storyteller_f.a.cloud.core.service.createRoom
import com.storyteller_f.a.cloud.core.service.deleteFavoriteByObject
import com.storyteller_f.a.cloud.core.service.exitRoom
import com.storyteller_f.a.cloud.core.service.getRoomInfo
import com.storyteller_f.a.cloud.core.service.getRoomMemberInfos
import com.storyteller_f.a.cloud.core.service.getRoomPubKeys
import com.storyteller_f.a.cloud.core.service.getTopicsByParentId
import com.storyteller_f.a.cloud.core.service.joinRoom
import com.storyteller_f.a.cloud.core.service.removeSubscriptionByObject
import com.storyteller_f.a.cloud.core.service.searchRoomMembers
import com.storyteller_f.a.cloud.core.service.updateRoom
import com.storyteller_f.a.cloud.server.auth.handleResult
import com.storyteller_f.a.cloud.server.auth.usePrincipal
import com.storyteller_f.a.cloud.server.auth.usePrincipalOrNull
import com.storyteller_f.a.cloud.server.common.GeneralOffsetPagingGenerator
import com.storyteller_f.a.cloud.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.cloud.server.common.PrimaryKeyPagingGenerator
import com.storyteller_f.a.cloud.server.common.pagination
import com.storyteller_f.endpoint4k.ktor.server.invoke
import com.storyteller_f.endpoint4k.ktor.server.receiveBody
import com.storyteller_f.shared.model.UserPubKeyInfo
import com.storyteller_f.shared.type.ObjectType
import io.ktor.server.routing.Route

fun Route.bindRoomRoute(backend: Backend) {
    CustomApi.Rooms.Id.Members.get(handleResult()) { q, p ->
        usePrincipalOrNull { uid ->
            q.pagination(IdentifiablePagingGenerator, { l, p ->
                MemberInfoListResponse(l, p)
            }) { f ->
                // 检查权限
                backend.getRoomMemberInfos(p.id, uid, f)
            }
        }
    }

    CustomApi.Rooms.Id.Members.search(handleResult()) { q, p ->
        usePrincipalOrNull { uid ->
            q.pagination(GeneralOffsetPagingGenerator, { l, p ->
                MemberInfoListResponse(l, p)
            }) { f ->
                searchRoomMembers(backend, p, uid, q, f)
            }
        }
    }
    CustomApi.Rooms.Aid.get(handleResult()) {
        usePrincipalOrNull { uid ->
            backend.getRoomInfo(ObjectFetch.AidFetch(it.aid), uid, it.fillJoinInfo)
        }
    }

    CustomApi.Rooms.Id.get(handleResult()) { q, p ->
        usePrincipalOrNull { uid ->
            backend.getRoomInfo(ObjectFetch.IdFetch(p.id), uid, q.fillJoinInfo)
        }
    }

    CustomApi.Rooms.Id.Topics.get(handleResult()) { q, p ->
        usePrincipalOrNull { uid ->
            q.pagination(IdentifiablePagingGenerator, { l, p ->
                TopicInfoListResponse(l, p)
            }) { f ->
                backend.getTopicsByParentId(p.id, ObjectType.ROOM, uid, q.fillHasCommented, f, q.pinType)
            }
        }
    }
}

fun Route.bindProtectedRoomRoute(backend: Backend) {
    CustomApi.Rooms.Id.Members.join(handleResult()) { p, api ->
        usePrincipal { uid ->
            backend.joinRoom(p.id, uid)
        }
    }
    CustomApi.Rooms.Id.Members.leave(handleResult()) { p, api ->
        usePrincipal { uid ->
            backend.exitRoom(p.id, uid)
        }
    }
    CustomApi.Rooms.Id.Members.publicKeys(handleResult()) { q, p ->
        usePrincipal { uid ->
            q.pagination(object : PrimaryKeyPagingGenerator<UserPubKeyInfo>(UserPubKeyInfo::id) {}, { l, p ->
                UserPubKeyInfoListResponse(l, p)
            }) { f ->
                backend.getRoomPubKeys(p.id, uid, f)
            }
        }
    }
    CustomApi.Rooms.add(handleResult()) { api ->
        val newRoom = api.receiveBody()
        usePrincipal { uid ->
            backend.createRoom(newRoom, uid)
        }
    }
    CustomApi.Rooms.Id.update(handleResult()) { p, api ->
        val newRoom = api.receiveBody()
        usePrincipal { uid ->
            backend.updateRoom(p.id, newRoom, uid)
        }
    }

    CustomApi.Rooms.Id.Favorite.add(handleResult()) { p, _ ->
        usePrincipal { uid ->
            backend.addFavorite(uid, NewFavorite(ObjectType.ROOM, p.id)).map { }
        }
    }
    CustomApi.Rooms.Id.Favorite.delete(handleResult()) { p, _ ->
        usePrincipal { uid ->
            backend.deleteFavoriteByObject(uid, p.id).map { }
        }
    }
    CustomApi.Rooms.Id.Subscription.add(handleResult()) { p, _ ->
        usePrincipal { uid ->
            backend.addSubscription(uid, NewSubscription(p.id, ObjectType.ROOM)).map { }
        }
    }
    CustomApi.Rooms.Id.Subscription.delete(handleResult()) { p, _ ->
        usePrincipal { uid ->
            backend.removeSubscriptionByObject(uid, p.id).map { }
        }
    }
}
