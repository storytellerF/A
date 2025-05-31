package com.storyteller_f.media

import com.storyteller_f.Backend
import com.storyteller_f.DatabaseFactory
import com.storyteller_f.shared.model.Dimension
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.tables.uploadFiles
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import org.apache.tika.Tika
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

data class UploadPack(
    val path: File,
    val name: String,
    val noPrefixName: String,
    val owner: PrimaryKey,
    val size: Long,
    val detectedContentType: String = "",
    val overrideContentType: String? = null,
    val dimension: Dimension? = null
)

data class CopyPack(val origin: String, val new: String)

interface MediaService {
    suspend fun upload(bucketName: String, uploadPacks: List<UploadPack>): Result<List<Pair<String, LocalDateTime>?>>

    /**
     * @param names 完整的name
     */
    suspend fun get(bucketName: String, names: List<String?>): Result<List<Pair<String, LocalDateTime>?>>

    suspend fun clean(bucketName: String): Result<Unit>

    suspend fun list(bucketName: String, prefix: String): Result<List<Pair<String, LocalDateTime>>>

    suspend fun copy(bucketName: String, copyPacks: List<CopyPack>): Result<List<Pair<String, LocalDateTime>?>>
}

suspend fun uploadFiles(
    tika: Tika,
    backend: Backend,
    files: List<UploadPack>
): Result<List<MediaInfo?>> {
    val packs = files.map {
        val (overrideType, detectedType) = checkContentType(it.path.toPath(), tika, it.overrideContentType)
        val dimension = if (detectedType.startsWith("image")) {
            getImageDimension(it.path.absolutePath, detectedType) {
                it.path.inputStream()
            }
        } else {
            null
        }
        it.copy(detectedContentType = detectedType, overrideContentType = overrideType, dimension = dimension)
    }

    return DatabaseFactory.uploadFiles(backend, packs)
}

private suspend fun checkContentType(
    file: Path,
    tika: Tika,
    contentType: String?
): Pair<String?, String> {
    return withContext(Dispatchers.IO) {
        val s = "audio/mp4"
        val mimeType = tika.detect(file)
        when {
            contentType != s -> null
            mimeType == s -> s
            else -> null
        } to mimeType
    }
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

suspend fun extractSvgDimensionInfo(inputStreamProducer: suspend () -> InputStream): Pair<String?, Pair<String?, String?>> {
    return withContext(Dispatchers.IO) {
        inputStreamProducer().use {
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

suspend fun getImageDimension(
    filePath: String,
    contentType: String,
    inputStreamProducer: suspend () -> InputStream,
): Dimension? {
    if (contentType == "image/svg+xml") {
        return getSvgDimension(inputStreamProducer)
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

private suspend fun getSvgDimension(inputStreamProducer: suspend () -> InputStream): Dimension? {
    val (viewBox, pair) = extractSvgDimensionInfo(inputStreamProducer)
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
