package com.storyteller_f.media

import com.ashampoo.kim.common.convertToPhotoMetadata
import com.ashampoo.kim.jvm.KimJvm
import com.storyteller_f.Backend
import com.storyteller_f.shared.model.AMEDIA_BUCKET
import com.storyteller_f.shared.model.Dimension
import com.storyteller_f.shared.model.MediaInfo
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.tika.Tika
import java.io.File
import java.io.FileInputStream
import javax.imageio.ImageIO
import javax.imageio.stream.FileImageInputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

data class UploadPack(
    val name: String,
    val path: File,
    val contentType: String? = null,
    val meta: Map<String, String> = emptyMap()
)

interface MediaService {
    suspend fun upload(bucketName: String, list: List<UploadPack>): Result<List<MediaInfo?>>

    suspend fun get(bucketName: String, objList: List<String?>): Result<List<MediaInfo?>>

    suspend fun clean(bucketName: String): Result<Unit>

    suspend fun list(bucketName: String, prefix: String): Result<List<MediaInfo>>
}

suspend fun uploadFiles(
    tika: Tika,
    backend: Backend,
    files: List<Triple<File, String, String?>>
): Result<List<MediaInfo?>> {
    val packs = files.map { (file, saveFileName, contentType) ->
        val type = checkContentType(file, tika, contentType)
        val meta = if (type.second.startsWith("image")) {
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
    val osName = System.getProperty("os.name")
    when {
        osName.contains("mac", true) -> System.setProperty("jna.library.path", "/opt/homebrew/lib")
        osName.contains("win", true) -> System.setProperty("jna.library.path", "C:\\msys64\\mingw64\\bin")
        else -> System.setProperty("jna.library.path", "/usr/local/lib")
    }
}

suspend fun getSvgSize(file: File): Pair<String?, Pair<String?, String?>> {
    return withContext(Dispatchers.IO) {
        FileInputStream(file).use {
            val factory = XMLInputFactory.newInstance()
            val reader = factory.createXMLStreamReader(it)
            try {
                if (reader.hasNext()) {
                    val event = reader.next()
                    if (event == XMLStreamConstants.START_ELEMENT && "svg".equals(reader.localName, true)) {
                        val viewBox = reader.getAttributeValue(null, "viewBox")
                        val height = reader.getAttributeValue(null, "height")
                        val width = reader.getAttributeValue(null, "width")
                        viewBox to (width to height)
                    } else {
                        null
                    }
                } else {
                    null
                }
            } finally {
                reader.close()
            }
        }
    } ?: (null to (null to null))
}

suspend fun getDimension(
    file: File,
    contentType: String
): Dimension? {
    if (contentType == "image/svg+xml") {
        val d = getSvgDimension(file)
        if (d != null) {
            return d
        }
    } else if (contentType != "image/avif") {
        val d = getAvifDimension(file)
        if (d != null) return d
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

private fun getAvifDimension(file: File): Dimension? {
    try {
        val metadata = KimJvm.readMetadata(file)?.convertToPhotoMetadata()
        if (metadata != null) {
            val width = metadata.widthPx
            val height = metadata.heightPx
            if (width != null && height != null) {
                return Dimension(width, height)
            }
        }
    } catch (e: Exception) {
        Napier.e(e) {
            "read $file failed"
        }
        throw e
    }
    return null
}

private suspend fun getSvgDimension(file: File): Dimension? {
    val (viewBox, pair) = getSvgSize(file)
    if (viewBox != null) {
        val viewBoxSizeList = viewBox.split(" ").map {
            it.trim().toFloatOrNull()
        }
        if (viewBoxSizeList.size == 4) {
            val width = viewBoxSizeList[2]
            val height = viewBoxSizeList[3]
            if (width != null && height != null) {
                return Dimension(width.toInt(), height.toInt())
            }
        }
    }

    // 获取 width 和 height 属性
    val width = pair.first?.removeSuffix("px")?.toIntOrNull()
    val height = pair.second?.removeSuffix("px")?.toIntOrNull()
    if (width != null && height != null) {
        return Dimension(width, height)
    }
    return null
}
