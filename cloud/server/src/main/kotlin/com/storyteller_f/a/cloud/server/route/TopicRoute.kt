package com.storyteller_f.a.cloud.server.route

import com.storyteller_f.a.api.CustomApi
import com.storyteller_f.a.api.NewFavorite
import com.storyteller_f.a.api.NewSubscription
import com.storyteller_f.a.api.ReactionInfoListResponse
import com.storyteller_f.a.api.TopicInfoListResponse
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.cloud.core.service.addFavorite
import com.storyteller_f.a.cloud.core.service.addReaction
import com.storyteller_f.a.cloud.core.service.addSubscription
import com.storyteller_f.a.cloud.core.service.createPlainTopic
import com.storyteller_f.a.cloud.core.service.createTopicSnapshot
import com.storyteller_f.a.cloud.core.service.deleteFavoriteByObject
import com.storyteller_f.a.cloud.core.service.deleteReaction
import com.storyteller_f.a.cloud.core.service.getTopic
import com.storyteller_f.a.cloud.core.service.getTopicByAid
import com.storyteller_f.a.cloud.core.service.getTopicsByParentId
import com.storyteller_f.a.cloud.core.service.reactionList
import com.storyteller_f.a.cloud.core.service.recommendTopics
import com.storyteller_f.a.cloud.core.service.removeSubscriptionByObject
import com.storyteller_f.a.cloud.core.service.searchCommunityTopics
import com.storyteller_f.a.cloud.core.service.searchRoomTopics
import com.storyteller_f.a.cloud.core.service.searchUserTopics
import com.storyteller_f.a.cloud.core.service.updateTopicPin
import com.storyteller_f.a.cloud.server.auth.handleResult
import com.storyteller_f.a.cloud.server.auth.usePrincipal
import com.storyteller_f.a.cloud.server.auth.usePrincipalOrNull
import com.storyteller_f.a.cloud.server.common.GeneralOffsetPagingGenerator
import com.storyteller_f.a.cloud.server.common.IdentifiablePagingGenerator
import com.storyteller_f.a.cloud.server.common.ReactionPaginationGenerator
import com.storyteller_f.a.cloud.server.common.pagination
import com.storyteller_f.endpoint4k.ktor.server.invoke
import com.storyteller_f.endpoint4k.ktor.server.receiveBody
import com.storyteller_f.shared.type.ObjectType
import io.ktor.server.routing.Route

fun Route.bindTopicRoute(backend: Backend) {
    CustomApi.Topics.recommend(handleResult()) {
        usePrincipalOrNull { uid ->
            it.pagination(GeneralOffsetPagingGenerator, { l, p ->
                TopicInfoListResponse(l, p)
            }) { f ->
                backend.recommendTopics(uid, f, it)
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

    bindSearchTopicRoute(backend)
}

private fun Route.bindSearchTopicRoute(backend: Backend) {
    CustomApi.Topics.Id.Topics.get(handleResult()) { q, p ->
        usePrincipalOrNull { uid ->
            q.pagination(IdentifiablePagingGenerator, { l, p ->
                TopicInfoListResponse(l, p)
            }) { f ->
                backend.getTopicsByParentId(
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

    // 用户主题搜索路由
    CustomApi.Topics.Users.Id.search(handleResult()) { q, p ->
        usePrincipalOrNull { uid ->
            q.pagination(GeneralOffsetPagingGenerator, { l, p ->
                TopicInfoListResponse(l, p)
            }) { f ->
                backend.searchUserTopics(p.id, q, f, uid)
            }
        }
    }

    // 房间主题搜索路由
    CustomApi.Topics.Rooms.Id.search(handleResult()) { q, p ->
        usePrincipalOrNull { uid ->
            q.pagination(GeneralOffsetPagingGenerator, { l, p ->
                TopicInfoListResponse(l, p)
            }) { f ->
                backend.searchRoomTopics(p.id, q, f, uid)
            }
        }
    }

    // 社区主题搜索路由
    CustomApi.Topics.Communities.Id.search(handleResult()) { q, p ->
        usePrincipalOrNull { uid ->
            q.pagination(GeneralOffsetPagingGenerator, { l, p ->
                TopicInfoListResponse(l, p)
            }) { f ->
                backend.searchCommunityTopics(p.id, q, f, uid)
            }
        }
    }

    CustomApi.Topics.Id.Reactions.get(handleResult()) { q, p ->
        usePrincipalOrNull { uid ->
            q.pagination(ReactionPaginationGenerator(backend), { l, p ->
                ReactionInfoListResponse(l, p)
            }) { fetch ->
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
            val deleteReactionBody = api.receiveBody()
            deleteReaction(deleteReactionBody, backend, uid, p)
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

    CustomApi.Topics.Id.Favorite.add(handleResult()) { p, _ ->
        usePrincipal { uid ->
            backend.addFavorite(uid, NewFavorite(ObjectType.TOPIC, p.id)).map { }
        }
    }
    CustomApi.Topics.Id.Favorite.delete(handleResult()) { p, _ ->
        usePrincipal { uid ->
            backend.deleteFavoriteByObject(uid, p.id).map { }
        }
    }
    CustomApi.Topics.Id.Subscription.add(handleResult()) { p, _ ->
        usePrincipal { uid ->
            backend.addSubscription(uid, NewSubscription(p.id, ObjectType.TOPIC)).map { }
        }
    }
    CustomApi.Topics.Id.Subscription.delete(handleResult()) { p, _ ->
        usePrincipal { uid ->
            backend.removeSubscriptionByObject(uid, p.id).map { }
        }
    }
}
