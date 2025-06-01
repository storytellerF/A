package com.storyteller_f.a.server.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.Backend
import com.storyteller_f.ForbiddenException
import com.storyteller_f.isDup
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.UnauthorizedException
import com.storyteller_f.shared.utils.mapIfNotNull
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.shared.utils.recoverResult
import com.storyteller_f.tables.ReactionRecord
import com.storyteller_f.tables.getReactionInfo
import com.storyteller_f.tables.getReactionInfoPaginationResult
import com.storyteller_f.tables.insertReaction
import com.storyteller_f.types.PaginationResult
import com.storyteller_f.types.ReactionFetch

suspend fun Backend.addReaction(
    userId: PrimaryKey,
    topicId: PrimaryKey,
    emojiText: String
) = checkRootWritePermission(ObjectType.TOPIC, topicId, userId).mapResultIfNotNull {
    if (it.hasWrite) {
        val newId = SnowflakeFactory.nextId()
        getReactionInfo(userId, topicId, emojiText).mapResult { oldReaction ->
            if (oldReaction != null && oldReaction.hasReacted) {
                Result.success(oldReaction to null)
            } else {
                val reactionRecord =
                    ReactionRecord(userId, topicId, ObjectType.TOPIC, emojiText, newId, now())
                insertReaction(reactionRecord).mapResult {
                    getReactionInfo(userId, topicId, emojiText).mapIfNotNull {
                        it to reactionRecord
                    }
                }.recoverResult { throwable ->
                    if (throwable.isDup()) {
                        getReactionInfo(userId, topicId, emojiText).mapIfNotNull {
                            it to null
                        }
                    } else {
                        Result.failure(throwable)
                    }
                }
            }
        }
    } else {
        Result.failure(ForbiddenException("permission denied"))
    }
}

suspend fun Backend.reactionList(
    objectId: PrimaryKey,
    uid: PrimaryKey?,
    fillHasReacted: Boolean?,
    reactionFetch: ReactionFetch,
): Result<PaginationResult<ReactionInfo>> {
    if (fillHasReacted == true && uid == null) return Result.failure(UnauthorizedException())
    return getReactionInfoPaginationResult(listOf(objectId), uid, reactionFetch)
}
