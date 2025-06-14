package com.storyteller_f.backend.service.tables

import com.storyteller_f.backend.service.BaseEntity
import com.storyteller_f.backend.service.BaseTable
import com.storyteller_f.backend.service.customPrimaryKey
import com.storyteller_f.backend.service.objectType
import com.storyteller_f.backend.service.titleName
import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.TitleStatus
import com.storyteller_f.shared.type.TitleType
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.*

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

class Title(
    id: PrimaryKey,
    createdTime: LocalDateTime,
    val name: String,
    val creator: PrimaryKey,
    val receiver: PrimaryKey,
    val type: TitleType,
    val scopeId: PrimaryKey,
    val scopeType: ObjectType,
    val status: TitleStatus,
    val descriptionTopicId: PrimaryKey,
) :
    BaseEntity(id, createdTime) {
    companion object {
        fun wrapRow(row: ResultRow): Title {
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
    }
}

fun Title.toTitleInfo(): TitleInfo {
    return TitleInfo(id, createdTime, type, creator, receiver, scopeId, scopeType, name, descriptionTopicId, null)
}
