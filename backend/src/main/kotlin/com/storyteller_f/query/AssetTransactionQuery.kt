package com.storyteller_f.query

import com.storyteller_f.tables.AssetTransaction
import com.storyteller_f.tables.AssetTransactions
import org.jetbrains.exposed.sql.insert

fun addAssetTransaction(assetTransaction: AssetTransaction) =
    check(AssetTransactions.insert {
        it[type] = assetTransaction.type
        it[before] = assetTransaction.before
        it[after] = assetTransaction.after
    }.insertedCount > 0) {
        "Insert asset transaction failed"
    }
