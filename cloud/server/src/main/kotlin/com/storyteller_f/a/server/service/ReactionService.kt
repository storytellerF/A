package com.storyteller_f.a.server.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.ForbiddenException
import com.storyteller_f.a.backend.core.ReactionFetch
import com.storyteller_f.a.backend.core.UnauthorizedException
import com.storyteller_f.a.exposed.isDup
import com.storyteller_f.a.exposed.query.PaginationResult
import com.storyteller_f.backend.service.Backend
import com.storyteller_f.backend.service.tables.ReactionRecord
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.shared.utils.recoverResult
import io.github.aakira.napier.Napier

suspend fun Backend.addReaction(
    userId: PrimaryKey,
    topicId: PrimaryKey,
    emojiText: String
) = checkRootWritePermission(ObjectType.TOPIC, topicId, userId).mapResultIfNotNull {
    if (it.hasWrite) {
        val newId = SnowflakeFactory.nextId()
        exposedDatabase.topicDatabase.getReactionInfo(userId, topicId, emojiText).mapResult { oldReaction ->
            if (oldReaction != null && oldReaction.hasReacted) {
                Result.success(oldReaction)
            } else {
                val reactionRecord =
                    ReactionRecord(userId, topicId, ObjectType.TOPIC, emojiText, newId, now())
                exposedDatabase.topicDatabase.insertReaction(reactionRecord).map {
                    exposedDatabase.topicDatabase.statsReactionRecord(reactionRecord).onFailure { throwable ->
                        Napier.e(throwable = throwable) {
                            "addReaction"
                        }
                    }
                    ReactionInfo(
                        reactionRecord.emoji,
                        reactionRecord.objectId,
                        (oldReaction?.count ?: 0) + 1,
                        true,
                        reactionRecord.id
                    )
                }.recoverResult { throwable ->
                    if (throwable.isDup()) {
                        Result.success(
                            ReactionInfo(
                                reactionRecord.emoji,
                                reactionRecord.objectId,
                                (oldReaction?.count ?: 0) + 1,
                                true,
                                reactionRecord.id
                            )
                        )
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
    return exposedDatabase.topicDatabase.getReactionInfoPaginationResult(listOf(objectId), uid, reactionFetch)
}
