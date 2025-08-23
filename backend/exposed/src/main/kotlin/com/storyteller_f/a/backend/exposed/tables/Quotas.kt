package com.storyteller_f.a.backend.exposed.tables

import com.storyteller_f.a.backend.core.types.Quota
import com.storyteller_f.a.backend.exposed.customPrimaryKey
import com.storyteller_f.a.backend.exposed.objectType
import com.storyteller_f.shared.model.QuotaType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table

object Quotas : Table() {
    val ownerId = customPrimaryKey("owner_id")
    val ownerType = objectType("owner_type")
    val quotaType = enumerationByName<QuotaType>("quota_type", 20)
    val used = long("used")
    val total = long("total")
    val locking = bool("locking")
    override val primaryKey: PrimaryKey?
        get() = PrimaryKey(ownerId, quotaType)
}

fun Quota.Companion.wrapRow(resultRow: ResultRow): Quota {
    return Quota(
        resultRow[Quotas.ownerId],
        resultRow[Quotas.ownerType],
        resultRow[Quotas.total],
        resultRow[Quotas.used],
        resultRow[Quotas.quotaType],
        resultRow[Quotas.locking]
    )
}
