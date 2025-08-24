package com.storyteller_f.a.app.dev_cli

import com.storyteller_f.a.app.dev.getConnectedDevices
import com.storyteller_f.a.app.dev.startListening
import java.io.File
import java.util.Scanner

private val previousDevices = mutableSetOf<String>()

fun main() {
    val gitBash = "C:/Program Files/Git/bin/bash.exe"
    val runPath = File("").canonicalPath
    println("current path: $runPath")
    val isNested = runPath.endsWith("devCli")
    val forwardScriptPath = File(
        if (isNested) "../.." else ".",
        "scripts/android_scripts/forward-android-devices.sh"
    ).canonicalPath
    val process = ProcessBuilder(gitBash, "-c", "$forwardScriptPath 9000").start()
    check(process.waitFor() == 0)
    println(process.inputReader().readText())
    previousDevices.addAll(getConnectedDevices())
    val job = startListening(9000, previousDevices)
    Runtime.getRuntime().addShutdownHook(object : Thread() {
        override fun run() {
            super.run()
            job.cancel()
            println("force shutdown")
        }
    })
    val scanner = Scanner(System.`in`)
    while (true) {
        if (!scanner.hasNextLine()) {
            println("manual shutdown")
            break
        }
    }
}
