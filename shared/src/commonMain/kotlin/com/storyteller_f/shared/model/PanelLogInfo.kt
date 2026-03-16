package com.storyteller_f.shared.model

import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class PanelLogInfo(
    val id: PrimaryKey,
    val adminId: PrimaryKey,
    val targetUserId: PrimaryKey,
    val action: String,
    val createdTime: LocalDateTime
)
