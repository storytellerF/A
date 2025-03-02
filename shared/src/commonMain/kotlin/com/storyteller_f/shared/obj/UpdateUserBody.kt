package com.storyteller_f.shared.obj

import kotlinx.serialization.Serializable

@Serializable
data class UpdateUserBody(val nickname: String? = null, val avatar: String? = null, val aid: String? = null)
