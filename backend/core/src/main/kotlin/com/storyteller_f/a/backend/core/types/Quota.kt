package com.storyteller_f.a.backend.core.types

import com.storyteller_f.shared.model.QuotaInfo
import com.storyteller_f.shared.model.QuotaType
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey

class Quota(
    val ownerId: PrimaryKey,
    val ownerType: ObjectType,
    val total: Long,
    val used: Long,
    val quotaType: QuotaType,
    val lockId: PrimaryKey?
) {
    companion object
}

fun Quota.toQuotaInfo(): QuotaInfo {
    return QuotaInfo(ownerId, ownerType, quotaType, total, used, lockId)
}
