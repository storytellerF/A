package com.storyteller_f.media

class FileSystemMediaService : MediaService {
    override fun upload(bucketName: String, list: List<Pair<String, String>>) = Unit

    override fun get(bucketName: String, objList: List<String?>): List<String?> {
        return objList.map {
            null
        }
    }

    override fun clean(bucketName: String) = Unit
}
