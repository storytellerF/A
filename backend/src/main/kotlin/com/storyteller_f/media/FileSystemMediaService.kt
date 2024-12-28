package com.storyteller_f.media

import com.j256.simplemagic.ContentInfo
import com.j256.simplemagic.ContentInfoUtil
import com.j256.simplemagic.ContentType
import com.storyteller_f.shared.model.Dimension
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.MediaItem
import io.github.aakira.napier.Napier
import java.io.File
import java.net.URLConnection
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.imageio.ImageIO
import javax.imageio.stream.FileImageInputStream

class FileSystemMediaService(private val url: String, base: String) : MediaService {
    private val root = File(base)
    private val util = ContentInfoUtil()

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
                        val dimension = getDimension(item, file)
                        MediaInfo("${url}amedia/$it", item, dimension)
                    } else {
                        null
                    }
                }
            }
        })
    }

    private fun getDimension(
        item: MediaItem,
        file: File
    ) = ImageIO.getImageReadersByMIMEType(item.contentType).asSequence().firstNotNullOfOrNull { reader ->
        try {
            reader.input = FileImageInputStream(file)
            Dimension(
                reader.getWidth(reader.minIndex),
                reader.getHeight(reader.minIndex)
            )
        } catch (e: Exception) {
            Napier.e(throwable = e) {
                "get image dimension failed ${item.name}"
            }
            null
        } finally {
            reader.dispose()
        }
    }

    private fun stat(it: String, file: File): MediaItem {
        val contentInfo: ContentInfo? = util.findMatch(file)
        val contentType =
            contentInfo?.mimeType ?: URLConnection.guessContentTypeFromName(file.path) ?: ContentType.OTHER.mimeType
        return MediaItem(
            it,
            contentType,
            file.length(),
            it.substringAfter("/")
        )
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

    fun getResponse(it: List<String>): File {
        return File(root, "amedia/${it.joinToString("/")}")
    }
}
