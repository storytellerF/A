package com.storyteller_f.shared.model

import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class PanelAccountInfo(override val id: PrimaryKey, val name: String) : PrimaryKeyIdentifiable {
    override val objectType: ObjectType = ObjectType.PANEL_ACCOUNT
}
