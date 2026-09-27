/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.cloud.filesystem

import com.storyteller_f.a.backend.core.service.CopyPack
import com.storyteller_f.a.backend.core.service.ObjectStorageRecord
import com.storyteller_f.a.backend.core.service.ObjectStorageService
import com.storyteller_f.a.backend.core.service.ObjectStorageWriteRecord
import com.storyteller_f.a.backend.core.service.UploadPack
import com.storyteller_f.shared.model.A_FILE_DEFAULT_BUCKET
import com.storyteller_f.shared.utils.cancellableRunCatching
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
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.notExists
import kotlin.io.path.pathString
import kotlin.io.path.visitFileTree
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant

class LocalFileSystemObjectStorageService(private val url: String, base: Path) : ObjectStorageService {
    private val base =
        if (!base.exists()) {
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
    ): Result<List<ObjectStorageWriteRecord>> =
        useFileSystem {
        val bucketPath = resolveBucket(bucketName)
        uploadPacks.map { uploadPack ->
            val target = resolveObject(bucketPath, uploadPack.fullName).createParentDirectories()
            Files.copy(uploadPack.file.toPath(), target, StandardCopyOption.REPLACE_EXISTING)
            ObjectStorageWriteRecord(uploadPack.fullName)
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun get(bucketName: String, names: List<String>): Result<List<ObjectStorageRecord>> =
        useFileSystem {
            names.mapNotNull { name ->
                val mediaPath = resolveObject(bucketName, name)
                if (mediaPath.isRegularFile()) {
                    val newUrl =
                        UrlBuilder.fromString(url)
                            .withPath("a_file/${A_FILE_DEFAULT_BUCKET}/$name")
                            .toString()
                    ObjectStorageRecord(
                        newUrl,
                        mediaPath.getLastModifiedTime().toInstant().toKotlinInstant()
                            .toLocalDateTime(TimeZone.UTC),
                        name,
                    )
                } else {
                    null
                }
            }
        }

    @OptIn(ExperimentalPathApi::class)
    override suspend fun clean(bucketName: String): Result<Unit> =
        useFileSystem {
        val bucketPath = resolveBucket(bucketName)
        bucketPath.deleteRecursively()
    }

    override suspend fun list(bucketName: String, prefix: String): Result<List<ObjectStorageRecord>> {
        val p = if (prefix.isBlank()) resolveBucket(bucketName) else resolveObject(bucketName, prefix)
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

    override suspend fun copy(bucketName: String, copyPacks: List<CopyPack>): Result<List<ObjectStorageRecord>> =
        useFileSystem {
            val bucketPath = resolveBucket(bucketName)
            copyPacks.map {
                val p = resolveObject(bucketPath, it.originFullName)
                if (!p.isRegularFile()) {
                    error("${it.originFullName} not exists")
                }
                val targetFile = resolveObject(bucketPath, it.newFullName).createParentDirectories()
                p.copyTo(targetFile, true)
                it.newFullName
            }
        }.mapResult {
            get(bucketName, it)
        }

    override suspend fun getInputStream(bucketName: String, name: String): Result<InputStream> =
        useFileSystem {
        val mediaPath = resolveObject(bucketName, name)
        if (mediaPath.isRegularFile()) {
            Files.newInputStream(mediaPath).buffered()
        } else {
            error("file $name not exists")
        }
    }

    override suspend fun compose(
        bucketName: String,
        targetFullName: String,
        sourceFullNames: List<String>,
    ): Result<ObjectStorageWriteRecord> =
        useFileSystem {
        val bucketPath = resolveBucket(bucketName)
        val target = resolveObject(bucketPath, targetFullName).createParentDirectories()
        Files.newOutputStream(target).buffered().use { out ->
            sourceFullNames.forEach { src ->
                val p = resolveObject(bucketPath, src)
                check(p.isRegularFile()) { "source $src not exists" }
                Files.newInputStream(p).buffered().use { ins ->
                    ins.copyTo(out)
                }
            }
        }
        ObjectStorageWriteRecord(targetFullName)
    }

    override suspend fun delete(bucketName: String, names: List<String>): Result<Unit> =
        useFileSystem {
        val bucketPath = resolveBucket(bucketName)
        names.forEach { name ->
            val p = resolveObject(bucketPath, name)
            if (p.exists()) {
                Files.deleteIfExists(p)
            }
        }
    }

    suspend fun <T> useFileSystem(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
        cancellableRunCatching {
            block()
        }
    }

    suspend fun getPathResponse(it: List<String>): Path? =
        useFileSystem {
        if (it.size < 2) return@useFileSystem null
        val path = resolveObject(it.first(), it.drop(1).joinToString("/"))
        path.takeIf { candidate -> candidate.isRegularFile() }?.toRealPath()
    }.getOrNull()

    private fun resolveBucket(bucketName: String): Path {
        val bucket = Paths.get(bucketName)
        require(!bucket.isAbsolute && bucket.nameCount == 1 && bucketName !in setOf(".", "..")) {
            "invalid bucket name"
        }
        return resolveWithoutLinks(base, bucketName)
    }

    private fun resolveObject(bucketName: String, name: String): Path = resolveObject(resolveBucket(bucketName), name)

    private fun resolveObject(bucketPath: Path, name: String): Path {
        require(name.isNotBlank()) { "object name is empty" }
        val candidate = Paths.get(name)
        require(!candidate.isAbsolute) { "absolute object name is not allowed" }
        val resolved = bucketPath.resolve(candidate).normalize()
        require(resolved.startsWith(bucketPath)) { "object name escapes its bucket" }
        return resolveWithoutLinks(bucketPath, bucketPath.relativize(resolved).pathString)
    }

    private fun resolveWithoutLinks(parent: Path, relative: String): Path {
        var resolved = parent
        Paths.get(relative).forEach { component ->
            resolved = resolved.resolve(component)
            require(!Files.isSymbolicLink(resolved)) { "symbolic links are not allowed in object paths" }
        }
        return resolved
    }
}
