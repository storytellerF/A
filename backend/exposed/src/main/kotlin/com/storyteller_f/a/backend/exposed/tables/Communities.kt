package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.core.COMMUNITY_NAME_LENGTH
import com.storyteller_f.a.backend.core.types.Community
import com.storyteller_f.a.backend.exposed.BaseTable
import com.storyteller_f.a.backend.exposed.customPrimaryKey
import com.storyteller_f.a.backend.exposed.memberPolicy
import com.storyteller_f.a.backend.exposed.objectStatus
import com.storyteller_f.shared.model.FontSettings
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.*

object Communities : BaseTable() {
    val name = varchar("name", COMMUNITY_NAME_LENGTH).index()
    val icon = customPrimaryKey("icon").nullable()
    val poster = customPrimaryKey("poster").index().nullable()
    val owner = customPrimaryKey("owner").index()
    val fontSettings = text("font_settings").nullable()
    val memberPolicy = memberPolicy("member_policy")
    val status = objectStatus("status")
}

private val json = Json { ignoreUnknownKeys = true }

fun Community.Companion.wrapRow(row: ResultRow): Community {
    return with(Communities) {
        val fontSettingsJson = row[fontSettings]
        val fontSettings = fontSettingsJson?.let {
            runCatching { json.decodeFromString<FontSettings>(it) }.getOrNull()
        }
        Community(
            row[id],
            row[createdTime],
            row[Aids.value],
            row[name],
            row[owner],
            row[memberPolicy],
            row[icon],
            row[poster],
            fontSettings,
            row[status],
        )
    }
}
