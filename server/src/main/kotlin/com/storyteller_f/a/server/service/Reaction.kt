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
import com.storyteller_f.tables.Reactions
import com.storyteller_f.tables.commonReactions
import com.storyteller_f.tables.getReaction
import com.storyteller_f.tables.getSimpleTopic
import org.jetbrains.exposed.sql.insert


suspend fun addReaction(
    userId: PrimaryKey,
    topicId: PrimaryKey,
    emojiText: String
): Result<ReactionInfo?> {
    return getSimpleTopic(topicId).mapResult { topic ->
        val newId = SnowflakeFactory.nextId()
        getReaction(userId, topicId, emojiText).mapResult { oldReaction ->
            if (oldReaction != null && oldReaction.hasReacted) {
                Result.success(oldReaction)
            } else {
                val now = now()
                val reactionInfo =
                    ReactionInfo(emojiText, topicId, ObjectType.TOPIC, now, (oldReaction?.count ?: 0) + 1, true)
                DatabaseFactory.insert {
                    Reactions.insert { statement ->
                        statement[id] = newId
                        statement[uid] = userId
                        statement[objectId] = reactionInfo.objectId
                        statement[objectType] = reactionInfo.objectType
                        statement[emoji] = reactionInfo.emoji
                        statement[createdTime] = now
                    }
                }.map { i ->
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
    return getSimpleTopic(objectId).mapResult { topicInfo ->
        commonReactions(uid, objectId).map { infos ->
            ServerResponse(infos, Pagination(null, null, infos.size.toLong()))
        }
    }
}

