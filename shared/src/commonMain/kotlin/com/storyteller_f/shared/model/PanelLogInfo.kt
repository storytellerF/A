package com.storyteller_f.shared.model

import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class PanelLogInfo(
    override val id: PrimaryKey,
    val adminId: PrimaryKey,
    val targetId: PrimaryKey,
    val objectType: ObjectType,
    val action: String,
    val createdTime: LocalDateTime
) : PrimaryKeyIdentifiable
