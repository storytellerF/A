package com.storyteller_f.a.backend.core

import com.storyteller_f.a.backend.core.service.CommunitySearchService
import com.storyteller_f.a.backend.core.service.CommunitySearchServiceFactory
import com.storyteller_f.a.backend.core.service.NameService
import com.storyteller_f.a.backend.core.service.NameServiceFactory
import com.storyteller_f.a.backend.core.service.ObjectStorageService
import com.storyteller_f.a.backend.core.service.ObjectStorageServiceFactory
import com.storyteller_f.a.backend.core.service.RoomSearchService
import com.storyteller_f.a.backend.core.service.RoomSearchServiceFactory
import com.storyteller_f.a.backend.core.service.TopicSearchService
import com.storyteller_f.a.backend.core.service.TopicSearchServiceFactory
import com.storyteller_f.a.backend.core.service.UserSearchService
import com.storyteller_f.a.backend.core.service.UserSearchServiceFactory
import com.storyteller_f.shared.model.Dimension
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.*
import javax.imageio.ImageIO
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

interface Backend {
    val customConfig: CustomConfig
    val topicSearchService: TopicSearchService
    val roomSearchService: RoomSearchService
    val communitySearchService: CommunitySearchService
    val userSearchService: UserSearchService
    val objectStorageService: ObjectStorageService
    val nameService: NameService
    val combinedDatabase: CombinedDatabase

    companion object
}

fun mediaService(env: MergedEnv): ObjectStorageService {
    val factory = ServiceLoader.load(ObjectStorageServiceFactory::class.java).firstOrNull {
        it.match(env)
    } ?: throw Exception("unsupported media service type ${env["MEDIA_SERVICE"]}")
    return factory.build(env)
}

fun buildTopicSearchService(env: MergedEnv): TopicSearchService {
    val factory = ServiceLoader.load(TopicSearchServiceFactory::class.java).firstOrNull {
        it.match(env)
    } ?: throw Exception("unsupported topic search service type ${env["SEARCH_SERVICE"]}")
    return factory.build(env)
}

fun buildUserSearchService(env: MergedEnv): UserSearchService {
    val factory = ServiceLoader.load(UserSearchServiceFactory::class.java).firstOrNull {
        it.match(env)
    } ?: throw Exception("unsupported user search service type ${env["SEARCH_SERVICE"]}")
    return factory.build(env)
}

fun buildRoomSearchService(env: MergedEnv): RoomSearchService {
    val factory = ServiceLoader.load(RoomSearchServiceFactory::class.java).firstOrNull {
        it.match(env)
    } ?: throw Exception("unsupported room search service type ${env["SEARCH_SERVICE"]}")
    return factory.build(env)
}

fun buildCommunitySearchService(env: MergedEnv): CommunitySearchService {
    val factory = ServiceLoader.load(CommunitySearchServiceFactory::class.java).firstOrNull {
        it.match(env)
    } ?: throw Exception("unsupported community search service type ${env["SEARCH_SERVICE"]}")
    return factory.build(env)
}

fun databaseConnection(env: MergedEnv): DatabaseConnection {
    val uri = env["DATABASE_URI"] ?: throw Exception("DATABASE_URI is empty")
    val driver = env["DATABASE_DRIVER"] ?: throw Exception("DATABASE_DRIVE is empty")
    val user = env["DATABASE_USER"] ?: throw Exception("DATABASE_USER is empty")
    val pass = env["DATABASE_PASS"] ?: throw Exception("DATABASE_PASS is empty")
    return DatabaseConnection(uri, driver, user, pass)
}

fun buildNameService(env: MergedEnv): NameService {
    val factory = ServiceLoader.load(NameServiceFactory::class.java).firstOrNull()
        ?: throw Exception("unsupported name service type")
    return factory.build(env)
}

/**
 * 安装教程 https://github.com/AOMediaCodec/libavif
 * 在Windows 上安装后的文件名需要手动重命名为libavif.dll
 */
fun loadAvif() {
    val osName = System.getProperty("os.name")
    when {
        osName.contains("mac", true) -> System.setProperty("jna.library.path", "/opt/homebrew/lib")
        osName.contains("win", true) -> {
//            val vcpkgRoot = System.getenv("VCPKG_ROOT")
//            if (vcpkgRoot.isNullOrBlank()) {
            System.setProperty("jna.library.path", "C:\\msys64\\ucrt64\\bin")
//            } else {
//                System.setProperty("jna.library.path", "${vcpkgRoot}\\packages\\libavif_x64-windows\\bin")
//            }
        }

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
                    if (event == XMLStreamConstants.START_ELEMENT && "svg".equals(
                            reader.localName,
                            true
                        )
                    ) {
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
    return ImageIO.getImageReadersByMIMEType(contentType).asSequence()
        .firstNotNullOfOrNull { reader ->
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
