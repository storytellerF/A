package com.storyteller_f.shared.obj

import kotlinx.serialization.Serializable

@Serializable
data class Pagination(val nextPageToken: String?, val prePageToken: String?, val total: Long)

@Serializable
data class ServerResponse<T>(val data: List<T>, val pagination: Pagination? = null) {
}
