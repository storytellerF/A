package com.storyteller_f.a.backend.core.types

import com.storyteller_f.shared.model.TitleInfo
import com.storyteller_f.shared.model.TitleStatus
import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime

class Title(
    val id: PrimaryKey,
    val createdTime: LocalDateTime,
    val name: String,
    val creator: PrimaryKey,
    val receiver: PrimaryKey,
    val type: TitleType,
    val scopeId: PrimaryKey,
    val scopeType: ObjectType,
    val status: TitleStatus,
    val descriptionTopicId: PrimaryKey,
) {
    companion object
}
fun Title.toTitleInfo(): TitleInfo {
    return TitleInfo(id, createdTime, type, creator, receiver, scopeId, scopeType, name, descriptionTopicId, null)
}
