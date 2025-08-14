package com.storyteller_f.a.built_in_bot

import com.storyteller_f.shared.utils.now
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

fun main() {
    runBlocking {
        val job = launch {
            while (isActive) {
                Napier.i(tag = "task") {
                    "execute ${now()}"
                }
                delay(10.seconds)
            }
        }
        // 注册 JVM 关闭钩子，捕获 SIGINT / SIGTERM
        Runtime.getRuntime().addShutdownHook(Thread {
            println("🔻 收到终止信号，准备退出...")
            job.cancel()
        })
        try {
            job.join()
        } catch (_: Exception) {
            Napier.i("job done")
        }
        Napier.i("worker done")
    }
}
