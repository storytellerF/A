package com.storyteller_f.shared.model

import com.storyteller_f.shared.type.AssetType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class AssetTransactionInfo(
    val assetId: PrimaryKey,
    val type: AssetType,
    val createdTime: LocalDateTime,
    val before: Int,
    val after: Int
)

