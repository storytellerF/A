package com.storyteller_f.a.server

import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.common.checkParameter
import com.storyteller_f.a.server.service.*
import com.storyteller_f.shared.obj.NewTopic
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.obj.TopicSnapshotPack
import com.storyteller_f.shared.type.OKey
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*

fun Routing.protectedContent() {
    bindProtectedRoomRoute()
    bindProtectedCommunityRoute()
    bindProtectedTopicRoute()
    webSocket("/link") {
        webSocketContent()
    }
}

private fun Routing.bindProtectedTopicRoute() {
    route("/topic") {
        post {
            usePrincipal {
                addTopicAtCommunity(it)
            }
        }
        get("/{id}/snapshot") {
            usePrincipal { id ->
                checkParameter<OKey, TopicSnapshotPack> { topicId ->
                    getTopicSnapshot(id, topicId)
                }
            }
        }
    }
}

private fun Routing.bindProtectedCommunityRoute() {
    route("/community") {
        get("/joined") {
            usePrincipal {
                searchJoinedCommunities(it)
            }
        }
        post("/{id}/join") {
            usePrincipal { id ->
                checkParameter<OKey, Unit>("id") {
                    joinCommunity(id, it)
                }

            }
        }
    }
}


private fun Routing.bindProtectedRoomRoute() {
    route("/room") {
        get("/joined") {
            usePrincipal {
                searchJoinedRooms(it)
            }
        }
        post("/{id}/join") {
            usePrincipal { id ->
                checkParameter<OKey, Unit>("id") {
                    joinRoom(it, id)
                }
            }
        }
        get("/{id}/pub-keys") {
            usePrincipal { id ->
                checkParameter<OKey, ServerResponse<Pair<OKey, String>>>("id") {
                    getRoomPubKeys(it, id)
                }
            }
        }
    }
}
