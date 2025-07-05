package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.exposed.BaseEntity
import com.storyteller_f.a.backend.exposed.BaseTable
import com.storyteller_f.a.backend.exposed.customPrimaryKey
import com.storyteller_f.a.backend.exposed.emoji
import com.storyteller_f.a.backend.exposed.objectType
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.*

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
