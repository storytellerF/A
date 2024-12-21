package com.storyteller_f.media

import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.MediaItem
import java.io.File
import java.net.URLConnection
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class FileSystemMediaService(private val url: String, base: String) : MediaService {
    private val root = File(base, "a")

    init {
        if (!root.exists()) {
            val r = root.mkdir()
            assert(r)
        }
    }

    override fun upload(bucketName: String, list: List<UploadPack>): Result<List<MediaInfo?>> {
        val file = File(root, bucketName)
        if (!file.exists()) {
            val r = file.mkdir()
            if (!r) {
                return Result.failure(Exception("bucket $bucketName create failed"))
            }
        }

        list.map { uploadPack ->
            val target = File(file, uploadPack.name)
            target.parentFile?.let {
                if (!it.exists()) {
                    val r = it.mkdirs()
                    if (!r) {
                        return Result.failure(Exception("create $it failed"))
                    }
                }
            }
            Files.copy(uploadPack.path.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        return get(bucketName, list.map {
            it.name
        })
    }

    override fun get(bucketName: String, objList: List<String?>): Result<List<MediaInfo?>> {
        return Result.success(objList.map {
            when (it) {
                null -> null
                else -> {
                    val file = File(root, "$bucketName/$it")
                    MediaInfo(
                        "${url}amedia/$it",
                        MediaItem(it, URLConnection.guessContentTypeFromName(it), file.length())
                    )
                }
            }
        })
    }

    override fun clean(bucketName: String): Result<Unit> {
        val file = File(root, bucketName)
        file.deleteRecursively()
        return Result.success(Unit)
    }

    override fun list(bucketName: String, prefix: String): Result<List<MediaInfo?>> {
        val file = File(root, "$bucketName/$prefix")
        return get(bucketName, file.listFiles().orEmpty().map {
            "${prefix}${it.name}"
        })
    }
}
