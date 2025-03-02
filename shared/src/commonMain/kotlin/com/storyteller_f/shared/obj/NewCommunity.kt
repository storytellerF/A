package com.storyteller_f.shared.obj

import kotlinx.serialization.Serializable

@Serializable
data class NewCommunity(val name: String, val aid: String, val icon: String? = null)

@Serializable
data class UpdateCommunityBody(val name: String? = null, val icon: String? = null, val poster: String? = null)
