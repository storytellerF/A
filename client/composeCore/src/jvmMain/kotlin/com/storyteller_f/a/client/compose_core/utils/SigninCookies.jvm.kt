/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.client.compose_core.utils

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Crypt32Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import kotlin.io.encoding.Base64

actual val supportsChildAccountSwitching = true

internal actual suspend fun readSigninCookies(key: String): String? = readProtectedValue(key)

internal actual suspend fun readProtectedValue(key: String): String? =
    withContext(Dispatchers.IO) {
        val name = credentialName(key)
        when {
            Platform.isWindows() ->
                protectedFile(name).takeIf { Files.exists(it) }?.let {
                    Crypt32Util.cryptUnprotectData(Files.readAllBytes(it)).decodeToString()
                }

            Platform.isMac() ->
                MacKeychain.read("A-signin", name)
                    ?.let { Base64.decode(it.trim()).decodeToString() }

            else -> readSecret(listOf("secret-tool", "lookup", "application", "A-signin", "account", name))
        }
    }

internal actual suspend fun writeSigninCookies(key: String, value: String) = writeProtectedValue(key, value)

internal actual suspend fun writeProtectedValue(key: String, value: String) =
    withContext(Dispatchers.IO) {
        val name = credentialName(key)
        when {
            Platform.isWindows() ->
                saveProtectedFile(name, Crypt32Util.cryptProtectData(value.encodeToByteArray()))

            Platform.isMac() ->
                MacKeychain.write("A-signin", name, Base64.encode(value.encodeToByteArray()))

            else ->
                writeSecret(
                    listOf("secret-tool", "store", "--label=A sign-in", "application", "A-signin", "account", name),
                    value,
                )
        }
    }

private fun protectedFile(name: String): Path =
    System.getProperty("app.protected.storage.dir")?.let { Path.of(it, name) }
        ?: Path.of(System.getProperty("user.home"), ".a", "protected-storage", name)

private fun saveProtectedFile(name: String, encrypted: ByteArray) {
    val target = protectedFile(name)
    Files.createDirectories(target.parent)
    val temporary = Files.createTempFile(target.parent, "protected-", ".tmp")
    try {
        Files.write(temporary, encrypted)
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally {
        Files.deleteIfExists(temporary)
    }
}

private fun credentialName(key: String): String {
    val scope = System.getProperty("app.protected.storage.dir").takeIf { Platform.isMac() }
    val scopedKey = if (scope == null) key else "$scope\u0000$key"
    return MessageDigest.getInstance("SHA-256").digest(scopedKey.encodeToByteArray())
        .joinToString("") { "%02x".format(Locale.ROOT, it) }
}

private suspend fun readSecret(command: List<String>): String? =
    coroutineScope {
        val process = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        BufferedOutputStream(process.outputStream).close()
        val output =
            async(Dispatchers.IO) {
                BufferedInputStream(process.inputStream).use { it.readBytes().decodeToString().trimEnd() }
            }
        try {
            withTimeoutOrNull(10_000) { process.onExit().await() }
                ?: throw java.io.IOException("System credential store timed out")
            if (process.exitValue() == 0) output.await() else null
        } finally {
            if (process.isAlive) process.destroyForcibly()
            output.cancel()
        }
    }

private suspend fun writeSecret(command: List<String>, value: String) =
    coroutineScope {
        val process =
            ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val input =
            async(Dispatchers.IO) {
                BufferedOutputStream(process.outputStream).use { it.write(value.encodeToByteArray()) }
            }
        try {
            withTimeoutOrNull(10_000) {
                input.await()
                process.onExit().await()
            } ?: throw java.io.IOException("System credential store timed out")
            check(process.exitValue() == 0) { "System credential store unavailable" }
        } finally {
            if (process.isAlive) process.destroyForcibly()
            input.cancel()
        }
    }
