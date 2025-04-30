package com.storyteller_f.tables

import com.storyteller_f.*
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.model.SingleReactionInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.*

object Reactions : BaseTable() {
    val emoji = emoji()
    val uid = customPrimaryKey("uid")
    val objectId = customPrimaryKey("object_id").index()
    val objectType = objectType("object_type")

    init {
        index("reactions-main", true, objectId, emoji, uid)
    }
}

class Reaction(
    val uid: PrimaryKey,
    val objectId: PrimaryKey,
    val objectType: ObjectType,
    id: PrimaryKey,
    createdTime: LocalDateTime
) : BaseEntity(id, createdTime) {
    companion object {
        fun wrapRow(resultRow: ResultRow): Reaction {
            return Reaction(
                resultRow[Reactions.uid],
                resultRow[Reactions.objectId],
                resultRow[Reactions.objectType],
                resultRow[Reactions.id],
                resultRow[Reactions.createdTime]
            )
        }
    }
}

suspend fun commonReactions(
    backend: Backend,
    uid: PrimaryKey?,
    objectId: PrimaryKey
): Result<List<ReactionInfo>> {
    val (countExpression, resultRowTransform: (ResultRow) -> Triple<String, Long, Boolean>, query) = getReactionBuilder(
        uid
    )
    return DatabaseFactory.mapQuery(backend, {
        ReactionInfo(first, objectId, ObjectType.TOPIC, second, third)
    }, resultRowTransform) {
        query.andWhere {
            Reactions.objectId eq objectId
        }.groupBy(Reactions.emoji).orderBy(countExpression, SortOrder.DESC)
    }
}

private fun getReactionBuilder(
    uid: PrimaryKey?
): Triple<Count, (ResultRow) -> Triple<String, Long, Boolean>, Query> {
    val r2 = Reactions.alias("r2")
    val countExpression = Reactions.uid.countDistinct()
    val baseSelection = listOf(
        Reactions.emoji,
        countExpression,
    )
    val hasCommentExpression = r2[Reactions.uid].max()
    val resultRowTransform: (ResultRow) -> Triple<String, Long, Boolean> = {
        Triple(
            it[Reactions.emoji],
            it[countExpression],
            it.getOrNull(hasCommentExpression) != null
        )
    }
    val query = if (uid != null) {
        Reactions.join(r2, JoinType.LEFT, Reactions.objectId, r2[Reactions.objectId]) {
            r2[Reactions.emoji] eq Reactions.emoji and (r2[Reactions.uid] eq uid)
        }.select(baseSelection + hasCommentExpression)
    } else {
        Reactions.select(baseSelection)
    }
    return Triple(countExpression, resultRowTransform, query)
}

suspend fun getReaction(
    backend: Backend,
    uid: PrimaryKey,
    objectId: PrimaryKey,
    emojiText: String
): Result<ReactionInfo?> {
    val (_, resultRowTransform: (ResultRow) -> Triple<String, Long, Boolean>, query) = getReactionBuilder(
        uid
    )
    return DatabaseFactory.first(backend, {
        ReactionInfo(emojiText, objectId, ObjectType.TOPIC, second, third)
    }, resultRowTransform) {
        query.andWhere {
            Reactions.objectId eq objectId and (Reactions.emoji eq emojiText)
        }.groupBy(Reactions.emoji)
    }
}

suspend fun getSingleReaction(
    backend: Backend,
    uid: PrimaryKey,
    emoji: String,
    objectId: PrimaryKey
): Result<SingleReactionInfo?> {
    return DatabaseFactory.first(backend, {
        SingleReactionInfo(id, emoji, objectId, objectType, createdTime, uid)
    }, {
        Reaction.wrapRow(it)
    }) {
        Reactions.selectAll().where {
            (Reactions.objectId eq objectId) and (Reactions.emoji eq emoji) and (Reactions.uid eq uid)
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
