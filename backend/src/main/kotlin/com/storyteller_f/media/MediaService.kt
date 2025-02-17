package com.storyteller_f.media

import com.ashampoo.kim.common.convertToPhotoMetadata
import com.ashampoo.kim.jvm.KimJvm
import com.storyteller_f.Backend
import com.storyteller_f.shared.model.AMEDIA_BUCKET
import com.storyteller_f.shared.model.Dimension
import com.storyteller_f.shared.model.MediaInfo
import io.github.aakira.napier.Napier
import org.apache.tika.Tika
import java.io.File
import javax.imageio.ImageIO
import javax.imageio.stream.FileImageInputStream

data class UploadPack(
    val name: String,
    val path: File,
    val contentType: String? = null,
    val meta: Map<String, String> = emptyMap()
)

interface MediaService {
    fun upload(bucketName: String, list: List<UploadPack>): Result<List<MediaInfo?>>

    fun get(bucketName: String, objList: List<String?>): Result<List<MediaInfo?>>

    fun clean(bucketName: String): Result<Unit>

    fun list(bucketName: String, prefix: String): Result<List<MediaInfo>>
}

fun uploadFiles(
    tika: Tika,
    backend: Backend,
    files: List<Triple<File, String, String?>>
): Result<List<MediaInfo?>> {
    val packs = files.map { (file, saveFileName, contentType) ->
        val type = checkContentType(file, tika, contentType)
        val meta = if (type.second.startsWith("image") == true) {
            getDimension(file, type.second)?.let {
                mapOf("width" to it.width.toString(), "height" to it.height.toString())
            } ?: emptyMap()
        } else {
            emptyMap()
        }
        UploadPack(saveFileName, file, contentType, meta)
    }

    return backend.mediaService.upload(AMEDIA_BUCKET, packs)
}

private fun checkContentType(
    file: File,
    tika: Tika,
    contentType: String?
): Pair<String?, String> {
    val s = "audio/mp4"
    val mimeType = tika.detect(file)
    return if (contentType == s) {
        if (mimeType == s) {
            s
        } else {
            null
        }
    } else {
        null
    } to mimeType
}

fun loadAvif() {
    if (System.getProperty("os.name").contains("Mac")) {
        System.setProperty("jna.library.path", "/opt/homebrew/lib")
    } else {
        System.setProperty("jna.library.path", "/usr/local/lib")
    }
}

fun getDimension(
    file: File,
    contentType: String
): Dimension? {
    if (contentType != "image/avif") {
        try {
            val metadata = KimJvm.readMetadata(file)?.convertToPhotoMetadata()
            if (metadata != null) {
                val width = metadata.widthPx
                val height = metadata.heightPx
                return if (width != null && height != null) {
                    Dimension(width, height)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Napier.e(e) {
                "read $file failed"
            }
            throw e
        }
    }
    return ImageIO.getImageReadersByMIMEType(contentType).asSequence().firstNotNullOfOrNull { reader ->
        try {
            reader.input = FileImageInputStream(file)
            reader.read(reader.minIndex)
            Dimension(
                reader.getWidth(reader.minIndex),
                reader.getHeight(reader.minIndex)
            )
        } catch (e: Throwable) {
            Napier.e(throwable = e) {
                "get image dimension failed $file"
            }
            null
        } finally {
            reader.dispose()
        }
    }
}
