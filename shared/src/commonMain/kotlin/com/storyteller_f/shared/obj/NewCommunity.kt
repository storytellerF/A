package com.storyteller_f.shared.obj

import kotlinx.serialization.Serializable

@Serializable
data class NewCommunity(val name: String, val aid: String, val icon: String? = null)
