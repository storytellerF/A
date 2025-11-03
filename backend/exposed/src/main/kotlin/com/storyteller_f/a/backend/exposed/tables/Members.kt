package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.core.types.Member
import com.storyteller_f.a.backend.exposed.BaseTable
import com.storyteller_f.a.backend.exposed.customPrimaryKey
import com.storyteller_f.a.backend.exposed.memberStatus
import com.storyteller_f.a.backend.exposed.objectType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.insert

object Members : BaseTable() {
    val uid = customPrimaryKey("uid").index()
    val objectId = customPrimaryKey("object_id").index()
    val objectType = objectType("object_type")
    val status = memberStatus("status")
    val joinedTime = datetime("joined_time").nullable()
    val invitedTime = datetime("invited_time").nullable()

    init {
        uniqueIndex("member-main", objectId, uid)
        index("member-uid", false, uid, objectId)
    }
}

fun Member.Companion.wrapRow(row: ResultRow): Member {
    return with(Members) {
        Member(
            row[id],
            row[uid],
            row[objectId],
            row[objectType],
            row[createdTime],
            row[status],
            row[joinedTime],
            row[invitedTime]
        )
    }
}

suspend fun addJoin(member: Member) {
    check(Members.insert {
        it[id] = member.id
        it[createdTime] = member.createdTime
        it[joinedTime] = member.joinedTime
        it[invitedTime] = member.invitedTime
        it[uid] = member.uid
        it[objectId] = member.objectId
        it[objectType] = member.objectType
        it[status] = member.status
    }.insertedCount > 0) {
        "join failed"
    }
}

suspend fun batchAddMembers(members: List<Member>): List<ResultRow> =
    Members.batchInsert(members) {
        this[Members.joinedTime] = it.createdTime
        this[Members.createdTime] = it.createdTime
        this[Members.invitedTime] = it.invitedTime
        this[Members.id] = it.id
        this[Members.uid] = it.uid
        this[Members.objectId] = it.objectId
        this[Members.objectType] = it.objectType
        this[Members.status] = it.status
    }
