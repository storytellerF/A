package com.storyteller_f.media

class FileSystemMediaService : MediaService {
    override fun upload(bucketName: String, list: List<Pair<String, String>>): Result<Unit> = Result.success(Unit)

    override fun get(bucketName: String, objList: List<String?>): Result<List<String?>> {
        return Result.success(objList.map {
            null
        })
    }

    override fun clean(bucketName: String): Result<Unit> = Result.success(Unit)
}
