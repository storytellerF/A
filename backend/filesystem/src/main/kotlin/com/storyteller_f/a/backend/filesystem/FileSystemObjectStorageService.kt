package com.storyteller_f.a.backend.filesystem

import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder
import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.service.CopyPack
import com.storyteller_f.a.backend.core.service.ObjectStorageRecord
import com.storyteller_f.a.backend.core.service.ObjectStorageService
import com.storyteller_f.a.backend.core.service.ObjectStorageServiceFactory
import com.storyteller_f.a.backend.core.service.ObjectStorageWriteRecord
import com.storyteller_f.a.backend.core.service.UploadPack
import com.storyteller_f.shared.model.A_FILE_DEFAULT_BUCKET
import com.storyteller_f.shared.utils.mapResult
import io.github.aakira.napier.Napier
import io.mikael.urlbuilder.UrlBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.io.InputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.notExists
import kotlin.io.path.pathString
import kotlin.io.path.visitFileTree
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant

class FileSystemObjectStorageService(private val url: String, base: Path) : ObjectStorageService {
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
    ): Result<List<ObjectStorageWriteRecord>> {
        return useFileSystem {
            val bucketPath = base.resolve(bucketName)
            uploadPacks.map { uploadPack ->
                val target = bucketPath.resolve(uploadPack.fullName).createParentDirectories()
                Files.copy(uploadPack.file.toPath(), target, StandardCopyOption.REPLACE_EXISTING)
                ObjectStorageWriteRecord(uploadPack.fullName)
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun get(
        bucketName: String,
        names: List<String>
    ): Result<List<ObjectStorageRecord>> {
        return useFileSystem {
            names.mapNotNull {
                val mediaPath = base.resolve("$bucketName/$it")
                if (mediaPath.exists()) {
                    val newUrl = UrlBuilder.fromString(url)
                        .withPath("a_file/${A_FILE_DEFAULT_BUCKET}/$it")
                        .toString()
                    ObjectStorageRecord(
                        newUrl,
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

    override suspend fun list(
        bucketName: String,
        prefix: String
    ): Result<List<ObjectStorageRecord>> {
        val p = base.resolve("$bucketName/$prefix")
        if (p.notExists()) {
            return Result.success(emptyList())
        }
        return useFileSystem {
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
    ): Result<List<ObjectStorageRecord>> {
        return useFileSystem {
            val bucketPath = base.resolve(bucketName)
            copyPacks.map {
                val p = bucketPath.resolve(it.originFullName)
                if (!p.exists()) {
                    throw Exception("${it.originFullName} not exists")
                }
                val targetFile = bucketPath.resolve(it.newFullName).createParentDirectories()
                p.copyTo(targetFile, true)
                it.newFullName
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

    override suspend fun compose(
        bucketName: String,
        targetFullName: String,
        sourceFullNames: List<String>
    ): Result<ObjectStorageWriteRecord> {
        return useFileSystem {
            val bucketPath = base.resolve(bucketName)
            val target = bucketPath.resolve(targetFullName).createParentDirectories()
            Files.newOutputStream(target).use { out ->
                sourceFullNames.forEach { src ->
                    val p = bucketPath.resolve(src)
                    if (!p.exists()) throw Exception("source $src not exists")
                    Files.newInputStream(p).use { ins ->
                        ins.copyTo(out)
                    }
                }
            }
            ObjectStorageWriteRecord(targetFullName)
        }
    }

    override suspend fun delete(bucketName: String, names: List<String>): Result<Unit> {
        return useFileSystem {
            val bucketPath = base.resolve(bucketName)
            names.forEach { name ->
                val p = bucketPath.resolve(name)
                if (p.exists()) {
                    Files.deleteIfExists(p)
                }
            }
            Unit
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

class FileSystemObjectStorageServiceFactory : ObjectStorageServiceFactory {
    override fun match(env: MergedEnv): Boolean {
        return env["MEDIA_SERVICE"] == "filesystem"
    }

    override fun build(env: MergedEnv): ObjectStorageService {
        val url = env["SERVER_URL"] ?: throw Exception("SERVER_URL is empty")
        val base = env["FILE_SYSTEM_MEDIA_PATH"]
        val p = if (base.isNullOrBlank()) {
            Napier.i {
                "use in-memory file"
            }
            MemoryFileSystemBuilder.newLinux().build().getPath("/a_file")
        } else {
            val path = Paths.get(base)
            Napier.i {
                "use file system oss ${path.toFile().canonicalPath}"
            }
            path
        }
        return FileSystemObjectStorageService(url, p)
    }
}
