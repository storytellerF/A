package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.core.types.Community
import com.storyteller_f.a.backend.exposed.BaseTable
import com.storyteller_f.a.backend.exposed.communityName
import com.storyteller_f.a.backend.exposed.customPrimaryKey
import com.storyteller_f.shared.type.*
import org.jetbrains.exposed.v1.core.*

object Communities : BaseTable() {
    val name = communityName()
    val icon = customPrimaryKey("icon").nullable()
    val poster = customPrimaryKey("poster").index().nullable()
    val owner = customPrimaryKey("owner").index()
    val fontId = customPrimaryKey("font_id").nullable()
}

fun Community.Companion.wrapRow(row: ResultRow): Community {
    return with(Communities) {
        Community(
            row[id],
            row[createdTime],
            row[Aids.value],
            row[name],
            row[owner],
            row[icon],
            row[poster],
            row[fontId]
        )
    }
}
