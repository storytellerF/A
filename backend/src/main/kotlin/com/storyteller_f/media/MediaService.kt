package com.storyteller_f.media

interface MediaService {
    fun upload(bucketName: String, list: List<Pair<String, String>>): Result<Unit>

    fun get(bucketName: String, objList: List<String?>): Result<List<String?>>

    fun clean(bucketName: String): Result<Unit>
}
