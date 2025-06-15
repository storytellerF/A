package com.storyteller_f.backend.service.tables

import com.storyteller_f.a.exposed.customPrimaryKey
import com.storyteller_f.a.exposed.objectType
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object MemberJoins : Table() {
    val uid = customPrimaryKey("uid").index()
    val objectId = customPrimaryKey("object_id").index()
    val objectType = objectType("object_type")
    val joinedTime = datetime("joined_time")

    init {
        index("member-joins-main", true, objectId, uid)
        index("member-joins-uid", true, uid, objectId)
    }
}

class MemberJoin(
    val uid: PrimaryKey,
    val objectId: PrimaryKey,
    val objectType: ObjectType,
    val joinedTime: LocalDateTime
) {
    companion object {
        fun wrapRow(row: ResultRow): MemberJoin {
            return with(MemberJoins) {
                MemberJoin(
                    row[uid],
                    row[objectId],
                    row[objectType],
                    row[joinedTime]
                )
            }
        }

        fun addRoomJoinRaw(
            room: PrimaryKey,
            userId: PrimaryKey,
            time: LocalDateTime,
        ) {
            check(MemberJoins.insert {
                it[joinedTime] = time
                it[objectId] = room
                it[objectType] = ObjectType.ROOM
                it[this.uid] = userId
            }.insertedCount > 0) {
                "join room failed"
            }
        }

        fun addCommunityJoinRaw(
            id: PrimaryKey,
            community: PrimaryKey,
            time: LocalDateTime,
        ) {
            check(MemberJoins.insert {
                it[joinedTime] = time
                it[uid] = id
                it[objectId] = community
                it[objectType] = ObjectType.COMMUNITY
            }.insertedCount > 0) {
                "join community failed"
            }
        }
    }
}
