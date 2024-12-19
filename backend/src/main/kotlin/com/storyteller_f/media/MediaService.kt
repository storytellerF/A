package com.storyteller_f.media

import com.storyteller_f.shared.model.MediaInfo
import java.io.File

data class UploadPack(val name: String, val path: File)

interface MediaService {
    fun upload(bucketName: String, list: List<UploadPack>): Result<List<MediaInfo?>>

    fun get(bucketName: String, objList: List<String?>): Result<List<MediaInfo?>>

    fun clean(bucketName: String): Result<Unit>

    fun list(bucketName: String, prefix: String): Result<List<MediaInfo?>>
}
