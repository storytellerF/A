package com.storyteller_f.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class MediaInfo(val url: String, val name: String? = null)

@Serializable
class MediaResponse(val file: String, val contentType: String)
