package com.storyteller_f.a.server.route

import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.auth.usePrincipalOrNull
import com.storyteller_f.a.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.server.common.ReactionPaginationGenerator
import com.storyteller_f.a.server.common.pagination
import com.storyteller_f.a.server.service.*
import com.storyteller_f.backend.service.Backend
import com.storyteller_f.shared.obj.DeleteReaction
import com.storyteller_f.shared.obj.NewReaction
import com.storyteller_f.shared.obj.NewTopic
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.utils.safeFirstEmoji
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.routing.Route

fun Route.bindSafeTopicRoute(backend: Backend) {
    get<RouteTopics.Search> {
        usePrincipalOrNull { uid ->
            pagination(IdentifiablePagingGenerator) { f ->
                backend.searchPublicTopics(it, f, uid)
            }
        }
    }

    get<RouteTopics.Recommend> {
        usePrincipalOrNull { uid ->
            pagination(IdentifiablePagingGenerator) { f ->
                backend.recommendTopics(
                    uid,
                    f
                )
            }
        }
    }

    get<RouteTopics.Id> {
        usePrincipalOrNull { uid ->
            backend.getTopic(it.id, uid, it.parent.fillHasCommented)
        }
    }

    get<RouteTopics.Aid> {
        usePrincipalOrNull { uid ->
            it.aid?.let { aid -> backend.getTopicByAid(aid, uid, it.parent.fillHasCommented) } ?: Result.success(
                null
            )
        }
    }

    get<RouteTopics.Id.Topics> {
        usePrincipalOrNull { uid ->
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
        usePrincipalOrNull { uid ->
            pagination(ReactionPaginationGenerator(backend)) { fetch ->
                backend.reactionList(it.parent.id, uid, it.fillHasReacted, fetch)
            }
        }
    }
}

fun Route.bindProtectedSafeTopicRoute(backend: Backend) {
    get<RouteTopics.Id.Snapshot> {
        usePrincipal { uid ->
            backend.createTopicSnapshot(uid, it.parent.id)
        }
    }

    post<RouteTopics> {
        usePrincipal { uid ->
            val topic = call.receive<NewTopic>()
            backend.createPublicTopic(uid, topic)
        }
    }

    post<RouteTopics.Id.Reactions> {
        usePrincipal { uid ->
            val emoji = call.receive<NewReaction>().emoji
            if (isEmoji(emoji)) {
                backend.addReaction(uid, it.parent.id, emoji)
            } else {
                Result.failure(BadRequestException("invalid emoji"))
            }
        }
    }

    post<RouteReactions.Delete> {
        usePrincipal { uid ->
            val deleteReaction = call.receive<DeleteReaction>()
            val emoji = deleteReaction.emoji
            if (isEmoji(emoji)) {
                backend.exposedDatabase.topicDatabase.deleteReaction(uid, emoji, deleteReaction.objectId)
            } else {
                Result.failure(BadRequestException("invalid emoji"))
            }
        }
    }

    post<RouteTopics.Id.Pin> {
        usePrincipal { uid ->
            backend.updateTopicPin(uid, it.parent.id, true)
        }
    }

    post<RouteTopics.Id.Unpin> {
        usePrincipal { uid ->
            backend.updateTopicPin(uid, it.parent.id, false)
        }
    }
}

private fun isEmoji(emoji: String): Boolean {
    return safeFirstEmoji(emoji)?.length == emoji.length
}
