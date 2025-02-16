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
import com.storyteller_f.tables.deleteReaction
import com.yy.mobile.emoji.EmojiReader
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.routing.Route

fun Route.bindSafeTopicRoute(backend: Backend, reader: DatabaseReader) {
    get<RouteTopics.Search> {
        usePrincipalOrNull(reader) { id ->
            pagination(PrimaryKey::class, {
                it.id.toString()
            }) { p, n, s ->
                searchPublicTopics(p, n, s, it, backend, id)
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
        usePrincipalOrNull(reader) { id ->
            getTopic(it.id, id, backend, it.parent.fillHasCommented)
        }
    }

    get<RouteTopics> {
        usePrincipalOrNull(reader) { id ->
            it.aid?.let { aid -> getTopicByAid(aid, id, backend, it.fillHasCommented) } ?: Result.success(null)
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
                    it.parent.parent.fillHasCommented
                )
            }
        }
    }
    get<RouteTopics.Id.Reactions> {
        usePrincipalOrNull(reader) { id ->
            reactionList(it.parent.id, id, it.parent.parent.fillHasCommented)
        }
    }
}

fun Route.bindProtectedSafeTopicRoute(backend: Backend, reader: DatabaseReader) {
    get<RouteTopics.Id.Snapshot> {
        usePrincipal(reader) { id ->
            createTopicSnapshot(id, it.parent.id, backend)
        }
    }

    post<RouteTopics> {
        usePrincipal(reader) {
            val topic = call.receive<NewTopic>()
            addTopicAtCommunity(it, backend, topic)
        }
    }

    post<RouteTopics.Id.Reactions> {
        usePrincipal(reader) { id ->
            val emoji = call.receive<NewReaction>().emoji
            if (isEmoji(emoji)) {
                addReaction(id, it.parent.id, emoji)
            } else {
                Result.failure(BadRequestException("invalid emoji"))
            }
        }
    }

    post<RouteReactions.Delete> {
        usePrincipal(reader) { id ->
            val deleteReaction = call.receive<DeleteReaction>()
            val emoji = deleteReaction.emoji
            if (isEmoji(emoji)) {
                deleteReaction(id, emoji, deleteReaction.objectId)
            } else {
                Result.failure(BadRequestException("invalid emoji"))
            }
        }
    }
}

private fun isEmoji(emoji: String) = EmojiReader.getTextLength(emoji) == 1 && EmojiReader.isEmojiOfCharIndex(emoji, 0)
