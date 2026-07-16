package com.storyteller_f.a.app.service

import com.storyteller_f.a.app.core.utils.safeSink
import io.github.irgaly.kfswatch.KfsDirectoryWatcher
import io.github.irgaly.kfswatch.KfsDirectoryWatcherEvent
import io.github.irgaly.kfswatch.KfsEvent
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.write

private const val MAX_IMPORT_SIZE = 500L * 1024 * 1024

class GPTOutput(val text: String)

class GPTModel(val key: String, val value: String)

interface GPT {
    val supportList: List<String>

    suspend fun generate(path: String, prompt: String): Result<Flow<GPTOutput>>

    fun models(scope: CoroutineScope): Flow<List<GPTModel>>

    suspend fun importModel(file: PlatformFile): Result<GPTModel> {
        return runCatching {
            val name = file.name
            require(supportList.any { name.endsWith(it, ignoreCase = true) }) {
                "unsupported model file: $name"
            }
            require(file.size() <= MAX_IMPORT_SIZE) {
                "model file too large: ${file.size()} bytes"
            }
            val target = Path(getGPTModelDirectory(), name)
            target.safeSink().buffered().use { it.write(file.readBytes()) }
            GPTModel(target.name, target.toString())
        }
    }
}

class NoOpGPT : GPT {
    override val supportList: List<String> = emptyList()

    override suspend fun generate(path: String, prompt: String): Result<Flow<GPTOutput>> {
        return Result.failure(Exception("unsupported"))
    }

    override fun models(scope: CoroutineScope): Flow<List<GPTModel>> {
        return emptyFlow()
    }
}

expect fun buildGPT(): GPT

expect fun getGPTModelDirectory(): Path

fun buildTranslatePrompt(content: String, target: String): String {
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
    }
}

@OptIn(FlowPreview::class)
fun observeModels(
    scope: CoroutineScope,
    path: Path,
    supportList: List<String>
): Flow<List<GPTModel>> {
    if (!SystemFileSystem.exists(path)) {
        SystemFileSystem.createDirectories(path)
    }
    val watcher = KfsDirectoryWatcher(scope)
    scope.launch {
        watcher.add(path.toString())
    }
    return merge(watcher.onEventFlow, flow {
        emit(KfsDirectoryWatcherEvent("", "", KfsEvent.Create))
    }).debounce(1000).map {
        filterModels(path, supportList).map {
            GPTModel(it.name, it.toString())
        }
    }
}

private fun filterModels(
    path: Path,
    supportList: List<String>
): List<Path> {
    if (!SystemFileSystem.exists(path)) return emptyList()
    return SystemFileSystem.list(path).filter { child ->
        supportList.any {
            child.name.endsWith(it)
        }
    }
}
