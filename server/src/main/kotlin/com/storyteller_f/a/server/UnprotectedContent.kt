package com.storyteller_f.a.server

import com.storyteller_f.Backend
import com.storyteller_f.a.server.auth.omitPrincipal
import com.storyteller_f.a.server.auth.usePrincipalOrNull
import com.storyteller_f.a.server.common.checkParameter
import com.storyteller_f.a.server.common.checkQueryParameter
import com.storyteller_f.a.server.common.getOrFailCompact
import com.storyteller_f.a.server.service.*
import com.storyteller_f.shared.model.*
import com.storyteller_f.shared.obj.Pagination
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.OKey
import com.storyteller_f.shared.type.ObjectType
import io.ktor.server.routing.*
import io.ktor.util.converters.*
import io.ktor.util.reflect.*


fun Route.unProtectedContent(backend: Backend) {
    get("/world") {
        omitPrincipal {
            pagination<TopicInfo, ULong>({
                it.id.toString()
            }) { prePageToken, nextPageToken, size ->
                runCatching {
                    searchWorld(backend, prePageToken, nextPageToken, size)
                }
            }
        }
    }

    bindRoomRoute(backend)

    bindCommunityRoute(backend)

    bindTopicRoute(backend)

    bindUserRoute(backend)
}

inline fun <T, reified PageTokenType : Any> RoutingContext.pagination(
    nextKeyBuilder: (T) -> String,
    block: (PageTokenType?, PageTokenType?, Int) -> Result<Pair<List<T>, Long>?>
): Result<ServerResponse<T>?> {
    val v = kotlin.runCatching {
        val size = call.queryParameters.getOrFailCompact<Int>("size")

        require(size > 0) {
            "Invalid query size"
        }
        val nextPageToken = call.queryParameters["nextPageToken"]
        val prePageToken = call.queryParameters["prePageToken"]

        require(nextPageToken.isNullOrBlank() || prePageToken.isNullOrBlank()) {
            "Invalid query"
        }
        val (parsedPrePageToken, parsedNextPageToken) = if (!nextPageToken.isNullOrBlank()) {
            null to if (PageTokenType::class == ULong::class) {
                nextPageToken.toULong() as PageTokenType
            } else {
                DefaultConversionService.fromValue(nextPageToken, PageTokenType::class) as PageTokenType
            }
        } else if (!prePageToken.isNullOrBlank()) {
            if (PageTokenType::class == ULong::class) {
                prePageToken.toULong() as PageTokenType
            } else {
                DefaultConversionService.fromValue(prePageToken, PageTokenType::class) as PageTokenType
            } to null
        } else {
            null to null
        }
        Triple(parsedPrePageToken, parsedNextPageToken, size)
    }
    return when {
        v.isSuccess -> {
            val (prePageToken, nextPageToken, size) = v.getOrThrow()
            block(prePageToken, nextPageToken, size).map {
                it?.let { (list, count) ->
                    val next = if (size == list.size) nextKeyBuilder(list.last())
                    else null
                    val pre = if (list.isNotEmpty()) nextKeyBuilder(list.first())
                    else null
                    ServerResponse(list, Pagination(next, pre, count))
                }

            }
        }

        else -> Result.failure(v.exceptionOrNull()!!)
    }

}

private fun Route.bindUserRoute(backend: Backend) {
    route("/user") {
        get("/{id}") {
            omitPrincipal {
                checkParameter<OKey, UserInfo>("id") {
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
                pagination<TopicInfo, OKey>({
                    ""
                }) { p, n, s ->
                    checkParameter<OKey, Pair<List<TopicInfo>, Long>>("id") {
                        getTopics(it, ObjectType.TOPIC, uid, backend, p, n, s)
                    }
                }

            }
        }

        get("/{id}") {
            usePrincipalOrNull {
                checkParameter<OKey, TopicInfo>("id") { topicId ->
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
                checkParameter<OKey, ServerResponse<TopicInfo>>("id") {
                    getTopics(it, ObjectType.COMMUNITY, backend = backend, p = p, n = n, s = s)
                }
            }
        }
        get("/{id}/rooms") {
            usePrincipalOrNull { uid ->
                checkParameter<OKey, ServerResponse<RoomInfo>>("id") {
                    searchRoomInCommunity(it, uid, backend)
                }
            }
        }

        get("/{communityId}") {
            omitPrincipal {
                checkParameter<OKey, CommunityInfo>("communityId") {
                    getCommunity(it, backend)
                }
            }
        }
        get("/search") {
            omitPrincipal {
                pagination<CommunityInfo, OKey>({
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
                checkParameter<OKey, ServerResponse<TopicInfo>>("id") {
                    getTopics(it, ObjectType.ROOM, uid, backend, p, n, s)
                }
            }
        }

        get("/{roomId}") {
            usePrincipalOrNull { uid ->
                checkParameter<OKey, RoomInfo>("roomId") {
                    getRoom(it, uid, backend)
                }
            }
        }
        get("/search") {
            usePrincipalOrNull { id ->
                checkQueryParameter<String, ServerResponse<RoomInfo>>("word") {
                    searchRooms(it, id, backend)
                }
            }
        }
    }
}
