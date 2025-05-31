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
        index("reactions-g", true, objectId, objectType, emoji, id)
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

suspend fun Backend.commonReactions(
    uid: PrimaryKey?,
    objectId: List<PrimaryKey>
): Result<List<ReactionInfo>> {
    return databaseSession.dbSearch {
        val countExpression = Reactions.id.countDistinct()
        search {
            Reactions.select(Reactions.objectId, Reactions.emoji, countExpression).where {
                Reactions.objectId inList objectId
            }.groupBy(Reactions.objectId, Reactions.emoji, Reactions.id).orderBy(countExpression, SortOrder.DESC)
        }
        map {
            Pair(it[Reactions.objectId], it[Reactions.emoji]) to it[countExpression]
        }
    }.mapResult { list ->
        (if (uid == null) {
            Result.success(emptyMap())
        } else {
            hasReactedInReaction(list.map {
                it.first.first
            }.distinct(), uid).map {
                it.groupByPair().mapValues { v ->
                    v.value.toSet()
                }
            }
        }).map { reactedMap ->
            list.map {
                ReactionInfo(
                    it.first.second,
                    it.first.first,
                    it.second,
                    reactedMap[it.first.first]?.contains(it.first.second) == true
                )
            }
        }
    }
}

suspend fun Backend.getReaction(
    uid: PrimaryKey,
    objectId: PrimaryKey,
    emojiText: String
): Result<ReactionInfo?> {
    return databaseSession.dbSearch {
        search {
            Reactions.selectAll().where {
                Reactions.objectId eq objectId and (Reactions.emoji eq emojiText)
            }
        }
        first {
            Reaction.wrapRow(it)
        }
    }.mapResultIfNotNull {
        this.processReactionInfo(objectId, emojiText, uid)
    }
}

private suspend fun Backend.processReactionInfo(
    objectId: PrimaryKey,
    emojiText: String,
    uid: PrimaryKey
): Result<ReactionInfo> = merge({
    getReactionCountInReaction(listOf(objectId), emojiText).map {
        it.associateByPair()
    }
}, {
    hasReacted(objectId, uid, emojiText)
}).map { (reactionCountMap, hasReacted) ->
    ReactionInfo(
        emojiText,
        objectId,
        reactionCountMap[objectId] ?: 0,
        hasReacted,
    )
}

suspend fun Backend.getSingleReaction(
    uid: PrimaryKey,
    emoji: String,
    objectId: PrimaryKey
): Result<SingleReactionInfo?> {
    return databaseSession.dbSearch {
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

suspend fun Backend.deleteReaction(
    uid: PrimaryKey,
    emoji: String,
    objectId: PrimaryKey
): Result<Boolean> = getSingleReaction(uid, emoji, objectId).mapResult {
    if (it == null) {
        Result.success(true)
    } else {
        deleteReaction(it.id)
    }
}

suspend fun Backend.deleteReaction(
    reactionId: PrimaryKey
): Result<Boolean> {
    return databaseSession.dbQuery {
        Reactions.deleteWhere { builder ->
            with(builder) {
                this@deleteWhere.id eq reactionId
            }
        }
    }.map { value ->
        value > 0
    }
}

suspend fun Backend.insertReaction(
    newId: PrimaryKey,
    userId: PrimaryKey,
    reactionInfo: ReactionInfo,
    now: LocalDateTime
) = databaseSession.dbQuery {
    check(Reactions.insert { statement ->
        statement[id] = newId
        statement[uid] = userId
        statement[objectId] = reactionInfo.objectId
        statement[objectType] = ObjectType.TOPIC
        statement[emoji] = reactionInfo.emoji
        statement[createdTime] = now
    }.insertedCount > 0) {
        "insert reaction failed"
    }
}

suspend fun Backend.getReactionCount(objectId: List<PrimaryKey>) =
    databaseSession.dbSearch {
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

suspend fun Backend.getReactionCountInReaction(objectId: List<PrimaryKey>, emoji: String) =
    databaseSession.dbSearch {
        val column = Reactions.emoji.countDistinct()
        search {
            Reactions.select(Reactions.objectId, column).where {
                (Reactions.objectId inList objectId) and (Reactions.emoji eq emoji)
            }.groupBy(Reactions.objectId)
        }
        map {
            it[Reactions.objectId] to it[column]
        }
    }

suspend fun Backend.hasReacted(objectId: PrimaryKey, uid: PrimaryKey, emoji: String): Result<Boolean> {
    return databaseSession.dbSearch {
        search {
            Reactions.selectAll().where {
                (Reactions.objectId eq objectId) and (Reactions.emoji eq emoji) and (Reactions.uid eq uid)
            }
        }
        isNotEmpty()
    }
}

suspend fun Backend.hasReactedInReaction(
    objectId: List<PrimaryKey>,
    uid: PrimaryKey,
) =
    databaseSession.dbSearch {
        search {
            Reactions.select(Reactions.objectId, Reactions.emoji).where {
                (Reactions.objectId inList objectId) and (Reactions.uid eq uid)
            }.groupBy(Reactions.objectId, Reactions.emoji)
        }
        map {
            it[Reactions.objectId] to it[Reactions.emoji]
        }
    }
