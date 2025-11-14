package com.storyteller_f.a.cloud.core.service

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.api.CommonPath
import com.storyteller_f.a.api.DeleteReaction
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.CustomBadRequestException
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.ReactionFetch
import com.storyteller_f.a.backend.core.UnauthorizedException
import com.storyteller_f.a.backend.core.types.ReactionRecord
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.UNIT_RESULT
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now
import com.storyteller_f.shared.utils.recoverIfDup
import com.storyteller_f.shared.utils.safeFirstEmoji
import io.github.aakira.napier.Napier

suspend fun Backend.addReaction(
    userId: PrimaryKey,
    topicId: PrimaryKey,
    emojiText: String
) = checkRootWritePermission(ObjectType.TOPIC, topicId, userId).mapResultIfNotNull {
    database.topic.getReactionInfo(userId, topicId, emojiText)
        .mapResult { oldReaction ->
            if (oldReaction != null && oldReaction.hasReacted) {
                Result.success(oldReaction)
            } else {
                val newId = SnowflakeFactory.nextId()
                val reactionRecord =
                    ReactionRecord(userId, topicId, ObjectType.TOPIC, emojiText, newId, now())
                val reactionInfo = ReactionInfo(
                    reactionRecord.emoji,
                    reactionRecord.objectId,
                    (oldReaction?.count ?: 0) + 1,
                    true,
                    reactionRecord.id
                )
                database.topic.insertReaction(reactionRecord).map {
                    database.topic.statsReactionRecord(
                        reactionRecord.objectId,
                        reactionRecord.emoji,
                        reactionRecord.objectType
                    ).onFailure { throwable ->
                        Napier.e(throwable = throwable) {
                            "addReaction"
                        }
                    }
                    reactionInfo
                }.recoverIfDup(database::isDup) {
                    Result.success(reactionInfo)
                }
            }
        }
}

suspend fun Backend.reactionList(
    objectId: PrimaryKey,
    uid: PrimaryKey?,
    fillHasReacted: Boolean?,
    reactionFetch: ReactionFetch,
): Result<PaginationResult<ReactionInfo>> {
    if (fillHasReacted == true && uid == null) return Result.failure(UnauthorizedException())
    return database.topic.getReactionInfoPaginationResult(
        listOf(objectId),
        uid,
        reactionFetch
    )
}

suspend fun addReaction(
    emoji: String,
    backend: Backend,
    uid: PrimaryKey,
    p: CommonPath
): Result<ReactionInfo?> = if (isEmoji(emoji)) {
    backend.addReaction(uid, p.id, emoji)
} else {
    Result.failure(CustomBadRequestException("invalid emoji"))
}

suspend fun deleteReaction(
    deleteReaction: DeleteReaction,
    backend: Backend,
    uid: PrimaryKey,
    p: CommonPath
): Result<ReactionInfo?> {
    val emoji = deleteReaction.emoji
    return if (isEmoji(emoji)) {
        backend.database.topic.deleteReaction(uid, emoji, p.id).mapResult {
            if (it) {
                backend.database.topic.statsReactionRecord(
                    p.id,
                    emoji,
                    ObjectType.TOPIC
                )
            } else {
                UNIT_RESULT
            }
        }
    } else {
        Result.failure(CustomBadRequestException("invalid emoji"))
    }.mapResult {
        backend.database.topic.getReactionInfo(uid, p.id, emoji)
            .map { reactionInfo ->
                reactionInfo ?: ReactionInfo(emoji, p.id, 0, false, 0)
            }
    }
}

private fun isEmoji(emoji: String): Boolean {
    return safeFirstEmoji(emoji)?.length == emoji.length
}
