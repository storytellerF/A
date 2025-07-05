package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.exposed.BaseTable
import com.storyteller_f.shared.model.AssetType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.r2dbc.insert

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
        suspend fun addAssetTransaction(assetTransaction: AssetTransaction) =
            check(AssetTransactions.insert {
                it[AssetTransactions.type] = assetTransaction.type
                it[AssetTransactions.before] = assetTransaction.before
                it[AssetTransactions.after] = assetTransaction.after
            }.insertedCount > 0) {
                "Insert asset transaction failed"
            }
    }
}
