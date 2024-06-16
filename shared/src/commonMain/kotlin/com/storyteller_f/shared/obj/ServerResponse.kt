package com.storyteller_f.shared.obj

import kotlinx.serialization.Serializable

@Serializable
data class ServerResponse<T>(val data: List<T>, val total: Int)
