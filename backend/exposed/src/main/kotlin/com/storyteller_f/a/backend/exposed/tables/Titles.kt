package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.core.types.Title
import com.storyteller_f.a.backend.exposed.BaseTable
import com.storyteller_f.a.backend.exposed.customPrimaryKey
import com.storyteller_f.a.backend.exposed.objectType
import com.storyteller_f.a.backend.exposed.titleName
import com.storyteller_f.shared.model.TitleStatus
import com.storyteller_f.shared.model.TitleType
import org.jetbrains.exposed.v1.core.*

fun Table.titleType(name: String) = enumerationByName<TitleType>(name, 10)

fun Table.titleStatus(name: String) = enumerationByName<TitleStatus>(name, 10)

object Titles : BaseTable() {
    val creator = customPrimaryKey("creator").index()
    val receiver = customPrimaryKey("receiver").index()
    val type = titleType("type").index()
    val scopeId = customPrimaryKey("scope_id").index()
    val scopeType = objectType("scope_type")
    val status = titleStatus("title_status")
    val name = titleName().index()
    val descriptionTopicId = customPrimaryKey("description_topic_id")

    init {
        index("creator", false, creator, type, scopeId)
        index("receiver", false, receiver, type, scopeId)
        index("creator-status", false, creator, status, type, scopeId)
        index("receiver-status", false, receiver, status, type, scopeId)
    }
}

fun Title.Companion.wrapRow(row: ResultRow): Title {
    return Title(
        row[Titles.id],
        row[Titles.createdTime],
        row[Titles.name],
        row[Titles.creator],
        row[Titles.receiver],
        row[Titles.type],
        row[Titles.scopeId],
        row[Titles.scopeType],
        row[Titles.status],
        row[Titles.descriptionTopicId]
    )
}
