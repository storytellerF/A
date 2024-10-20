package com.storyteller_f.a.server

import com.storyteller_f.Backend
import com.storyteller_f.a.server.auth.omitPrincipal
import com.storyteller_f.a.server.auth.usePrincipalOrNull
import com.storyteller_f.a.server.common.checkParameter
import com.storyteller_f.a.server.common.checkQueryParameter
import com.storyteller_f.a.server.common.pagination
import com.storyteller_f.a.server.service.*
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import io.ktor.server.routing.*

fun Route.unProtectedContent(backend: Backend) {
    get("/world") {
        omitPrincipal {
            pagination<TopicInfo, ULong>({
                it.id.toString()
            }) { prePageToken, nextPageToken, size ->
                searchWorld(backend, prePageToken, nextPageToken, size)
            }
        }
    }

    bindRoomRoute(backend)

    bindCommunityRoute(backend)

    bindTopicRoute(backend)

    bindUserRoute(backend)
}

private fun Route.bindUserRoute(backend: Backend) {
    route("/user") {
        get("/{id}") {
            omitPrincipal {
                checkParameter<PrimaryKey, UserInfo>("id") {
                    getUser(it, backend = backend)
                }
            }
        }
    }
}

private fun Route.bindTopicRoute(backend: Backend) {
    route("/topic") {
        get("/{id}/topics") {
            usePrincipalOrNull { uid ->
                pagination<TopicInfo, PrimaryKey>({
                    it.id.toString()
                }) { p, n, s ->
                    checkParameter<PrimaryKey, Pair<List<TopicInfo>, Long>>("id") {
                        getTopics(it, ObjectType.TOPIC, uid, backend, p, n, s)
                    }
                }
            }
        }

        get("/{id}") {
            usePrincipalOrNull {
                checkParameter<PrimaryKey, TopicInfo>("id") { topicId ->
                    getTopic(topicId, it, backend)
                }
            }
        }

        post("/verify-snapshot") {
            omitPrincipal {
                verifySnapshot(backend)
            }
        }
    }
}

private fun Route.bindCommunityRoute(backend: Backend) {
    route("/community") {
        get("/{id}/topics") {
            omitPrincipal {
                pagination<TopicInfo, PrimaryKey>({
                    it.id.toString()
                }) { p, n, size ->
                    checkParameter<PrimaryKey, Pair<List<TopicInfo>, Long>>("id") {
                        getTopics(
                            it,
                            ObjectType.COMMUNITY,
                            backend = backend,
                            preTopicId = p,
                            nextTopicId = n,
                            size = size
                        )
                    }
                }
            }
        }
        get("/{id}/rooms") {
            usePrincipalOrNull { uid ->
                pagination<RoomInfo, PrimaryKey>({
                    it.id.toString()
                }) { p, n, size ->
                    checkParameter<PrimaryKey, Pair<List<RoomInfo>, Long>>("id") {
                        searchRoomInCommunity(it, uid, backend, p, n, size)
                    }
                }
            }
        }

        get("/{communityId}") {
            omitPrincipal {
                checkParameter<PrimaryKey, CommunityInfo>("communityId") {
                    getCommunity(it, backend)
                }
            }
        }
        get("/search") {
            omitPrincipal {
                pagination<CommunityInfo, PrimaryKey>({
                    it.id.toString()
                }) { p, n, s ->
                    checkQueryParameter<String, Pair<List<CommunityInfo>, Long>>("word") {
                        searchCommunities(it, backend, p, n, s)
                    }
                }
            }
        }
    }
}

private fun Route.bindRoomRoute(backend: Backend) {
    route("/room") {
        get("/{id}/topics") {
            usePrincipalOrNull { uid ->
                pagination<TopicInfo, PrimaryKey>({
                    it.id.toString()
                }) { pre, next, size ->
                    checkParameter<PrimaryKey, Pair<List<TopicInfo>, Long>>("id") {
                        getTopics(it, ObjectType.ROOM, uid, backend, pre, next, size)
                    }
                }
            }
        }

        get("/{roomId}") {
            usePrincipalOrNull { uid ->
                checkParameter<PrimaryKey, RoomInfo>("roomId") {
                    getRoom(it, uid, backend)
                }
            }
        }
        get("/search") {
            usePrincipalOrNull { id ->
                pagination<RoomInfo, PrimaryKey>({
                    it.id.toString()
                }) { p, n, size ->
                    checkQueryParameter<String, Pair<List<RoomInfo>, Long>>("word") {
                        searchRooms(it, id, backend, p, n, size)
                    }
                }
            }
        }
    }
}
