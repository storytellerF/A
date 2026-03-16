package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.core.types.PanelLog
import com.storyteller_f.a.backend.exposed.BaseTable
import com.storyteller_f.a.backend.exposed.customPrimaryKey
import org.jetbrains.exposed.v1.core.ResultRow

object PanelLogs : BaseTable() {
    val adminId = customPrimaryKey("admin_id").index()
    val targetUserId = customPrimaryKey("target_user_id").index()
    val action = text("action")
}

fun PanelLog.Companion.wrapRow(row: ResultRow): PanelLog {
    return with(PanelLogs) {
        PanelLog(
            row[id],
            row[adminId],
            row[targetUserId],
            row[action],
            row[createdTime]
        )
    }
}
