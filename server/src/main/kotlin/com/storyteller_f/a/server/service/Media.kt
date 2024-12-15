package com.storyteller_f.a.server.service

import com.storyteller_f.Backend
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.obj.ServerResponse
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.mapResult

suspend fun getMediaList(uid: PrimaryKey, backend: Backend): Result<ServerResponse<MediaInfo?>> {
    return backend.mediaService.list("amedia", "$uid").mapResult { names ->
        backend.mediaService.get("amedia", names.map {
            "$uid/$it"
        }).map {
            ServerResponse(it.mapIndexedNotNull { index, s ->
                s?.let { it1 -> MediaInfo(it1, names[index]) }
            })
        }
    }
}
