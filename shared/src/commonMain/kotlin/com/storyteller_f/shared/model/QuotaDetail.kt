/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.shared.model

import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class QuotaDetail(val quotaInfo: QuotaInfo, val uploadRecord: UploadRecordInfo? = null) {
    val ownerId: PrimaryKey get() = quotaInfo.ownerId
    val ownerType: ObjectType get() = quotaInfo.ownerType
    val quotaType: QuotaType get() = quotaInfo.quotaType
    val total: Long get() = quotaInfo.total
    val used: Long get() = quotaInfo.used
    val lockId: PrimaryKey? get() = quotaInfo.lockId
}
