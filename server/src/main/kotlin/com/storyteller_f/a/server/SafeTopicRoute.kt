package com.storyteller_f.a.server

import com.storyteller_f.Backend
import com.storyteller_f.a.server.auth.usePrincipal
import com.storyteller_f.a.server.auth.usePrincipalOrNull
import com.storyteller_f.a.server.common.pagination
import com.storyteller_f.a.server.service.*
import com.storyteller_f.shared.obj.NewReaction
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.tables.deleteReaction
import com.yy.mobile.emoji.EmojiReader
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.routing.Route

fun Route.bindSafeTopicRoute(backend: Backend) {
    get<RouteTopics.Search> {
        usePrincipalOrNull { id ->
            pagination(PrimaryKey::class, {
                it.id.toString()
            }) { _, n, s ->
                searchPublicTopics(n, s, it, backend, id)
            }
        }
    }

    get<RouteTopics.Recommend> {
        usePrincipalOrNull { uid ->
            pagination(PrimaryKey::class, {
                it.id.toString()
            }) { prePageToken, nextPageToken, size ->
                recommendTopics(backend, prePageToken, nextPageToken, size, uid, it.parent.fillHasCommented == true)
            }
        }
    }

    get<RouteTopics.Id> {
        usePrincipalOrNull { id ->
            getTopic(it.id, id, backend, it.parent.fillHasCommented)
        }
    }

    get<RouteTopics.Id.Topics> {
        usePrincipalOrNull { uid ->
            pagination(PrimaryKey::class, {
                it.id.toString()
            }) { p, n, s ->
                getTopics(it.parent.id, ObjectType.TOPIC, uid, backend, p, n, s, it.parent.parent.fillHasCommented)
            }
        }
    }
}

fun Route.bindProtectedSafeTopicRoute(backend: Backend) {
    get<RouteTopics.Id.Snapshot> {
        usePrincipal { id ->
            getTopicSnapshot(id, it.parent.id, backend)
        }
    }

    post<RouteTopics> {
        usePrincipal {
            addTopicAtCommunity(it, backend)
        }
    }

    post<RouteTopics.Id.Reactions> {
        usePrincipal { id ->
            val emoji = call.receive<NewReaction>().emoji
            if (EmojiReader.getTextLength(emoji) == 1 && EmojiReader.isEmojiOfCharIndex(emoji, 0)) {
                addReaction(id, it.parent.id, emoji)
            } else {
                Result.failure(BadRequestException("invalid emoji"))
            }
        }
    }

    post<RouteReactions.Delete> {
        usePrincipal { id ->
            val emoji = call.receive<NewReaction>().emoji
            if (EmojiReader.getTextLength(emoji) == 1 && EmojiReader.isEmojiOfCharIndex(emoji, 0)) {
                deleteReaction(id, emoji)
            } else {
                Result.failure(BadRequestException("invalid emoji"))
            }
        }
    }

    get<RouteTopics.Id.Reactions> {
        usePrincipalOrNull { id ->
            reactionList(it.parent.id, id, it.parent.parent.fillHasCommented)
        }
    }
}
