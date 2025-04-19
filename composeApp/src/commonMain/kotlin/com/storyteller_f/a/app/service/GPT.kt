package com.storyteller_f.a.app.service

import com.storyteller_f.shared.type.GPTModel
import com.storyteller_f.shared.type.GPTOutput
import io.github.irgaly.kfswatch.KfsDirectoryWatcher
import io.github.irgaly.kfswatch.KfsDirectoryWatcherEvent
import io.github.irgaly.kfswatch.KfsEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.collections.map

interface GPT {
    fun generate(path: String, prompt: String, stopWord: String): Result<Flow<GPTOutput>>

    fun models(scope: CoroutineScope): Flow<List<GPTModel>>
}

class NoOpGPT : GPT {
    override fun generate(path: String, prompt: String, stopWord: String): Result<Flow<GPTOutput>> {
        return Result.failure(Exception("unsupported"))
    }

    override fun models(scope: CoroutineScope): Flow<List<GPTModel>> {
        return emptyFlow()
    }
}

expect fun buildGPT(): GPT

fun buildTranslatePrompt(content: String, target: String, current: String): Pair<String, String> {
    return buildString {
        append(
            """
        你是一个专业的技术文档翻译助手。请将以下 Markdown 文档内容翻译为【$target】，要求如下：

        1. **保留所有 Markdown 格式**（如 `#` 标题、`**加粗**`、`-` 列表、`> 引用`、```代码块``` 等）；
        2. **不要翻译代码内容、链接地址和文件路径**；
        3. **保留原文的结构与层级**；
        4. 翻译时注意准确性和语义自然性，特别是专业术语；
        5. 返回结果仅包含翻译后的 Markdown，不要添加额外解释；

        Markdown 内容如下：
            """.trimIndent()
        )
        appendLine(content)
        if (current.endsWith("gguf")) {
            appendLine("###END###")
        }
    } to "###END###"
}

fun observeModels(
    scope: CoroutineScope,
    path: Path,
    supportList: List<String>
): Flow<List<GPTModel>> {
    val watcher = KfsDirectoryWatcher(scope)
    scope.launch {
        watcher.add(path.toString())
    }
    return merge(watcher.onEventFlow, flow {
        emit(KfsDirectoryWatcherEvent("", "", KfsEvent.Create))
    }).map {
        SystemFileSystem.list(path).filter { child ->
            supportList.any {
                child.name.endsWith(it)
            }
        }.map {
            GPTModel(it.name, it.toString())
        }
    }
}
