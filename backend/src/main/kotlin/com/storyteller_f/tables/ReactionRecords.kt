package com.storyteller_f.tables

import com.storyteller_f.*
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.model.ReactionRecordInfo
import com.storyteller_f.shared.obj.ReactionCursorKey
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.groupByPair
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.types.Cursor
import com.storyteller_f.types.PaginationResult
import com.storyteller_f.types.ReactionFetch
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

object ReactionRecords : BaseTable() {
    val uid = customPrimaryKey("uid")
    val objectId = customPrimaryKey("object_id").index()
    val objectType = objectType("object_type")
    val emoji = emoji()

    init {
        index("reactions-main", true, objectId, emoji, uid)
    }
}

class ReactionRecord(
    val uid: PrimaryKey,
    val objectId: PrimaryKey,
    val objectType: ObjectType,
    val emoji: String,
    id: PrimaryKey,
    createdTime: LocalDateTime
) : BaseEntity(id, createdTime) {
    companion object {
        fun wrapRow(resultRow: ResultRow): ReactionRecord {
            return with(ReactionRecords) {
                ReactionRecord(
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

object Reactions : Table() {
    val objectId = customPrimaryKey("object_id")
    val objectType = objectType("object_type")
    val emoji = emoji()
    val count = long("count")
    val lastReactionId = customPrimaryKey("last_reaction_id")
    override val primaryKey: PrimaryKey = PrimaryKey(emoji)

    init {
        index("reaction-stats-main", false, objectId, count, lastReactionId)
        index("reaction-stats-unique", true, objectId, emoji)
    }
}

class Reaction(
    val objectId: PrimaryKey,
    val objectType: ObjectType,
    val emoji: String,
    val count: Long,
    val lastReactionId: PrimaryKey
) {
    companion object {
        fun wrapRow(resultRow: ResultRow): Reaction {
            return with(Reactions) {
                Reaction(
                    resultRow[objectId],
                    resultRow[objectType],
                    resultRow[emoji],
                    resultRow[count],
                    resultRow[lastReactionId]
                )
            }
        }
    }
}

suspend fun Backend.statsReactionRecord(reactionRecord: ReactionRecord): Result<Unit> {
    return getReactionCountForEmoji(
        listOf(reactionRecord.objectId),
        reactionRecord.emoji
    ).mapResult { reactionCountList ->
        if (reactionCountList.isEmpty()) {
            databaseSession.dbQuery {
                Reactions.deleteWhere {
                    Reactions.objectId eq reactionRecord.objectId and (Reactions.emoji eq reactionRecord.emoji)
                }
                Unit
            }
        } else {
            databaseSession.dbQuery {
                check(Reactions.upsert(Reactions.objectId, Reactions.emoji) {
                    it[objectId] = reactionRecord.objectId
                    it[emoji] = reactionRecord.emoji
                    it[count] = reactionCountList.first().second
                    it[objectType] = reactionRecord.objectType
                    it[lastReactionId] = reactionRecord.id
                }.insertedCount > 0) {
                    "insert reaction failed"
                }
            }
        }
    }
}

fun buildReactionInfoQuery(objectId: List<PrimaryKey>, reactionFetch: ReactionFetch): Query {
    val query = Reactions.selectAll().where {
        Reactions.objectId inList objectId
    }
    val cursor = reactionFetch.cursor
    when (cursor) {
        is Cursor.NextCursor<*> -> if (cursor.value is ReactionCursorKey) {
            query.andWhere {
                val value = cursor.value
                Reactions.count greaterEq value.count and (Reactions.lastReactionId less value.reactionId)
            }
        }

        is Cursor.PreCursor<*> -> if (cursor.value is ReactionCursorKey) {
            query.andWhere {
                val value = cursor.value
                Reactions.count lessEq value.count and (Reactions.lastReactionId greater value.reactionId)
            }
        }

        null -> {}
    }
    return query
}

suspend fun Backend.getReactionInfoPaginationResult(
    objectId: List<PrimaryKey>,
    uid: PrimaryKey?,
    reactionFetch: ReactionFetch
) =
    databaseSession.dbSearch {
        search {
            buildReactionInfoQuery(objectId, reactionFetch).limit(reactionFetch.size)
                .orderBy(Reactions.count to SortOrder.DESC, Reactions.lastReactionId to SortOrder.ASC)
        }
        map(Reaction::wrapRow)
    }.mapResult { list ->
        databaseSession.dbSearch {
            search {
                buildReactionInfoQuery(objectId, reactionFetch)
            }
            count()
        }.mapResult { count ->
            (if (uid == null) {
                Result.success(emptyMap())
            } else {
                val objectIdList = list.map {
                    it.objectId
                }.distinct()
                hasReactedEmoji(objectIdList, uid).map {
                    it.groupByPair().mapValues { v ->
                        v.value.toSet()
                    }
                }
            }).map { reactedMap ->
                PaginationResult(list.map {
                    ReactionInfo(
                        it.emoji,
                        it.objectId,
                        it.count,
                        reactedMap[it.objectId]?.contains(it.emoji) == true,
                        it.lastReactionId
                    )
                }, count)
            }
        }
    }

suspend fun Backend.getReactionInfo(
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
        hasReactedForEmoji(objectId, uid, emojiText).map { hasReacted ->
            ReactionInfo(it.emoji, it.objectId, it.count, hasReacted, it.lastReactionId)
        }
    }
}

suspend fun Backend.getReactionRecordInfo(
    uid: PrimaryKey,
    emoji: String,
    objectId: PrimaryKey
): Result<ReactionRecordInfo?> {
    return databaseSession.dbSearch {
        search {
            ReactionRecords.selectAll().where {
                (ReactionRecords.objectId eq objectId) and
                    (ReactionRecords.emoji eq emoji) and
                    (ReactionRecords.uid eq uid)
            }
        }
        first {
            val reactionRecord = ReactionRecord.wrapRow(it)
            ReactionRecordInfo(
                reactionRecord.id,
                emoji,
                reactionRecord.objectId,
                reactionRecord.objectType,
                reactionRecord.createdTime,
                uid
            )
        }
    }
}

suspend fun Backend.deleteReaction(
    uid: PrimaryKey,
    emoji: String,
    objectId: PrimaryKey
) = getReactionRecordInfo(uid, emoji, objectId).mapResult { recordInfo ->
    if (recordInfo == null) {
        Result.success(true)
    } else {
        deleteReaction(recordInfo.id).map {
            if (it) {
                statsReactionRecord(
                    ReactionRecord(
                        recordInfo.id,
                        recordInfo.objectId,
                        recordInfo.objectType,
                        recordInfo.emoji,
                        recordInfo.id,
                        recordInfo.createdTime
                    )
                )
            }
            it
        }
    }
}

suspend fun Backend.deleteReaction(
    reactionId: PrimaryKey
): Result<Boolean> {
    return databaseSession.dbQuery {
        ReactionRecords.deleteWhere { builder ->
            with(builder) {
                this@deleteWhere.id eq reactionId
            }
        }
    }.map { value ->
        value > 0
    }
}

suspend fun Backend.insertReaction(
    reactionRecord: ReactionRecord
) = databaseSession.dbQuery {
    check(ReactionRecords.insert { statement ->
        statement[id] = reactionRecord.id
        statement[uid] = reactionRecord.uid
        statement[objectId] = reactionRecord.objectId
        statement[objectType] = reactionRecord.objectType
        statement[emoji] = reactionRecord.emoji
        statement[createdTime] = reactionRecord.createdTime
    }.insertedCount > 0) {
        "insert reaction failed"
    }
}

suspend fun Backend.getReactionCount(objectIdList: List<PrimaryKey>): Result<List<Pair<Long, Long>>> {
    if (objectIdList.isEmpty()) return Result.success(emptyList())
    return databaseSession.dbSearch {
        search {
            Reactions.selectAll().where {
                Reactions.objectId inList objectIdList
            }
        }
        map {
            it[Reactions.objectId] to it[Reactions.count]
        }
    }
}

suspend fun Backend.getReactionCountForEmoji(objectId: List<PrimaryKey>, emoji: String) =
    databaseSession.dbSearch {
        val column = ReactionRecords.emoji.countDistinct()
        search {
            ReactionRecords.select(ReactionRecords.objectId, column).where {
                (ReactionRecords.objectId inList objectId) and (ReactionRecords.emoji eq emoji)
            }.groupBy(ReactionRecords.objectId)
        }
        map {
            it[ReactionRecords.objectId] to it[column]
        }
    }

suspend fun Backend.hasReactedForEmoji(objectId: PrimaryKey, uid: PrimaryKey, emoji: String): Result<Boolean> {
    return databaseSession.dbSearch {
        search {
            ReactionRecords.selectAll().where {
                (ReactionRecords.objectId eq objectId) and
                    (ReactionRecords.emoji eq emoji) and
                    (ReactionRecords.uid eq uid)
            }
        }
        isNotEmpty()
    }
}

suspend fun Backend.hasReactedEmoji(
    objectIdList: List<PrimaryKey>,
    uid: PrimaryKey,
): Result<List<Pair<Long, String>>> {
    if (objectIdList.isEmpty()) return Result.success(emptyList())
    return databaseSession.dbSearch {
        search {
            ReactionRecords.select(ReactionRecords.objectId, ReactionRecords.emoji).where {
                (ReactionRecords.objectId inList objectIdList) and (ReactionRecords.uid eq uid)
            }.groupBy(ReactionRecords.objectId, ReactionRecords.emoji)
        }
        map {
            it[ReactionRecords.objectId] to it[ReactionRecords.emoji]
        }
    }
}
