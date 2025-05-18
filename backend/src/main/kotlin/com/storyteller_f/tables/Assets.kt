package com.storyteller_f.tables

import com.storyteller_f.BaseTable
import com.storyteller_f.shared.type.AssetType
import org.jetbrains.exposed.sql.insert

object AssetTransactions : BaseTable() {
    val type = enumerationByName<AssetType>("type", 20)
    val before = long("before")
    val after = long("after")
}

class AssetTransaction(val type: AssetType, val before: Long, val after: Long) {
    companion object {
        fun wrapRow(row: org.jetbrains.exposed.sql.ResultRow): AssetTransaction {
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

fun addAssetTransaction(assetTransaction: AssetTransaction) =
    check(AssetTransactions.insert {
        it[type] = assetTransaction.type
        it[before] = assetTransaction.before
        it[after] = assetTransaction.after
    }.insertedCount > 0) {
        "Insert asset transaction failed"
    }