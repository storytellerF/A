package com.storyteller_f.a.backend.core.types

import com.storyteller_f.shared.model.AssetType

class AssetTransaction(val type: AssetType, val before: Long, val after: Long) {
    companion object
}
