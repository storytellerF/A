package com.storyteller_f.backend.service.media

import com.storyteller_f.a.backend.core.CopyPack
import com.storyteller_f.a.backend.core.UploadPack
import com.storyteller_f.shared.model.AMEDIA_DEFAULT_BUCKET
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toKotlinInstant
import kotlinx.datetime.toLocalDateTime
import org.apache.hc.core5.net.URIBuilder
import java.io.InputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.*

class FileSystemMediaService(private val url: String, base: Path) : MediaService {
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

    override suspend fun upload(
        bucketName: String,
        uploadPacks: List<UploadPack>
    ): Result<List<MediaRecord>> {
        return withContext(Dispatchers.IO) {
            val bucketPath = base.resolve(bucketName)
            uploadPacks.map { uploadPack ->
                val target = bucketPath.resolve(uploadPack.newFullName).createParentDirectories()
                Files.copy(uploadPack.path.toPath(), target, StandardCopyOption.REPLACE_EXISTING)
            }
            get(bucketName, uploadPacks.map {
                it.newFullName
            })
        }
    }

    override suspend fun get(bucketName: String, names: List<String>): Result<List<MediaRecord>> {
        return withContext(Dispatchers.IO) {
            Result.success(names.mapNotNull {
                val mediaPath = base.resolve("$bucketName/$it")
                if (mediaPath.exists()) {
                    MediaRecord(
                        URIBuilder(url).setPath("amedia/${AMEDIA_DEFAULT_BUCKET}/$it").build()
                            .toString(),
                        mediaPath.getLastModifiedTime().toInstant().toKotlinInstant()
                            .toLocalDateTime(TimeZone.UTC),
                        it
                    )
                } else {
                    null
                }
            })
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

    override suspend fun list(bucketName: String, prefix: String): Result<List<MediaRecord>> {
        return withContext(Dispatchers.IO) {
            val p = base.resolve("$bucketName/$prefix")
            val children = buildList<String?> {
                p.visitFileTree(1) {
                    onVisitFile { file, _ ->
                        add("$prefix${file.name}")
                        FileVisitResult.CONTINUE
                    }
                }
            }.filterNotNull()
            get(bucketName, children)
        }
    }

    override suspend fun copy(
        bucketName: String,
        copyPacks: List<CopyPack>
    ): Result<List<MediaRecord>> {
        return withContext(Dispatchers.IO) {
            val bucketPath = base.resolve(bucketName)
            val newNames = copyPacks.map {
                val p = bucketPath.resolve(it.origin)
                if (!p.exists()) {
                    throw Exception("${it.origin} not exists")
                }
                val targetFile = bucketPath.resolve(it.new).createParentDirectories()
                p.copyTo(targetFile, true)
                it.new
            }
            get(bucketName, newNames)
        }
    }

    override suspend fun getInputStream(
        bucketName: String,
        name: String
    ): Result<InputStream> {
        return withContext(Dispatchers.IO) {
            val mediaPath = base.resolve("$bucketName/$name")
            if (mediaPath.exists()) {
                Result.success(mediaPath.inputStream())
            } else {
                Result.failure(Exception("file $name not exists"))
            }
        }
    }

    fun getPathResponse(it: List<String>): Path? {
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
