package com.storyteller_f.a.backend.core

import io.github.aakira.napier.Napier
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import java.util.Properties

class MergedEnv(val list: List<Map<String, String>>) {
    operator fun get(key: String): String? {
        return list.firstNotNullOfOrNull { map ->
            map[key]
        }
    }
}

fun readEnv(envMap: Map<String, String>? = null) = MergedEnv(
    listOfNotNull(
        envMap, // 测试时手动传递
        System.getenv(), // 正式部署
        readFileEnv("../../${BackendConfig.FLAVOR}.env"), // 本地开发
        readFileEnv(".env"), // koyeb 部署
        readResourceEnv(".env"), // 测试
    )
)

fun readResourceEnv(resName: String): Map<String, String>? {
    val stream = ClassLoader.getSystemResourceAsStream(resName)
    Napier.i {
        if (stream == null) {
            "resource env file $resName not exists"
        } else {
            "read env from resource: $resName"
        }
    }
    return stream?.use {
        Properties().apply {
            load(it)
        }
    }?.map {
        it.key as String to it.value as String
    }?.associate { it }
}

fun readFileEnv(resName: String): Map<String, String>? {
    val file = File(resName)
    Napier.i {
        if (file.exists()) {
            "read env from file: ${file.canonicalPath}"
        } else {
            "env file ${file.canonicalPath} not exists"
        }
    }
    return if (file.exists()) {
        FileInputStream(resName).use {
            Properties().apply {
                load(it)
            }
        }.map {
            it.key as String to it.value as String
        }.associate { it }
    } else {
        null
    }
}

fun setLogPath(name: String) {
    if (System.getProperty("LOG_PATH") == null) {
        val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
        val envOs = System.getenv("OSTYPE")?.lowercase(Locale.getDefault()) ?: ""

        // 判断是否是 Windows 原生或 Cygwin / MINGW
        val isWindowsLike =
            osName.contains("win") || envOs.contains("cygwin") || envOs.contains("mingw")

        val logPath = if (isWindowsLike) {
            System.getProperty("java.io.tmpdir")
        } else {
            "/var/log"
        }
        System.setProperty("LOG_PATH", File(logPath, name).canonicalPath)
    }
}

fun preprocessUserInputKeyword(words: List<String>?): String? = words?.let {
    it.filter { w -> w.isNotBlank() }.takeIf { list -> list.isNotEmpty() }?.joinToString(" ")
}
