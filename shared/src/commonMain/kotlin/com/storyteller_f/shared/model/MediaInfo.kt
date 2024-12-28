package com.storyteller_f.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class Dimension(val width: Int, val height: Int)

@Serializable
data class MediaInfo(val url: String, val item: MediaItem, val dimension: Dimension?)

@Serializable
data class MediaItem(val name: String, val contentType: String, val size: Long, val noPrefixName: String)

@Serializable
class MediaResponse(val file: String, val contentType: String)
