package com.storyteller_f.shared.obj

import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.type.TitleType
import kotlinx.serialization.Serializable

@Serializable
data class NewTitle(
    val name: String,
    val type: TitleType,
    val receiver: PrimaryKey,
    val scopeId: PrimaryKey,
    val scopeType: ObjectType,
    val description: String,
)
