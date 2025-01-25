package com.storyteller_f.a.server.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.UnauthorizedException
import com.storyteller_f.isDup
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.obj.Pagination
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.now
import com.storyteller_f.shared.utils.recoverError
import com.storyteller_f.tables.*

suspend fun addReaction(
    userId: PrimaryKey,
    topicId: PrimaryKey,
    emojiText: String
): Result<ReactionInfo?> {
    return DatabaseFactory.getSimpleTopic(topicId).mapResult { topic ->
        val newId = SnowflakeFactory.nextId()
        getReaction(userId, topicId, emojiText).mapResult { oldReaction ->
            if (oldReaction != null && oldReaction.hasReacted) {
                Result.success(oldReaction)
            } else {
                val now = now()
                val reactionInfo =
                    ReactionInfo(emojiText, topicId, ObjectType.TOPIC, (oldReaction?.count ?: 0) + 1, true)
                DatabaseFactory.insertReaction(newId, userId, reactionInfo, now).map { i ->
                    if (i > 0) {
                        reactionInfo
                    } else {
                        null
                    }
                }.recoverError { throwable ->
                    if (throwable.isDup()) {
                        getReaction(userId, topicId, emojiText)
                    } else {
                        Result.failure(throwable)
                    }
                }
            }
        }
    }
}

suspend fun reactionList(
    objectId: PrimaryKey,
    uid: PrimaryKey?,
    fillHasReacted: Boolean?
): Result<ServerResponse<ReactionInfo>> {
    if (fillHasReacted == true && uid == null) return Result.failure(UnauthorizedException())
    return DatabaseFactory.getSimpleTopic(objectId).mapResult {
        commonReactions(uid, objectId).map { infos ->
            ServerResponse(infos, Pagination(null, null, infos.size.toLong()))
        }
    }
}
