package com.storyteller_f.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class MediaInfo(val url: String, val item: MediaItem)

@Serializable
data class MediaItem(val name: String, val contentType: String?, val size: Long)

@Serializable
class MediaResponse(val file: String, val contentType: String)
