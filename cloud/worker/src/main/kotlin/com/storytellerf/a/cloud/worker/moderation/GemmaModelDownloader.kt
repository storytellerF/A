/*
 * This is a private project. All rights reserved.
 */
package com.storytellerf.a.cloud.worker.moderation

import com.storyteller_f.a.backend.core.MergedEnv
import io.github.aakira.napier.Napier
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat

internal const val GEMMA_MODEL_FILE_NAME = "gemma-4-E2B-it.litertlm"
internal const val GEMMA_MODEL_SIZE = 2_588_147_712L
private const val GEMMA_MODEL_SHA256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
private const val HUGGING_FACE_TOKEN = "HUGGING_FACE_HUB_TOKEN"
private const val MODEL_DOWNLOAD_URL =
    "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/$GEMMA_MODEL_FILE_NAME"
private val MODEL_DOWNLOAD_TIMEOUT: Duration = Duration.ofHours(2)

internal fun ensureGemmaModel(
    env: MergedEnv,
    homeDirectory: Path = Path.of(System.getProperty("user.home")),
    modelVerifier: (Path) -> Boolean = ::isCompleteModel,
    modelDownloader: (String?, Path) -> Unit = ::downloadModel,
): Path {
    Files.createDirectories(homeDirectory)
    val modelPath = homeDirectory.resolve(GEMMA_MODEL_FILE_NAME)
    if (modelVerifier(modelPath)) {
        Napier.i(tag = "moderation") {
            "use existing Gemma model at $modelPath"
        }
        return modelPath
    }

    val token = env[HUGGING_FACE_TOKEN]

    val temporaryPath = homeDirectory.resolve("$GEMMA_MODEL_FILE_NAME.part")
    Napier.i(tag = "moderation") {
        "download Gemma model to $modelPath"
    }
    runCatching {
        modelDownloader(token, temporaryPath)
        check(Files.size(temporaryPath) == GEMMA_MODEL_SIZE) {
            "Downloaded Gemma model has an unexpected size"
        }
        check(calculateSha256(temporaryPath) == GEMMA_MODEL_SHA256) {
            "Downloaded Gemma model failed SHA-256 verification"
        }
        moveDownloadedModel(temporaryPath, modelPath)
    }.onFailure {
        Files.deleteIfExists(temporaryPath)
    }.getOrThrow()

    Napier.i(tag = "moderation") {
        "Gemma model download completed"
    }
    return modelPath
}

private fun isCompleteModel(path: Path): Boolean =
    Files.isRegularFile(path) &&
        Files.size(path) == GEMMA_MODEL_SIZE &&
        calculateSha256(path) == GEMMA_MODEL_SHA256

private fun downloadModel(token: String?, destination: Path) {
    val client =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(MODEL_DOWNLOAD_TIMEOUT)
            .build()
    val requestBuilder =
        HttpRequest.newBuilder(URI.create(MODEL_DOWNLOAD_URL))
            .timeout(MODEL_DOWNLOAD_TIMEOUT)
            .GET()
    if (!token.isNullOrBlank()) {
        requestBuilder.header("Authorization", "Bearer $token")
    }
    val request = requestBuilder.build()
    val response =
        client.send(
            request,
            HttpResponse.BodyHandlers.ofFile(
                destination,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            ),
        )
    check(response.statusCode() == HTTP_OK) {
        "Gemma model download failed with HTTP ${response.statusCode()}"
    }
}

private fun calculateSha256(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path).buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var readCount = input.read(buffer)
        while (readCount >= 0) {
            if (readCount > 0) {
                digest.update(buffer, 0, readCount)
            }
            readCount = input.read(buffer)
        }
    }
    return HexFormat.of().formatHex(digest.digest())
}

private fun moveDownloadedModel(source: Path, destination: Path) {
    try {
        Files.move(
            source,
            destination,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING)
    }
}

private const val HTTP_OK = 200
