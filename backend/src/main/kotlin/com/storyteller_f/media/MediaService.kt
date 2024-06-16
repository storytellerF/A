package com.storyteller_f.media

interface MediaService {
    fun upload(bucketName: String, list: List<Pair<String, String>>)

    fun get(bucketName: String, objList: List<String?>) : List<String?>

    fun clean(bucketName: String)
}
