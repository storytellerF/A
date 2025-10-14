package com.storyteller_f.a.backend.core.types

import com.storyteller_f.shared.model.PanelAccountInfo
import com.storyteller_f.shared.type.PrimaryKey

class PanelAccount(val id: PrimaryKey, val name: String) {
    companion object
}

fun PanelAccount.toPanelAccountInfo() = PanelAccountInfo(id)
