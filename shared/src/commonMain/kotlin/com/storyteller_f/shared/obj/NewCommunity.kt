package com.storyteller_f.shared.obj

import com.storyteller_f.shared.type.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
data class NewCommunity(val name: String, val aid: String, val icon: PrimaryKey? = null)

@Serializable
data class UpdateCommunityBody(val name: String? = null, val icon: PrimaryKey? = null, val poster: PrimaryKey? = null)
