package com.storyteller_f.a.server.route

import com.storyteller_f.a.api.core.Api
import com.storyteller_f.a.api.server.invoke
import com.storyteller_f.a.server.auth.handleResult
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.auth.usePrincipalOrNull1
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
import io.ktor.server.routing.RoutingContext

fun Route.bindSafeTopicRoute(backend: Backend) {
    Api.Topics.Search.get.invoke(RoutingContext::handleResult) {
        usePrincipalOrNull1 { uid ->
            it.pagination(IdentifiablePagingGenerator) { f ->
                backend.searchPublicTopics(it, f, uid)
            }
        }
    }

    Api.Topics.Recommend.get.invoke(RoutingContext::handleResult) {
        usePrincipalOrNull1 { uid ->
            pagination(IdentifiablePagingGenerator) { f ->
                backend.recommendTopics(uid, f)
            }
        }
    }

    Api.Topics.Id.get.invoke(RoutingContext::handleResult) { q, p ->
        usePrincipalOrNull1 { uid ->
            backend.getTopic(p.id, uid, q.fillHasCommented)
        }
    }

    Api.Topics.Aid.get.invoke(RoutingContext::handleResult) {
        usePrincipalOrNull1 { uid ->
            backend.getTopicByAid(it.aid, uid, it.fillHasCommented)
        }
    }

    Api.Topics.Id.Topics.get.invoke(RoutingContext::handleResult) { q, p ->
        usePrincipalOrNull1 { uid ->
            q.pagination(IdentifiablePagingGenerator) { f ->
                backend.getTopLevelTopicsInObject(
                    p.id,
                    ObjectType.TOPIC,
                    uid,
                    q.fillHasCommented,
                    f,
                    q.pinType
                )
            }
        }
    }
    Api.Topics.Id.Reactions.get.invoke(RoutingContext::handleResult) { q, p ->
        usePrincipalOrNull1 { uid ->
            q.pagination(ReactionPaginationGenerator(backend)) { fetch ->
                backend.reactionList(p.id, uid, q.fillHasReacted, fetch)
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
