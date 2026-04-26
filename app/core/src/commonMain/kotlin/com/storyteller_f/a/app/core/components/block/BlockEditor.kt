package com.storyteller_f.a.app.core.components.block

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Block 编辑器主组件
 * 类似 Notion 的编辑体验，每个块独立编辑，支持拖拽排序
 */
@Composable
fun BlockEditor(
    initialMarkdown: String = "",
    onMarkdownChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 解析初始 Markdown 为 Blocks
    val blocks = remember {
        mutableStateListOf<ContentBlock>().apply {
            addAll(parseMarkdownToBlocks(initialMarkdown))
        }
    }

    // 当前聚焦的 Block ID
    var focusedBlockId by remember { mutableStateOf<String?>(null) }

    // 当 Blocks 变化时，生成 Markdown 并回调
    val markdown by derivedStateOf {
        generateMarkdownFromBlocks(blocks.toList())
    }

    LaunchedEffect(markdown) {
        onMarkdownChange(markdown)
    }

    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp)
    ) {
        items(blocks, key = { it.id }) { block ->
            EditableBlock(
                block = block,
                isFocused = block.id == focusedBlockId,
                onContentChange = { newContent ->
                    val index = blocks.indexOfFirst { it.id == block.id }
                    if (index >= 0) {
                        blocks[index] = updateBlockContent(blocks[index], newContent)
                    }
                },
                onFocusChange = { focused ->
                    focusedBlockId = if (focused) block.id else null
                },
                onDelete = {
                    val index = blocks.indexOfFirst { it.id == block.id }
                    if (index >= 0) {
                        blocks.removeAt(index)
                        if (blocks.isEmpty()) {
                            blocks.add(createEmptyParagraphBlock())
                        }
                    }
                },
                onAddBlockBefore = {
                    val index = blocks.indexOfFirst { it.id == block.id }
                    val newBlock = createEmptyParagraphBlock()
                    blocks.add(index, newBlock)
                    focusedBlockId = newBlock.id
                },
                onAddBlockAfter = {
                    val index = blocks.indexOfFirst { it.id == block.id }
                    val newBlock = createEmptyParagraphBlock()
                    blocks.add(index + 1, newBlock)
                    focusedBlockId = newBlock.id
                },
                onChangeBlockType = { newBlock ->
                    val index = blocks.indexOfFirst { it.id == block.id }
                    if (index >= 0) {
                        blocks[index] = newBlock
                    }
                }
            )
        }

        // 底部空白区域，点击可添加新块
        item {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(100.dp))
        }
    }
}

/**
 * 更新 Block 内容，保持类型不变
 */
private fun updateBlockContent(block: ContentBlock, newContent: String): ContentBlock {
    return when (block) {
        is ContentBlock.Paragraph -> block.copy(content = newContent)
        is ContentBlock.ListItem -> block.copy(content = newContent)
        is ContentBlock.Quote -> block.copy(content = newContent)
        is ContentBlock.CodeBlock -> block.copy(content = newContent)
        is ContentBlock.ImageBlock -> block.copy(alt = newContent)
        is ContentBlock.ObjectBlock -> block.copy(title = newContent)
        is ContentBlock.RefBlock -> block.copy(refPath = newContent)
        is ContentBlock.MathBlock -> block.copy(content = newContent)
        is ContentBlock.Divider -> block
    }
}

/**
 * 从外部设置 Markdown 内容（例如加载已保存的帖子）
 */
fun setBlockEditorContent(
    blocks: MutableList<ContentBlock>,
    markdown: String
) {
    blocks.clear()
    blocks.addAll(parseMarkdownToBlocks(markdown))
}
