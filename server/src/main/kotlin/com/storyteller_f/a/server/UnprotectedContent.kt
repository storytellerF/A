package com.storyteller_f.a.server

import com.storyteller_f.a.server.auth.omitPrincipal
import com.storyteller_f.a.server.auth.usePrincipalOrNull
import com.storyteller_f.a.server.common.checkParameter
import com.storyteller_f.a.server.common.checkQueryParameter
import com.storyteller_f.a.server.service.*
import com.storyteller_f.shared.hmacVerify
import com.storyteller_f.shared.model.CommunityInfo
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.obj.TopicSnapshotPack
import com.storyteller_f.shared.type.OKey
import com.storyteller_f.shared.type.ObjectType
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*


fun Routing.unProtectedContent() {
    get("/world") {
        call.respond(searchWorld())
    }

    bindRoomRoute()

    bindCommunityRoute()

    bindTopicRoute()

    bindUserRoute()
}

private fun Routing.bindUserRoute() {
    route("/user") {
        get("/{id}") {
            omitPrincipal {
                checkParameter<OKey, UserInfo>("id") {
                    getUser(it)
                }
            }
        }

    }
}

private fun Routing.bindTopicRoute() {
    route("/topic") {
        get("/{id}/topics") {
            usePrincipalOrNull { uid ->
                checkParameter<OKey, ServerResponse<TopicInfo>>("id") {
                    getTopics(it, ObjectType.TOPIC, uid)
                }
            }
        }

        get("/{id}") {
            usePrincipalOrNull {
                checkParameter<OKey, TopicInfo>("id") { topicId ->
                    getTopic(topicId, it)
                }
            }
        }

        post("/verify-snapshot") {
            omitPrincipal {
                verifySnapshot()
            }
        }

    }
}


private fun Routing.bindCommunityRoute() {
    route("/community") {
        get("/{id}/topics") {
            omitPrincipal {
                checkParameter<OKey, ServerResponse<TopicInfo>>("id") {
                    getTopics(it, ObjectType.COMMUNITY)
                }
            }
        }
        get("/{id}/rooms") {
            usePrincipalOrNull { uid ->
                checkParameter<OKey, ServerResponse<RoomInfo>>("id") {
                    searchRoomInCommunity(it, uid)
                }
            }
        }

        get("/{communityId}") {
            omitPrincipal {
                checkParameter<OKey, CommunityInfo>("communityId") {
                    getCommunity(it)
                }
            }
        }
        get("/search") {
            omitPrincipal {
                checkQueryParameter<String, ServerResponse<CommunityInfo>>("word") {
                    searchCommunities(it)
                }
            }
        }
    }
}

private fun Routing.bindRoomRoute() {
    route("/room") {
        get("/{id}/topics") {
            usePrincipalOrNull { uid ->
                checkParameter<OKey, ServerResponse<TopicInfo>>("id") {
                    getTopics(it, ObjectType.ROOM, uid)
                }
            }
        }

        get("/{roomId}") {
            usePrincipalOrNull { uid ->
                checkParameter<OKey, RoomInfo>("roomId") {
                    getRoom(it, uid)
                }
            }
        }
        get("/search") {
            usePrincipalOrNull { id ->
                checkQueryParameter<String, ServerResponse<RoomInfo>>("word") {
                    searchRooms(it, id)
                }
            }
        }
    }
}
