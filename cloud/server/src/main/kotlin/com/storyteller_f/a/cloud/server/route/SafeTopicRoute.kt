package com.storyteller_f.a.cloud.server.route

import com.storyteller_f.a.api.core.CustomApi
import com.storyteller_f.a.api.server.invoke
import com.storyteller_f.a.api.server.receiveBody
import com.storyteller_f.a.backend.core.UnauthorizedException
import com.storyteller_f.a.backend.service.Backend
import com.storyteller_f.a.cloud.server.auth.handleResult
import com.storyteller_f.a.cloud.server.auth.usePrincipal
import com.storyteller_f.a.cloud.server.auth.usePrincipalOrNull
import com.storyteller_f.a.cloud.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.cloud.server.common.ReactionPaginationGenerator
import com.storyteller_f.a.cloud.server.common.pagination
import com.storyteller_f.a.cloud.server.service.*
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.safeFirstEmoji
import io.ktor.server.plugins.*
import io.ktor.server.routing.*

fun Route.bindTopicRoute(backend: Backend) {
    CustomApi.Topics.search.invoke(RoutingContext::handleResult) {
        usePrincipalOrNull { uid ->
            it.pagination(IdentifiablePagingGenerator) { f ->
                backend.searchPublicTopics(it, f, uid)
            }
        }
    }

    CustomApi.Topics.recommend.invoke(RoutingContext::handleResult) {
        usePrincipalOrNull { uid ->
            pagination(IdentifiablePagingGenerator) { f ->
                if (uid == null && it.fillHasCommented == true) {
                    Result.failure(UnauthorizedException())
                } else {
                    backend.recommendTopics(uid, f)
                }
            }
        }
    }

    CustomApi.Topics.Id.get.invoke(RoutingContext::handleResult) { q, p ->
        usePrincipalOrNull { uid ->
            backend.getTopic(p.id, uid, q.fillHasCommented)
        }
    }

    CustomApi.Topics.Aid.get.invoke(RoutingContext::handleResult) {
        usePrincipalOrNull { uid ->
            backend.getTopicByAid(it.aid, uid, it.fillHasCommented)
        }
    }

    CustomApi.Topics.Id.Topics.get.invoke(RoutingContext::handleResult) { q, p ->
        usePrincipalOrNull { uid ->
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
    CustomApi.Topics.Id.Reactions.get.invoke(RoutingContext::handleResult) { q, p ->
        usePrincipalOrNull { uid ->
            q.pagination(ReactionPaginationGenerator(backend)) { fetch ->
                backend.reactionList(p.id, uid, q.fillHasReacted, fetch)
            }
        }
    }
}

fun Route.bindProtectedTopicRoute(backend: Backend) {
    CustomApi.Topics.Id.createSnapshot.invoke(RoutingContext::handleResult) { p, api ->
        usePrincipal { uid ->
            backend.createTopicSnapshot(uid, p.id)
        }
    }

    CustomApi.Topics.add.invoke(RoutingContext::handleResult) { api ->
        usePrincipal { uid ->
            backend.createPublicTopic(uid, with(api) { receiveBody() })
        }
    }

    CustomApi.Topics.Id.Reactions.add.invoke(RoutingContext::handleResult) { p, api ->
        usePrincipal { uid ->
            val emoji = with(api) { receiveBody() }.emoji
            if (isEmoji(emoji)) {
                backend.addReaction(uid, p.id, emoji)
            } else {
                Result.failure(BadRequestException("invalid emoji"))
            }
        }
    }
    CustomApi.Topics.Id.Reactions.delete.invoke(RoutingContext::handleResult) { p, api ->
        usePrincipal { uid ->
            val deleteReaction = with(api) { receiveBody() }
            val emoji = deleteReaction.emoji
            if (isEmoji(emoji)) {
                backend.exposedDatabase.topicDatabase.deleteReaction(uid, emoji, p.id).mapResult {
                    (if (it) {
                        backend.exposedDatabase.topicDatabase.statsReactionRecord(
                            p.id,
                            emoji,
                            ObjectType.TOPIC
                        )
                    } else {
                        Result.success(Unit)
                    }).mapResult {
                        backend.exposedDatabase.topicDatabase.getReactionInfo(uid, p.id, emoji).map {
                            it ?: ReactionInfo(emoji, p.id, 0, false, 0)
                        }
                    }
                }
            } else {
                Result.failure(BadRequestException("invalid emoji"))
            }
        }
    }

    CustomApi.Topics.Id.pin.invoke(RoutingContext::handleResult) { p, api ->
        usePrincipal { uid ->
            backend.updateTopicPin(uid, p.id, true)
        }
    }

    CustomApi.Topics.Id.unpin.invoke(RoutingContext::handleResult) { p, api ->
        usePrincipal { uid ->
            backend.updateTopicPin(uid, p.id, false)
        }
    }
}

private fun isEmoji(emoji: String): Boolean {
    return safeFirstEmoji(emoji)?.length == emoji.length
}
