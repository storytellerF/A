package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.core.types.AssetTransaction
import com.storyteller_f.a.backend.exposed.BaseTable
import com.storyteller_f.shared.model.AssetType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.r2dbc.insert

object AssetTransactions : BaseTable() {
    val type = enumerationByName<AssetType>("type", 20)
    val before = long("before")
    val after = long("after")
}

fun AssetTransaction.Companion.wrapRow(row: ResultRow): AssetTransaction {
    return with(AssetTransactions) {
        AssetTransaction(
            row[type],
            row[before],
            row[after]
        )
    }
}
