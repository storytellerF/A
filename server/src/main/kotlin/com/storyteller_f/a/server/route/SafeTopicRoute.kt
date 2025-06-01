package com.storyteller_f.a.server.route

import com.maxmind.geoip2.DatabaseReader
import com.storyteller_f.Backend
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.auth.usePrincipalOrNull
import com.storyteller_f.a.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.server.common.PagingGenerator
import com.storyteller_f.a.server.common.pagination
import com.storyteller_f.a.server.reactionChannel
import com.storyteller_f.a.server.service.*
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.obj.DeleteReaction
import com.storyteller_f.shared.obj.NewReaction
import com.storyteller_f.shared.obj.NewTopic
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.utils.onNotNull
import com.storyteller_f.shared.utils.safeFirstEmoji
import com.storyteller_f.tables.deleteReaction
import com.storyteller_f.types.Cursor
import com.storyteller_f.types.ReactionCursorKey
import com.storyteller_f.types.ReactionFetch
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.routing.Route

fun Route.bindSafeTopicRoute(reader: DatabaseReader, backend: Backend) {
    get<RouteTopics.Search> {
        usePrincipalOrNull(reader) { uid ->
            pagination(IdentifiablePagingGenerator) { f ->
                backend.searchPublicTopics(it, f, uid)
            }
        }
    }

    get<RouteTopics.Recommend> {
        usePrincipalOrNull(reader) { uid ->
            pagination(IdentifiablePagingGenerator) { f ->
                backend.recommendTopics(
                    uid,
                    it.parent.fillHasCommented,
                    f
                )
            }
        }
    }

    get<RouteTopics.Id> {
        usePrincipalOrNull(reader) { uid ->
            backend.getTopic(it.id, uid, it.parent.fillHasCommented)
        }
    }

    get<RouteTopics.Aid> {
        usePrincipalOrNull(reader) { uid ->
            it.aid?.let { aid -> backend.getTopicByAid(aid, uid, it.parent.fillHasCommented) } ?: Result.success(
                null
            )
        }
    }

    get<RouteTopics.Id.Topics> {
        usePrincipalOrNull(reader) { uid ->
            pagination(IdentifiablePagingGenerator) { f ->
                backend.getTopLevelTopicsInObject(
                    it.parent.id,
                    ObjectType.TOPIC,
                    uid,
                    it.parent.parent.fillHasCommented,
                    f,
                    it.pinType
                )
            }
        }
    }
    get<RouteTopics.Id.Reactions> {
        usePrincipalOrNull(reader) { uid ->
            pagination(object : PagingGenerator<ReactionInfo, ReactionFetch> {
                override fun parse(prePageToken: String?, nextPageToken: String?, size: Int): ReactionFetch {
                    return ReactionFetch(
                        when {
                            !nextPageToken.isNullOrBlank() -> Cursor.NextCursor(
                                backend.json.decodeFromString<ReactionCursorKey>(
                                    nextPageToken
                                )
                            )

                            !prePageToken.isNullOrBlank() -> Cursor.PreCursor(
                                backend.json.decodeFromString<ReactionCursorKey>(
                                    prePageToken
                                )
                            )

                            else -> null
                        },
                        size
                    )
                }

                override fun generate(list: List<ReactionInfo>, size: Int): Pair<String?, String?> {
                    val next = if (size <= list.size) {
                        val last = list.last()
                        backend.json.encodeToString(ReactionCursorKey(last.count, last.lastReactionId))
                    } else {
                        null
                    }
                    val pre = if (list.isNotEmpty()) {
                        val first = list.first()
                        backend.json.encodeToString(ReactionCursorKey(first.count, first.lastReactionId))
                    } else {
                        null
                    }
                    return pre to next
                }
            }) { fetch ->
                backend.reactionList(it.parent.id, uid, it.fillHasReacted, fetch)
            }
        }
    }
}

fun Route.bindProtectedSafeTopicRoute(reader: DatabaseReader, backend: Backend) {
    get<RouteTopics.Id.Snapshot> {
        usePrincipal(reader) { uid ->
            backend.createTopicSnapshot(uid, it.parent.id)
        }
    }

    post<RouteTopics> {
        usePrincipal(reader) { uid ->
            val topic = call.receive<NewTopic>()
            backend.createPublicTopic(uid, topic)
        }
    }

    post<RouteTopics.Id.Reactions> {
        usePrincipal(reader) { uid ->
            val emoji = call.receive<NewReaction>().emoji
            if (isEmoji(emoji)) {
                backend.addReaction(uid, it.parent.id, emoji).onNotNull { (_, reactionRecord) ->
                    if (reactionRecord != null) {
                        reactionChannel.send(reactionRecord)
                    }
                }
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
                backend.deleteReaction(uid, emoji, deleteReaction.objectId)
            } else {
                Result.failure(BadRequestException("invalid emoji"))
            }
        }
    }

    post<RouteTopics.Id.Pin> {
        usePrincipal(reader) { uid ->
            backend.updateTopicPin(uid, it.parent.id, true)
        }
    }

    post<RouteTopics.Id.Unpin> {
        usePrincipal(reader) { uid ->
            backend.updateTopicPin(uid, it.parent.id, false)
        }
    }
}

private fun isEmoji(emoji: String): Boolean {
    return safeFirstEmoji(emoji)?.length == emoji.length
}
