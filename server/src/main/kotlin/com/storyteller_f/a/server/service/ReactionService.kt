package com.storyteller_f.a.server.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.*
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.obj.Pagination
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultNotNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.shared.utils.recoverError
import com.storyteller_f.tables.commonReactions
import com.storyteller_f.tables.getReaction
import com.storyteller_f.tables.getSimpleTopic
import com.storyteller_f.tables.insertReaction

suspend fun addReaction(
    backend: Backend,
    userId: PrimaryKey,
    topicId: PrimaryKey,
    emojiText: String
): Result<ReactionInfo?> {
    return checkRootWritePermission(backend, ObjectType.TOPIC, topicId, userId).mapResultNotNull {
        if (it.hasWrite) {
            DatabaseFactory.getSimpleTopic(backend, topicId).mapResult { topic ->
                val newId = SnowflakeFactory.nextId()
                getReaction(backend, userId, topicId, emojiText).mapResult { oldReaction ->
                    if (oldReaction != null && oldReaction.hasReacted) {
                        Result.success(oldReaction)
                    } else {
                        val now = now()
                        val reactionInfo =
                            ReactionInfo(emojiText, topicId, ObjectType.TOPIC, (oldReaction?.count ?: 0) + 1, true)
                        DatabaseFactory.insertReaction(backend, newId, userId, reactionInfo, now).map { i ->
                            reactionInfo
                        }.recoverError { throwable ->
                            if (throwable.isDup()) {
                                getReaction(backend, userId, topicId, emojiText)
                            } else {
                                Result.failure(throwable)
                            }
                        }
                    }
                }
            }
        } else {
            Result.failure(ForbiddenException("permission denied"))
        }
    }
}

suspend fun reactionList(
    backend: Backend,
    objectId: PrimaryKey,
    uid: PrimaryKey?,
    fillHasReacted: Boolean?
): Result<ServerResponse<ReactionInfo>> {
    if (fillHasReacted == true && uid == null) return Result.failure(UnauthorizedException())
    return DatabaseFactory.getSimpleTopic(backend, objectId).mapResult {
        commonReactions(backend, uid, objectId).map { infos ->
            ServerResponse(infos, Pagination(null, null, infos.size.toLong()))
        }
    }
}
