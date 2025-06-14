package com.storyteller_f.backend.service.query

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.backend.service.ExposedDatabaseSession
import com.storyteller_f.backend.service.tables.AssetTransaction
import com.storyteller_f.backend.service.tables.AssetTransactions
import com.storyteller_f.backend.service.tables.TaskRecord
import com.storyteller_f.backend.service.tables.Topic
import com.storyteller_f.backend.service.tables.Users
import com.storyteller_f.shared.type.AssetType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.TaskRecordType
import com.storyteller_f.shared.utils.now
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update

fun addAssetTransaction(assetTransaction: AssetTransaction) =
    check(AssetTransactions.insert {
        it[AssetTransactions.type] = assetTransaction.type
        it[AssetTransactions.before] = assetTransaction.before
        it[AssetTransactions.after] = assetTransaction.after
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
