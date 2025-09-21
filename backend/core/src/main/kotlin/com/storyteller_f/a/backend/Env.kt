package com.storyteller_f.a.backend

import java.io.File
import java.util.Locale

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
