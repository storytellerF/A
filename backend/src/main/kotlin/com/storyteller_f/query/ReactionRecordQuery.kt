package com.storyteller_f.query

import com.storyteller_f.Backend
import com.storyteller_f.count
import com.storyteller_f.first
import com.storyteller_f.isNotEmpty
import com.storyteller_f.map
import com.storyteller_f.shared.model.ReactionInfo
import com.storyteller_f.shared.model.ReactionRecordInfo
import com.storyteller_f.shared.obj.ReactionCursorKey
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.groupByPair
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.tables.Reaction
import com.storyteller_f.tables.Reaction.Companion.wrapRow
import com.storyteller_f.tables.ReactionRecord
import com.storyteller_f.tables.ReactionRecords
import com.storyteller_f.tables.ReactionRecords.emoji
import com.storyteller_f.tables.ReactionRecords.objectId
import com.storyteller_f.tables.ReactionRecords.objectType
import com.storyteller_f.tables.ReactionRecords.uid
import com.storyteller_f.tables.Reactions
import com.storyteller_f.tables.Reactions.emoji
import com.storyteller_f.tables.Reactions.lastReactionId
import com.storyteller_f.tables.Reactions.objectId
import com.storyteller_f.tables.Reactions.objectType
import com.storyteller_f.types.Cursor
import com.storyteller_f.types.PaginationResult
import com.storyteller_f.types.ReactionFetch
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.countDistinct
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert

suspend fun Backend.statsReactionRecord(reactionRecord: ReactionRecord): Result<Unit> {
    return getReactionCountForEmoji(
        listOf(reactionRecord.objectId),
        reactionRecord.emoji
    ).mapResult { reactionCountList ->
        if (reactionCountList.isEmpty()) {
            exposedDatabaseSession.dbQuery {
                Reactions.deleteWhere {
                    Reactions.objectId eq reactionRecord.objectId and (Reactions.emoji eq reactionRecord.emoji)
                }
                Unit
            }
        } else {
            exposedDatabaseSession.dbQuery {
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

suspend fun Backend.getReactionInfoPaginationResult(
    objectId: List<PrimaryKey>,
    uid: PrimaryKey?,
    reactionFetch: ReactionFetch
) =
    exposedDatabaseSession.dbSearch {
        search {
            buildReactionInfoQuery(objectId, reactionFetch).limit(reactionFetch.size)
                .orderBy(Reactions.count to SortOrder.DESC, Reactions.lastReactionId to SortOrder.ASC)
        }
        map(Reaction::wrapRow)
    }.mapResult { list ->
        exposedDatabaseSession.dbSearch {
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
    return exposedDatabaseSession.dbSearch {
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
    return exposedDatabaseSession.dbSearch {
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
    return exposedDatabaseSession.dbQuery {
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
) = exposedDatabaseSession.dbQuery {
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
    return exposedDatabaseSession.dbSearch {
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
    exposedDatabaseSession.dbSearch {
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
    return exposedDatabaseSession.dbSearch {
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
    return exposedDatabaseSession.dbSearch {
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
