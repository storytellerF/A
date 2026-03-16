package com.storyteller_f.a.backend.core.types

import com.storyteller_f.shared.model.PanelLogInfo
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime

class PanelLog(
    val id: PrimaryKey,
    val adminId: PrimaryKey,
    val targetUserId: PrimaryKey,
    val action: String,
    val createdTime: LocalDateTime
) {
    companion object
}

fun PanelLog.toPanelLogInfo(): PanelLogInfo {
    return PanelLogInfo(id, adminId, targetUserId, action, createdTime)
}
