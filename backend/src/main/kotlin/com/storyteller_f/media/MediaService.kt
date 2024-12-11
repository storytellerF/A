package com.storyteller_f.media

import java.io.File

data class UploadPack(val name: String, val path: File)

interface MediaService {
    fun upload(bucketName: String, list: List<UploadPack>): Result<Unit>

    fun get(bucketName: String, objList: List<String?>): Result<List<String?>>

    fun clean(bucketName: String): Result<Unit>
}
