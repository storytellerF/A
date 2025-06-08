package com.storyteller_f.query

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.ExposedDatabaseSession
import com.storyteller_f.shared.type.AssetType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.TaskRecordType
import com.storyteller_f.shared.utils.now
import com.storyteller_f.tables.AssetTransaction
import com.storyteller_f.tables.AssetTransactions
import com.storyteller_f.tables.TaskRecord
import com.storyteller_f.tables.Topic
import com.storyteller_f.tables.Users
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update

fun addAssetTransaction(assetTransaction: AssetTransaction) =
    check(AssetTransactions.insert {
        it[type] = assetTransaction.type
        it[before] = assetTransaction.before
        it[after] = assetTransaction.after
    }.insertedCount > 0) {
        "Insert asset transaction failed"
    }

suspend fun ExposedDatabaseSession.addAcgForUser(
    acgList: List<Pair<PrimaryKey, Int>>,
    userAcgMap: Map<Long, Long>,
    list: List<Topic>
): Result<Unit> = dbQuery {
    acgList.forEach { (id, acg) ->
        userAcgMap[id]?.let { oldAcgAmount ->
            Users.update({
                Users.id eq id
            }) {
                it[Users.acgAmount] = oldAcgAmount + acg
            }
            addAssetTransaction(AssetTransaction(AssetType.ACG, oldAcgAmount, oldAcgAmount + acg))
        }
    }

    addTaskRecord(
        TaskRecord(
            SnowflakeFactory.nextId(),
            now(),
            TaskRecordType.TOPIC_ACG,
            list.last().id
        )
    )
}
