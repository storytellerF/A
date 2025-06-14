package com.storyteller_f.backend.service.tables

import com.storyteller_f.backend.service.BaseTable
import com.storyteller_f.shared.type.AssetType
import org.jetbrains.exposed.sql.ResultRow

object AssetTransactions : BaseTable() {
    val type = enumerationByName<AssetType>("type", 20)
    val before = long("before")
    val after = long("after")
}

class AssetTransaction(val type: AssetType, val before: Long, val after: Long) {
    companion object {
        fun wrapRow(row: ResultRow): AssetTransaction {
            return with(AssetTransactions) {
                AssetTransaction(
                    row[type],
                    row[before],
                    row[after]
                )
            }
        }
    }
}
