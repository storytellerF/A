package com.storyteller_f.backend.service.query

import com.storyteller_f.backend.service.ExposedDatabaseSession
import com.storyteller_f.backend.service.count
import com.storyteller_f.backend.service.first
import com.storyteller_f.backend.service.isNotEmpty
import com.storyteller_f.backend.service.map
import com.storyteller_f.backend.service.tables.Reaction
import com.storyteller_f.backend.service.tables.ReactionRecord
import com.storyteller_f.backend.service.tables.ReactionRecords
import com.storyteller_f.backend.service.tables.Reactions
import com.storyteller_f.backend.service.types.Cursor
import com.storyteller_f.backend.service.types.PaginationResult
import com.storyteller_f.backend.service.types.ReactionFetch
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.model.ReactionRecordInfo
import com.storyteller_f.shared.obj.ReactionCursorKey
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.groupByPair
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

suspend fun ExposedDatabaseSession.statsReactionRecord(
    reactionRecord: ReactionRecord
) = getReactionCountForEmoji(
    listOf(reactionRecord.objectId),
    reactionRecord.emoji
).mapResult { reactionCountList ->
    if (reactionCountList.isEmpty()) {
        dbQuery {
            Reactions.deleteWhere {
                Reactions.objectId eq reactionRecord.objectId and (Reactions.emoji eq reactionRecord.emoji)
            }
            Unit
        }
    } else {
        dbQuery {
            check(Reactions.upsert(Reactions.objectId, Reactions.emoji) {
                it[Reactions.objectId] = reactionRecord.objectId
                it[Reactions.emoji] = reactionRecord.emoji
                it[Reactions.count] = reactionCountList.size.toLong()
                it[Reactions.objectType] = reactionRecord.objectType
                it[Reactions.lastReactionId] = reactionRecord.id
            }.insertedCount > 0) {
                "insert reaction failed"
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
        is Cursor.NextCursor<ReactionCursorKey> -> query.andWhere {
            val value = cursor.value
            Reactions.count greaterEq value.count and (Reactions.lastReactionId less value.reactionId)
        }

        is Cursor.PreCursor<ReactionCursorKey> -> query.andWhere {
            val value = cursor.value
            Reactions.count lessEq value.count and (Reactions.lastReactionId greater value.reactionId)
        }

        null -> {}
    }
    return query
}

suspend fun ExposedDatabaseSession.getReactionInfoPaginationResult(
    objectId: List<PrimaryKey>,
    uid: PrimaryKey?,
    reactionFetch: ReactionFetch
) =
    dbSearch {
        search {
            buildReactionInfoQuery(objectId, reactionFetch).limit(reactionFetch.size)
                .orderBy(Reactions.count to SortOrder.DESC, Reactions.lastReactionId to SortOrder.ASC)
        }
        map(Reaction.Companion::wrapRow)
    }.mapResult { list ->
        dbSearch {
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

suspend fun ExposedDatabaseSession.getReactionInfo(
    uid: PrimaryKey,
    objectId: PrimaryKey,
    emojiText: String
): Result<ReactionInfo?> {
    return dbSearch {
        search {
            Reactions.selectAll().where {
                Reactions.objectId eq objectId and (Reactions.emoji eq emojiText)
            }
        }
        first {
            Reaction.Companion.wrapRow(it)
        }
    }.mapResultIfNotNull {
        hasReactedForEmoji(objectId, uid, emojiText).map { hasReacted ->
            ReactionInfo(it.emoji, it.objectId, it.count, hasReacted, it.lastReactionId)
        }
    }
}

suspend fun ExposedDatabaseSession.getReactionRecordInfo(
    uid: PrimaryKey,
    emoji: String,
    objectId: PrimaryKey
): Result<ReactionRecordInfo?> {
    return dbSearch {
        search {
            ReactionRecords.selectAll().where {
                (ReactionRecords.objectId eq objectId) and
                    (ReactionRecords.emoji eq emoji) and
                    (ReactionRecords.uid eq uid)
            }
        }
        first {
            val reactionRecord = ReactionRecord.Companion.wrapRow(it)
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

suspend fun ExposedDatabaseSession.deleteReaction(
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

suspend fun ExposedDatabaseSession.deleteReaction(
    reactionId: PrimaryKey
): Result<Boolean> {
    return dbQuery {
        ReactionRecords.deleteWhere { builder ->
            with(builder) {
                ReactionRecords.id eq reactionId
            }
        }
    }.map { value ->
        value > 0
    }
}

suspend fun ExposedDatabaseSession.insertReaction(
    reactionRecord: ReactionRecord
) = dbQuery {
    check(ReactionRecords.insert { statement ->
        statement[ReactionRecords.id] = reactionRecord.id
        statement[ReactionRecords.uid] = reactionRecord.uid
        statement[ReactionRecords.objectId] = reactionRecord.objectId
        statement[ReactionRecords.objectType] = reactionRecord.objectType
        statement[ReactionRecords.emoji] = reactionRecord.emoji
        statement[ReactionRecords.createdTime] = reactionRecord.createdTime
    }.insertedCount > 0) {
        "insert reaction failed"
    }
}

suspend fun ExposedDatabaseSession.getReactionCount(objectIdList: List<PrimaryKey>): Result<List<Pair<Long, Long>>> {
    if (objectIdList.isEmpty()) return Result.success(emptyList())
    return dbSearch {
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

suspend fun ExposedDatabaseSession.getReactionCountForEmoji(
    objectId: List<PrimaryKey>,
    emoji: String
) = dbSearch {
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

suspend fun ExposedDatabaseSession.hasReactedForEmoji(
    objectId: PrimaryKey,
    uid: PrimaryKey,
    emoji: String
): Result<Boolean> {
    return dbSearch {
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

suspend fun ExposedDatabaseSession.hasReactedEmoji(
    objectIdList: List<PrimaryKey>,
    uid: PrimaryKey,
): Result<List<Pair<Long, String>>> {
    if (objectIdList.isEmpty()) return Result.success(emptyList())
    return dbSearch {
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
