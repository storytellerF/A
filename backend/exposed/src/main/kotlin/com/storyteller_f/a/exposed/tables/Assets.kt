package com.storyteller_f.a.exposed.tables

import com.storyteller_f.a.exposed.BaseTable
import com.storyteller_f.shared.model.AssetType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert

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
        fun addAssetTransaction(assetTransaction: AssetTransaction) =
            check(AssetTransactions.insert {
                it[AssetTransactions.type] = assetTransaction.type
                it[AssetTransactions.before] = assetTransaction.before
                it[AssetTransactions.after] = assetTransaction.after
            }.insertedCount > 0) {
                "Insert asset transaction failed"
            }
    }
}
