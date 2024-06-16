package com.storyteller_f.a.server.service

import com.storyteller_f.shared.model.MediaInfo

fun getMediaInfo(iconUrl: String?): MediaInfo? {
    return iconUrl?.let { MediaInfo(it) }
}
