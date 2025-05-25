package com.storyteller_f.tables

import com.storyteller_f.*
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.model.SingleReactionInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.associateByPair
import com.storyteller_f.shared.utils.groupByPair
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.merge
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.*

object Reactions : BaseTable() {
    val emoji = emoji()
    val uid = customPrimaryKey("uid")
    val objectId = customPrimaryKey("object_id").index()
    val objectType = objectType("object_type")

    init {
        index("reactions-main", true, objectId, emoji, uid)
        index("reactions-uid", true, uid, objectId, emoji)
    }
}

class Reaction(
    val uid: PrimaryKey,
    val objectId: PrimaryKey,
    val objectType: ObjectType,
    val emoji: String,
    id: PrimaryKey,
    createdTime: LocalDateTime
) : BaseEntity(id, createdTime) {
    companion object {
        fun wrapRow(resultRow: ResultRow): Reaction {
            return with(Reactions) {
                Reaction(
                    resultRow[uid],
                    resultRow[objectId],
                    resultRow[objectType],
                    resultRow[emoji],
                    resultRow[id],
                    resultRow[createdTime]
                )
            }
        }
    }
}

suspend fun commonReactions(
    backend: Backend,
    uid: PrimaryKey?,
    objectId: List<PrimaryKey>
): Result<List<ReactionInfo>> {
    return DatabaseFactory.dbSearch(backend) {
        val countExpression = Reactions.uid.countDistinct()
        search {
            Reactions.select(Reactions.fields + countExpression).where {
                Reactions.objectId inList objectId
            }.groupBy(Reactions.objectId, Reactions.emoji).orderBy(countExpression, SortOrder.DESC)
        }
        map {
            Reaction.wrapRow(it) to it[countExpression]
        }
    }.mapResult { list ->
        (if (uid == null) {
            Result.success(emptyMap())
        } else {
            DatabaseFactory.hasReactedInReaction(backend, list.map {
                it.first.objectId
            }.distinct(), uid).map {
                it.groupByPair().mapValues { v ->
                    v.value.toSet()
                }
            }
        }).map { reactedMap ->
            list.map {
                ReactionInfo(
                    it.first.emoji,
                    it.first.objectId,
                    it.first.objectType,
                    it.second,
                    reactedMap[it.first.objectId]?.contains(it.first.emoji) == true
                )
            }
        }
    }
}

suspend fun getReaction(
    backend: Backend,
    uid: PrimaryKey,
    objectId: PrimaryKey,
    emojiText: String
): Result<ReactionInfo?> {
    return DatabaseFactory.dbSearch(backend) {
        search {
            Reactions.selectAll().where {
                Reactions.objectId eq objectId and (Reactions.emoji eq emojiText)
            }
        }
        first {
            Reaction.wrapRow(it)
        }
    }.mapResultIfNotNull {
        processReactionInfo(backend, objectId, emojiText, uid)
    }
}

private suspend fun processReactionInfo(
    backend: Backend,
    objectId: PrimaryKey,
    emojiText: String,
    uid: PrimaryKey
): Result<ReactionInfo> = merge({
    DatabaseFactory.getReactionCountInReaction(backend, listOf(objectId), emojiText).map {
        it.associateByPair()
    }
}, {
    DatabaseFactory.hasReacted(backend, (objectId), uid, emojiText)
}).map { (reactionCountMap, hasReacted) ->
    ReactionInfo(
        emojiText,
        objectId,
        ObjectType.TOPIC,
        reactionCountMap[objectId] ?: 0,
        hasReacted,
    )
}

suspend fun getSingleReaction(
    backend: Backend,
    uid: PrimaryKey,
    emoji: String,
    objectId: PrimaryKey
): Result<SingleReactionInfo?> {
    return DatabaseFactory.dbSearch(backend) {
        search {
            Reactions.selectAll().where {
                (Reactions.objectId eq objectId) and (Reactions.emoji eq emoji) and (Reactions.uid eq uid)
            }
        }
        first {
            val reaction = Reaction.wrapRow(it)
            SingleReactionInfo(
                reaction.id,
                emoji,
                reaction.objectId,
                reaction.objectType,
                reaction.createdTime,
                uid
            )
        }
    }
}

suspend fun deleteReaction(
    backend: Backend,
    uid: PrimaryKey,
    emoji: String,
    objectId: PrimaryKey
): Result<Boolean> = getSingleReaction(backend, uid, emoji, objectId).mapResult {
    if (it == null) {
        Result.success(true)
    } else {
        DatabaseFactory.deleteReaction(backend, it.id)
    }
}

suspend fun DatabaseFactory.deleteReaction(
    backend: Backend,
    reactionId: PrimaryKey
): Result<Boolean> {
    return dbQuery(backend) {
        Reactions.deleteWhere { builder ->
            with(builder) {
                id eq reactionId
            }
        }
    }.map { value ->
        value > 0
    }
}

suspend fun DatabaseFactory.insertReaction(
    backend: Backend,
    newId: PrimaryKey,
    userId: PrimaryKey,
    reactionInfo: ReactionInfo,
    now: LocalDateTime
) = dbQuery(backend) {
    check(Reactions.insert { statement ->
        statement[id] = newId
        statement[uid] = userId
        statement[objectId] = reactionInfo.objectId
        statement[objectType] = reactionInfo.objectType
        statement[emoji] = reactionInfo.emoji
        statement[createdTime] = now
    }.insertedCount > 0) {
        "insert reaction failed"
    }
}

suspend fun DatabaseFactory.getReactionCount(backend: Backend, objectId: List<PrimaryKey>) =
    dbSearch(backend) {
        val column = Reactions.id.countDistinct()
        search {
            Reactions.select(column).where {
                (Reactions.objectId inList objectId)
            }.groupBy(Reactions.objectId)
        }
        map {
            it[Reactions.objectId] to it[column]
        }
    }

suspend fun DatabaseFactory.getReactionCountInReaction(backend: Backend, objectId: List<PrimaryKey>, emoji: String) =
    dbSearch(backend) {
        val column = Reactions.emoji.countDistinct()
        search {
            Reactions.select(column).where {
                (Reactions.objectId inList objectId) and (Reactions.emoji eq emoji)
            }.groupBy(Reactions.objectId)
        }
        map {
            it[Reactions.objectId] to it[column]
        }
    }

suspend fun DatabaseFactory.hasReacted(backend: Backend, objectId: PrimaryKey, uid: PrimaryKey, emoji: String) =
    isNotEmpty(backend) {
        Reactions.selectAll().where {
            (Reactions.uid eq uid) and (Reactions.objectId eq objectId) and (Reactions.emoji eq emoji)
        }
    }

suspend fun DatabaseFactory.hasReactedInReaction(
    backend: Backend,
    objectId: List<PrimaryKey>,
    uid: PrimaryKey,
) =
    dbSearch(backend) {
        search {
            Reactions.select(Reactions.objectId, Reactions.emoji).where {
                (Reactions.uid eq uid) and (Reactions.objectId inList objectId)
            }.groupBy(Reactions.objectId, Reactions.emoji)
        }
        map {
            it[Reactions.objectId] to it[Reactions.emoji]
        }
    }
