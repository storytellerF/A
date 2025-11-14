package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.core.types.Quota
import com.storyteller_f.a.backend.exposed.customPrimaryKey
import com.storyteller_f.a.backend.exposed.objectType
import com.storyteller_f.a.backend.exposed.quotaType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table

object Quotas : Table() {
    val ownerId = customPrimaryKey("owner_id")
    val ownerType = objectType("owner_type")
    val quotaType = quotaType("quota_type")
    val used = long("used")
    val total = long("total")
    val lockId = customPrimaryKey("lock_id").nullable()
    override val primaryKey = PrimaryKey(ownerId, quotaType)
}

fun Quota.Companion.wrapRow(resultRow: ResultRow): Quota {
    return Quota(
        resultRow[Quotas.ownerId],
        resultRow[Quotas.ownerType],
        resultRow[Quotas.total],
        resultRow[Quotas.used],
        resultRow[Quotas.quotaType],
        resultRow[Quotas.lockId]
    )
}
