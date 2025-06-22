package com.storyteller_f.a.backend.service.media

import com.storyteller_f.a.backend.core.CopyPack
import com.storyteller_f.a.backend.core.UploadPack
import com.storyteller_f.shared.model.AMEDIA_DEFAULT_BUCKET
import com.storyteller_f.shared.utils.mapResult
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
        uploadPacks: List<UploadPack>,
    ): Result<List<MediaRecord>> {
        return useFileSystem {
            val bucketPath = base.resolve(bucketName)
            uploadPacks.map { uploadPack ->
                val target = bucketPath.resolve(uploadPack.newFullName).createParentDirectories()
                Files.copy(uploadPack.path.toPath(), target, StandardCopyOption.REPLACE_EXISTING)
            }
        }.mapResult {
            get(bucketName, uploadPacks.map {
                it.newFullName
            })
        }
    }

    override suspend fun get(bucketName: String, names: List<String>): Result<List<MediaRecord>> {
        return useFileSystem {
            names.mapNotNull {
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
            }
        }
    }

    @OptIn(ExperimentalPathApi::class)
    override suspend fun clean(bucketName: String): Result<Unit> {
        return useFileSystem {
            val bucketPath = base.resolve(bucketName)
            bucketPath.deleteRecursively()
        }
    }

    override suspend fun list(bucketName: String, prefix: String): Result<List<MediaRecord>> {
        return useFileSystem {
            val p = base.resolve("$bucketName/$prefix")
            buildList<String?> {
                p.visitFileTree(1) {
                    onVisitFile { file, _ ->
                        add("$prefix${file.name}")
                        FileVisitResult.CONTINUE
                    }
                }
            }.filterNotNull()
        }.mapResult {
            get(bucketName, it)
        }
    }

    override suspend fun copy(
        bucketName: String,
        copyPacks: List<CopyPack>,
    ): Result<List<MediaRecord>> {
        return useFileSystem {
            val bucketPath = base.resolve(bucketName)
            copyPacks.map {
                val p = bucketPath.resolve(it.origin)
                if (!p.exists()) {
                    throw Exception("${it.origin} not exists")
                }
                val targetFile = bucketPath.resolve(it.new).createParentDirectories()
                p.copyTo(targetFile, true)
                it.new
            }
        }.mapResult {
            get(bucketName, it)
        }
    }

    override suspend fun getInputStream(
        bucketName: String,
        name: String,
    ): Result<InputStream> {
        return useFileSystem {
            val mediaPath = base.resolve("$bucketName/$name")
            if (mediaPath.exists()) {
                mediaPath.inputStream()
            } else {
                throw Exception("file $name not exists")
            }
        }
    }

    suspend fun <T> useFileSystem(block: suspend () -> T): Result<T> {
        return withContext(Dispatchers.IO) {
            runCatching {
                block()
            }
        }
    }

    suspend fun getPathResponse(it: List<String>): Path? {
        return useFileSystem {
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
