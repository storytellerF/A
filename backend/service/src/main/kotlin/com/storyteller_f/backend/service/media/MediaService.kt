package com.storyteller_f.backend.service.media

import com.storyteller_f.a.backend.core.CopyPack
import com.storyteller_f.a.backend.core.UploadPack
import com.storyteller_f.backend.service.Backend
import com.storyteller_f.backend.service.uploadFiles
import com.storyteller_f.shared.model.Dimension
import com.storyteller_f.shared.model.MediaInfo
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import org.apache.tika.Tika
import java.io.InputStream
import javax.imageio.ImageIO
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

data class MediaRecord(val url: String, val lastModified: LocalDateTime, val fullName: String)

interface MediaService {
    suspend fun upload(bucketName: String, uploadPacks: List<UploadPack>): Result<List<MediaRecord>>

    /**
     * @param names 完整的name
     */
    suspend fun get(bucketName: String, names: List<String>): Result<List<MediaRecord>>

    suspend fun clean(bucketName: String): Result<Unit>

    suspend fun list(bucketName: String, prefix: String): Result<List<MediaRecord>>

    suspend fun copy(bucketName: String, copyPacks: List<CopyPack>): Result<List<MediaRecord>>

    suspend fun getInputStream(bucketName: String, name: String): Result<InputStream>
}

suspend fun Backend.uploadFilesAfterDetectContentTypeAndDimension(
    tika: Tika,
    files: List<UploadPack>
): Result<List<MediaInfo?>> {
    return uploadFiles(files.map {
        val detectedType = tika.detect(it.path)
        val dimension = if (detectedType.startsWith("image")) {
            getImageDimension(it.path.absolutePath, detectedType) {
                it.path.inputStream()
            }
        } else {
            null
        }
        it.copy(contentType = detectedType, dimension = dimension)
    })
}

// 在windows 中安装的libavif 名称不符合条件，需要手动改名
fun loadAvif() {
    val osName = System.getProperty("os.name")
    when {
        osName.contains("mac", true) -> System.setProperty("jna.library.path", "/opt/homebrew/lib")
        osName.contains("win", true) -> System.setProperty("jna.library.path", "C:\\msys64\\mingw64\\bin")
        else -> System.setProperty("jna.library.path", "/usr/local/lib")
    }
}

suspend fun extractSvgDimensionInfo(inputStreamProducer: suspend () -> InputStream): Dimension? {
    return withContext(Dispatchers.IO) {
        inputStreamProducer().use {
            val factory = XMLInputFactory.newInstance()
            val reader = factory.createXMLStreamReader(it)
            try {
                if (reader.hasNext()) {
                    val event = reader.next()
                    if (event == XMLStreamConstants.START_ELEMENT && "svg".equals(reader.localName, true)) {
                        val viewBox: String? = reader.getAttributeValue(null, "viewBox")
                        val height: String? = reader.getAttributeValue(null, "height")
                        val width: String? = reader.getAttributeValue(null, "width")
                        getSvgDimension(viewBox, width to height)
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
    }
}

suspend fun getImageDimension(
    filePath: String,
    contentType: String,
    inputStreamProducer: suspend () -> InputStream,
): Dimension? {
    if (contentType == "image/svg+xml") {
        return extractSvgDimensionInfo(inputStreamProducer)
    }
    return ImageIO.getImageReadersByMIMEType(contentType).asSequence().firstNotNullOfOrNull { reader ->
        try {
            inputStreamProducer().use {
                reader.input = ImageIO.createImageInputStream(it)
                reader.read(reader.minIndex)
                Dimension(
                    reader.getWidth(reader.minIndex),
                    reader.getHeight(reader.minIndex)
                )
            }
        } catch (e: Throwable) {
            Napier.e(throwable = e) {
                "get image dimension failed $filePath"
            }
            null
        } finally {
            reader.dispose()
        }
    }
}

fun getSvgDimension(viewBox: String?, pair: Pair<String?, String?>): Dimension? {
    // 获取 width 和 height 属性
    val width = pair.first?.removeSuffix("px")?.toIntOrNull()
    val height = pair.second?.removeSuffix("px")?.toIntOrNull()
    if (width != null && height != null) {
        return Dimension(width, height)
    }
    if (viewBox != null) {
        val viewBoxSizeList = viewBox.split(Regex("\\s+")).map {
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
    return null
}
