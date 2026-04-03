package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.core.COMMUNITY_NAME_LENGTH
import com.storyteller_f.a.backend.core.types.Community
import com.storyteller_f.a.backend.exposed.BaseTable
import com.storyteller_f.a.backend.exposed.customPrimaryKey
import com.storyteller_f.a.backend.exposed.memberPolicy
import com.storyteller_f.a.backend.exposed.objectStatus
import org.jetbrains.exposed.v1.core.*

object Communities : BaseTable() {
    val name = varchar("name", COMMUNITY_NAME_LENGTH).index()
    val icon = customPrimaryKey("icon").nullable()
    val poster = customPrimaryKey("poster").index().nullable()
    val owner = customPrimaryKey("owner").index()
    val fontId = customPrimaryKey("font_id").nullable()
    val memberPolicy = memberPolicy("member_policy")
    val status = objectStatus("status")
}

fun Community.Companion.wrapRow(row: ResultRow): Community {
    return with(Communities) {
        Community(
            row[id],
            row[createdTime],
            row[Aids.value],
            row[name],
            row[owner],
            row[memberPolicy],
            row[icon],
            row[poster],
            row[fontId],
            row[status],
        )
    }
}
