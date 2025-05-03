package com.storyteller_f.shared.obj

import kotlinx.serialization.Serializable

@Serializable
data class Pagination<T>(val nextPageToken: T?, val prePageToken: T?, val total: Long)

@Serializable
data class ServerResponse<T>(val data: List<T>, val pagination: Pagination<String>? = null)
