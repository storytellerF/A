package com.storyteller_f.tables

import com.storyteller_f.BaseObj
import com.storyteller_f.BaseTable
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.customPrimaryKey
import com.storyteller_f.objectType
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.model.SingleReactionInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.Tuple4
import com.storyteller_f.shared.utils.mapResultNotNull
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.LongColumnType
import org.jetbrains.exposed.sql.Max
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.longLiteral
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll

object Reactions : BaseTable() {
    val emoji = varchar("emoji", 20)
    val uid = customPrimaryKey("uid")
    val objectId = customPrimaryKey("object_id")
    val objectType = objectType("object_type")

    init {
        index("reaction-main", true, objectId, uid, emoji)
    }
}

class Reaction(
    val emoji: String,
    val uid: PrimaryKey,
    val objectId: PrimaryKey,
    val objectType: ObjectType,
    id: PrimaryKey,
    createdTime: LocalDateTime
) : BaseObj(id, createdTime) {
    companion object {
        fun wrapRow(resultRow: ResultRow): Reaction {
            return Reaction(
                resultRow[Reactions.emoji],
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
    uid: PrimaryKey?,
    objectId: PrimaryKey
): Result<List<ReactionInfo>> {
    val latestTimeExpression = Reactions.createdTime.max<LocalDateTime, LocalDateTime>()
    val countExpression = Reactions.uid.count()
    val baseSelection = listOf(
        Reactions.emoji,
        countExpression,
        latestTimeExpression
    )
    val selection = reactionAuthorContains(uid)

    return DatabaseFactory.mapQuery({
        ReactionInfo(data1, objectId, ObjectType.TOPIC, data3, data2, data4 == 1L)
    }, {
        Tuple4(
            it[Reactions.emoji], it[countExpression], it[latestTimeExpression], when {
                selection != null -> it[selection]
                else -> 0
            }
        )
    }) {
        Reactions.select(
            when {
                selection != null -> baseSelection + selection
                else -> baseSelection
            }
        ).where {
            Reactions.objectId eq objectId
        }.groupBy(Reactions.emoji).orderBy(latestTimeExpression, SortOrder.DESC)
    }
}

private fun reactionAuthorContains(uid: PrimaryKey?): Max<Long, Long>? {
    return when {
        uid != null -> reactionAuthorContainsIfUidNotNull(uid)
        else -> null
    }
}

private fun reactionAuthorContainsIfUidNotNull(uid: PrimaryKey): Max<Long, Long> = Expression.build {
    val expr = case().When<Long>(Reactions.uid.eq(uid), longLiteral(1)).Else(longLiteral(0))
    Max(expr, LongColumnType())
}

suspend fun getReaction(uid: PrimaryKey, objectId: PrimaryKey, emojiText: String): Result<ReactionInfo?> {
    val containsExpression = reactionAuthorContainsIfUidNotNull(uid)
    val countExpression = Reactions.id.count()
    return DatabaseFactory.first({
        ReactionInfo(emojiText, objectId, ObjectType.TOPIC, third, first, second == 1L)
    }, {
        Triple(it[countExpression], it[containsExpression], it[Reactions.createdTime])
    }) {
        Reactions.select(countExpression, containsExpression).where {
            (Reactions.objectId eq objectId) and (Reactions.emoji eq emojiText)
        }.groupBy(Reactions.emoji)
    }
}

suspend fun getSingleReaction(uid: PrimaryKey, emoji: String): Result<SingleReactionInfo?> {
    return DatabaseFactory.first({
        SingleReactionInfo(id, emoji, objectId, objectType, createdTime, uid)
    }, {
        Reaction.wrapRow(it)
    }) {
        Reactions.selectAll().where {
            (Reactions.emoji eq emoji)
        }.groupBy(Reactions.emoji)
    }
}


suspend fun deleteReaction(
    id: PrimaryKey,
    emoji: String
): Result<Boolean?> = getSingleReaction(id, emoji).mapResultNotNull {
    deleteReaction(it.id)
}


suspend fun deleteReaction(reactionId: PrimaryKey): Result<Boolean> {
    return DatabaseFactory.dbQuery {
        Reactions.deleteWhere { builder ->
            with(builder) {
                id eq reactionId
            }
        }
    }.map { value ->
        if (value > 0) {
            true
        } else {
            false
        }
    }
}
