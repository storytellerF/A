package com.storyteller_f.a.server

import com.storyteller_f.Backend
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.common.checkParameter
import com.storyteller_f.a.server.common.pagination
import com.storyteller_f.a.server.service.*
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.obj.TopicSnapshotPack
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.types.PaginationResult
import io.ktor.server.routing.*
import io.ktor.server.websocket.*

fun Route.protectedContent(backend: Backend) {
    bindProtectedRoomRoute(backend)
    bindProtectedCommunityRoute(backend)
    bindProtectedTopicRoute(backend)
    bindProtectedUserRoute()
    webSocket("/link") {
        webSocketContent(backend)
    }
}

private fun Route.bindProtectedUserRoute() {
    route("/user") {
        post("/update") {
            usePrincipal { id ->
                updateUser(id)
            }
        }
    }
}

private fun Route.bindProtectedTopicRoute(backend: Backend) {
    route("/topic") {
        post {
            usePrincipal {
                addTopicAtCommunity(it, backend)
            }
        }
        get("/{id}/snapshot") {
            usePrincipal { id ->
                checkParameter<PrimaryKey, TopicSnapshotPack> { topicId ->
                    getTopicSnapshot(id, topicId, backend)
                }
            }
        }
    }
}

private fun Route.bindProtectedCommunityRoute(backend: Backend) {
    route("/community") {
        get("/joined") {
            usePrincipal {
                pagination<CommunityInfo, PrimaryKey>(PrimaryKey::class, {
                    it.id.toString()
                }) { pre, next, size ->
                    searchJoinedCommunities(it, backend, pre, next, size)
                }
            }
        }
        post("/{id}/join") {
            usePrincipal { id ->
                checkParameter<PrimaryKey, Boolean>("id") {
                    joinCommunity(id, it)
                }
            }
        }
    }
}

private fun Route.bindProtectedRoomRoute(backend: Backend) {
    route("/room") {
        get("/joined") {
            usePrincipal {
                pagination<RoomInfo, PrimaryKey>(PrimaryKey::class, {
                    it.id.toString()
                }) { pre, next, size ->
                    searchJoinedRooms(it, backend = backend, pre, next, size)
                }
            }
        }
        post("/{id}/join") {
            usePrincipal { id ->
                checkParameter<PrimaryKey, RoomInfo>("id") {
                    joinRoom(it, id)
                }
            }
        }
        get("/{id}/pub-keys") {
            usePrincipal { id ->
                pagination<Pair<PrimaryKey, String>, PrimaryKey>(PrimaryKey::class, {
                    it.first.toString()
                }) { pre, next, size ->
                    checkParameter<PrimaryKey, PaginationResult<Pair<PrimaryKey, String>>>("id") {
                        getRoomPubKeys(it, id, pre, next, size)
                    }
                }
            }
        }
    }
}
