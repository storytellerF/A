package com.storyteller_f.a.cloud.server.route

import com.storyteller_f.a.api.CustomApi
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.UnauthorizedException
import com.storyteller_f.a.cloud.core.service.addReaction
import com.storyteller_f.a.cloud.core.service.createPlainTopic
import com.storyteller_f.a.cloud.core.service.createTopicSnapshot
import com.storyteller_f.a.cloud.core.service.deleteReaction
import com.storyteller_f.a.cloud.core.service.getTopic
import com.storyteller_f.a.cloud.core.service.getTopicByAid
import com.storyteller_f.a.cloud.core.service.getTopicsByParentId
import com.storyteller_f.a.cloud.core.service.reactionList
import com.storyteller_f.a.cloud.core.service.recommendTopics
import com.storyteller_f.a.cloud.core.service.searchPublicTopics
import com.storyteller_f.a.cloud.core.service.updateTopicPin
import com.storyteller_f.a.cloud.server.auth.handleResult
import com.storyteller_f.a.cloud.server.auth.usePrincipal
import com.storyteller_f.a.cloud.server.auth.usePrincipalOrNull
import com.storyteller_f.a.cloud.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.cloud.server.common.ReactionPaginationGenerator
import com.storyteller_f.a.cloud.server.common.pagination
import com.storyteller_f.endpoint4k.ktor.server.invoke
import com.storyteller_f.endpoint4k.ktor.server.receiveBody
import com.storyteller_f.shared.type.ObjectType
import io.ktor.server.routing.Route

fun Route.bindTopicRoute(backend: Backend) {
    CustomApi.Topics.search(handleResult()) {
        usePrincipalOrNull { uid ->
            it.pagination(IdentifiablePagingGenerator) { f ->
                backend.searchPublicTopics(it, f, uid)
            }
        }
    }

    CustomApi.Topics.recommend(handleResult()) {
        usePrincipalOrNull { uid ->
            it.pagination(IdentifiablePagingGenerator) { f ->
                if (uid == null && it.fillHasCommented == true) {
                    Result.failure(UnauthorizedException())
                } else {
                    backend.recommendTopics(uid, f)
                }
            }
        }
    }

    CustomApi.Topics.Id.get(handleResult()) { q, p ->
        usePrincipalOrNull { uid ->
            backend.getTopic(p.id, uid, q.fillHasCommented)
        }
    }

    CustomApi.Topics.Aid.get(handleResult()) {
        usePrincipalOrNull { uid ->
            backend.getTopicByAid(it.aid, uid, it.fillHasCommented)
        }
    }

    CustomApi.Topics.Id.Topics.get(handleResult()) { q, p ->
        usePrincipalOrNull { uid ->
            q.pagination(IdentifiablePagingGenerator) { f ->
                backend.getTopicsByParentId(p.id, ObjectType.TOPIC, uid, q.fillHasCommented, f, q.pinType)
            }
        }
    }
    CustomApi.Topics.Id.Reactions.get(handleResult()) { q, p ->
        usePrincipalOrNull { uid ->
            q.pagination(ReactionPaginationGenerator(backend)) { fetch ->
                backend.reactionList(p.id, uid, q.fillHasReacted, fetch)
            }
        }
    }
}

fun Route.bindProtectedTopicRoute(backend: Backend) {
    CustomApi.Topics.Id.createSnapshot(handleResult()) { p, api ->
        usePrincipal { uid ->
            backend.createTopicSnapshot(uid, p.id)
        }
    }

    CustomApi.Topics.add(handleResult()) { api ->
        usePrincipal { uid ->
            backend.createPlainTopic(uid, api.receiveBody())
        }
    }

    CustomApi.Topics.Id.Reactions.add(handleResult()) { p, api ->
        usePrincipal { uid ->
            val emoji = api.receiveBody().emoji
            addReaction(emoji, backend, uid, p)
        }
    }
    CustomApi.Topics.Id.Reactions.delete(handleResult()) { p, api ->
        usePrincipal { uid ->
            val deleteReaction = api.receiveBody()
            deleteReaction(deleteReaction, backend, uid, p)
        }
    }

    CustomApi.Topics.Id.pin(handleResult()) { p, api ->
        usePrincipal { uid ->
            backend.updateTopicPin(uid, p.id, true)
        }
    }

    CustomApi.Topics.Id.unpin(handleResult()) { p, api ->
        usePrincipal { uid ->
            backend.updateTopicPin(uid, p.id, false)
        }
    }
}
