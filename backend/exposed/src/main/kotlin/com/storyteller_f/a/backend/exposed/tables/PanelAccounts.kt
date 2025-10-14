package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.core.types.PanelAccount
import com.storyteller_f.a.backend.exposed.BaseTable
import com.storyteller_f.shared.type.toPrimaryKey
import org.jetbrains.exposed.v1.core.ResultRow

object PanelAccounts : BaseTable() {
    val name = varchar("name", 100)
}

fun PanelAccount.Companion.wrapRow(resultRow: ResultRow): PanelAccount {
    return PanelAccount(
        resultRow[PanelAccounts.id],
        resultRow[PanelAccounts.name]
    )
}
