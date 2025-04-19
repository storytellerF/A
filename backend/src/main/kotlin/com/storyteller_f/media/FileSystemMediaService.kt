package com.storyteller_f.media

import com.storyteller_f.shared.model.AMEDIA_DEFAULT_BUCKET
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.MediaItem
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toKotlinInstant
import kotlinx.datetime.toLocalDateTime
import org.apache.http.client.utils.URIBuilder
import org.apache.tika.Tika
import java.net.URLConnection
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.*

class FileSystemMediaService(private val url: String, base: Path) : MediaService {
    private val tika = Tika()
    private val base = if (!base.exists()) {
        base.createDirectories()
    } else {
        base.toRealPath()
    }

    init {
        Napier.i {
            "media path ${base.toRealPath().pathString}"
        }
    }

    override suspend fun upload(bucketName: String, list: List<UploadPack>): Result<List<MediaInfo?>> {
        return withContext(Dispatchers.IO) {
            val bucketPath = base.resolve(bucketName)
            if (!bucketPath.exists()) {
                bucketPath.createDirectories()
            }

            list.map { uploadPack ->
                val target = bucketPath.resolve(uploadPack.name)
                target.createParentDirectories()
                Files.copy(uploadPack.path.toPath(), target, StandardCopyOption.REPLACE_EXISTING)
            }
            get(bucketName, list.map {
                it.name
            })
        }
    }

    override suspend fun get(bucketName: String, objList: List<String?>): Result<List<MediaInfo?>> {
        return withContext(Dispatchers.IO) {
            Result.success(objList.map {
                when (it) {
                    null -> null
                    else -> {
                        val mediaPath = base.resolve("$bucketName/$it")
                        if (mediaPath.exists()) {
                            val item = stat(it, mediaPath)
                            val dimension = getDimension(mediaPath, item.contentType)
                            MediaInfo(
                                URIBuilder(url).setPath("amedia/${AMEDIA_DEFAULT_BUCKET}/$it").build().toString(),
                                item,
                                dimension
                            )
                        } else {
                            null
                        }
                    }
                }
            })
        }
    }

    private suspend fun stat(it: String, file: Path): MediaItem {
        return withContext(Dispatchers.IO) {
            val contentType = runCatching {
                tika.detect(file)
            }.getOrNull() ?: URLConnection.guessContentTypeFromName(file.pathString)
                ?: org.apache.http.entity.ContentType.APPLICATION_OCTET_STREAM.mimeType
            MediaItem(
                it,
                contentType,
                file.fileSize(),
                it.substringAfter("/"),
                file.getLastModifiedTime().toInstant().toKotlinInstant().toLocalDateTime(TimeZone.UTC)
            )
        }
    }

    @OptIn(ExperimentalPathApi::class)
    override suspend fun clean(bucketName: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val bucketPath = base.resolve(bucketName)
                bucketPath.deleteRecursively()
            }
        }
    }

    override suspend fun list(bucketName: String, prefix: String): Result<List<MediaInfo>> {
        return withContext(Dispatchers.IO) {
            val p = base.resolve("$bucketName/$prefix")
            val children = buildList<String?> {
                p.visitFileTree(1) {
                    onVisitFile { file, _ ->
                        add("$prefix${file.name}")
                        FileVisitResult.CONTINUE
                    }
                }
            }
            get(bucketName, children).map {
                it.filterNotNull()
            }
        }
    }

    fun getResponse(it: List<String>): Path? {
        return runCatching {
            val path = base.resolve(it.joinToString("/"))
            val file = path.toRealPath()
            if (file.pathString != path.absolutePathString()) {
                null
            } else {
                file
            }
        }.getOrNull()
    }
}
