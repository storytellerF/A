package com.storyteller_f.a.server.route

import com.maxmind.geoip2.DatabaseReader
import com.storyteller_f.Backend
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.auth.usePrincipalOrNull
import com.storyteller_f.a.server.common.pagination
import com.storyteller_f.a.server.service.*
import com.storyteller_f.shared.obj.DeleteReaction
import com.storyteller_f.shared.obj.NewReaction
import com.storyteller_f.shared.obj.NewTopic
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.safeFirstEmoji
import com.storyteller_f.tables.deleteReaction
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.routing.Route

fun Route.bindSafeTopicRoute(backend: Backend, reader: DatabaseReader) {
    get<RouteTopics.Search> {
        usePrincipalOrNull(reader) { uid ->
            pagination(PrimaryKey::class, {
                it.id.toString()
            }) { p, n, s ->
                searchPublicTopics(p, n, s, it, backend, uid)
            }
        }
    }

    get<RouteTopics.Recommend> {
        usePrincipalOrNull(reader) { uid ->
            pagination(PrimaryKey::class, {
                it.id.toString()
            }) { prePageToken, nextPageToken, size ->
                recommendTopics(backend, prePageToken, nextPageToken, size, uid, it.parent.fillHasCommented)
            }
        }
    }

    get<RouteTopics.Id> {
        usePrincipalOrNull(reader) { uid ->
            getTopic(it.id, uid, backend, it.parent.fillHasCommented)
        }
    }

    get<RouteTopics> {
        usePrincipalOrNull(reader) { uid ->
            it.aid?.let { aid -> getTopicByAid(aid, uid, backend, it.fillHasCommented) } ?: Result.success(null)
        }
    }

    get<RouteTopics.Id.Topics> {
        usePrincipalOrNull(reader) { uid ->
            pagination(PrimaryKey::class, {
                it.id.toString()
            }) { p, n, s ->
                getTopLevelTopicsInObject(
                    it.parent.id,
                    ObjectType.TOPIC,
                    uid,
                    backend,
                    p,
                    n,
                    s,
                    it.parent.parent.fillHasCommented,
                    it.pinType
                )
            }
        }
    }
    get<RouteTopics.Id.Reactions> {
        usePrincipalOrNull(reader) { uid ->
            reactionList(it.parent.id, uid, it.fillHasReacted)
        }
    }
}

fun Route.bindProtectedSafeTopicRoute(backend: Backend, reader: DatabaseReader) {
    get<RouteTopics.Id.Snapshot> {
        usePrincipal(reader) { uid ->
            createTopicSnapshot(uid, it.parent.id, backend)
        }
    }

    post<RouteTopics> {
        usePrincipal(reader) { uid ->
            val topic = call.receive<NewTopic>()
            createPublicTopic(uid, backend, topic)
        }
    }

    post<RouteTopics.Id.Reactions> {
        usePrincipal(reader) { uid ->
            val emoji = call.receive<NewReaction>().emoji
            if (isEmoji(emoji)) {
                addReaction(uid, it.parent.id, emoji)
            } else {
                Result.failure(BadRequestException("invalid emoji"))
            }
        }
    }

    post<RouteReactions.Delete> {
        usePrincipal(reader) { uid ->
            val deleteReaction = call.receive<DeleteReaction>()
            val emoji = deleteReaction.emoji
            if (isEmoji(emoji)) {
                deleteReaction(uid, emoji, deleteReaction.objectId)
            } else {
                Result.failure(BadRequestException("invalid emoji"))
            }
        }
    }

    post<RouteTopics.Id.Pin> {
        usePrincipal(reader) { uid ->
            updateTopicPin(uid, it.parent.id, true)
        }
    }

    post<RouteTopics.Id.Unpin> {
        usePrincipal(reader) { uid ->
            updateTopicPin(uid, it.parent.id, false)
        }
    }
}

private fun isEmoji(emoji: String): Boolean {
    return emoji.safeFirstEmoji()?.length == emoji.length
}
