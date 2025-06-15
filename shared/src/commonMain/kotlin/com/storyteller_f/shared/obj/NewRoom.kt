package com.storyteller_f.shared.obj

import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class NewRoom(val name: String, val aid: String, val icon: PrimaryKey? = null, val communityId: PrimaryKey? = null)

@Serializable
data class UpdateRoomBody(val name: String? = null, val icon: PrimaryKey? = null)
