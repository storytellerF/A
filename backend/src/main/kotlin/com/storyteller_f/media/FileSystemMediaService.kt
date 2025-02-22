package com.storyteller_f.media

import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.MediaItem
import io.github.aakira.napier.Napier
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.apache.http.client.utils.URIBuilder
import org.apache.tika.Tika
import java.io.File
import java.net.URLConnection
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class FileSystemMediaService(private val url: String, base: String) : MediaService {
    private val root = File(base).canonicalFile
    private val tika = Tika()

    init {
        Napier.i {
            "media path ${root.canonicalPath}"
        }
        if (!root.exists()) {
            val r = root.mkdirs()
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
                    if (file.exists()) {
                        val item = stat(it, file)
                        val dimension = getDimension(file, item.contentType)
                        MediaInfo(URIBuilder(url).setPath("amedia/$it").build().toString(), item, dimension)
                    } else {
                        null
                    }
                }
            }
        })
    }

    private fun stat(it: String, file: File): MediaItem {
        val contentType = kotlin.runCatching {
            tika.detect(file)
        }.getOrNull() ?: URLConnection.guessContentTypeFromName(file.path)
            ?: org.apache.http.entity.ContentType.APPLICATION_OCTET_STREAM.mimeType
        return MediaItem(
            it,
            contentType,
            file.length(),
            it.substringAfter("/"),
            Instant.fromEpochMilliseconds(file.lastModified()).toLocalDateTime(TimeZone.UTC)
        )
    }

    override fun clean(bucketName: String): Result<Unit> {
        val file = File(root, bucketName)
        file.deleteRecursively()
        return Result.success(Unit)
    }

    override fun list(bucketName: String, prefix: String): Result<List<MediaInfo>> {
        val file = File(root, "$bucketName/$prefix")
        return get(bucketName, file.listFiles().orEmpty().map {
            "${prefix}${it.name}"
        }).map {
            it.filterNotNull()
        }
    }

    fun getResponse(it: List<String>): File? {
        val file = File(root, "amedia/${it.joinToString("/")}")
        if (file.canonicalPath != file.absolutePath) {
            return null
        }
        return file
    }
}
