package com.storyteller_f.a.backend.core.types

import com.storyteller_f.shared.model.AssetType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.datetime.LocalDateTime

class AssetTransaction(
    val id: PrimaryKey,
    val createdTime: LocalDateTime,
    val type: AssetType,
    val before: Long,
    val after: Long
) {
    companion object
}
