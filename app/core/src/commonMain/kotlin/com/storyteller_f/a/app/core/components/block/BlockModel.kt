package com.storyteller_f.a.app.core.components.block

import kotlinx.serialization.Serializable

/**
 * Block 数据模型，用于表示文档中的一个内容块
 * 类似 Notion 的 Block 概念，每个块独立编辑，支持拖拽排序
 */
@Serializable
sealed interface ContentBlock {
    val id: String
    val type: BlockType

    /**
     * 段落块 - 普通文本内容
     */
    @Serializable
    data class Paragraph(
        override val id: String,
        val content: String,
        val level: Int = 0 // 0=普通文本, 1-6=标题
    ) : ContentBlock {
        override val type: BlockType = BlockType.PARAGRAPH
    }

    /**
     * 列表项 - 有序/无序列表
     */
    @Serializable
    data class ListItem(
        override val id: String,
        val content: String,
        val ordered: Boolean,
        val indent: Int = 0
    ) : ContentBlock {
        override val type: BlockType = BlockType.LIST_ITEM
    }

    /**
     * 引用块 - BlockQuote
     */
    @Serializable
    data class Quote(
        override val id: String,
        val content: String
    ) : ContentBlock {
        override val type: BlockType = BlockType.QUOTE
    }

    /**
     * 代码块 - CodeFence
     */
    @Serializable
    data class CodeBlock(
        override val id: String,
        val content: String,
        val language: String = ""
    ) : ContentBlock {
        override val type: BlockType = BlockType.CODE
    }

    /**
     * 图片块
     */
    @Serializable
    data class ImageBlock(
        override val id: String,
        val name: String,
        val url: String = "",
        val alt: String = "",
        val title: String = ""
    ) : ContentBlock {
        override val type: BlockType = BlockType.IMAGE
    }

    /**
     * 对象嵌入块 - 用于嵌入视频、音频等多媒体对象
     */
    @Serializable
    data class ObjectBlock(
        override val id: String,
        val name: String = "",
        val url: String = "",
        val contentType: String? = null,
        val cover: String? = null,
        val title: String? = null,
        val isPlaylist: Boolean? = null
    ) : ContentBlock {
        override val type: BlockType = BlockType.OBJECT
    }

    /**
     * 自定义引用块 - CSA (com.storyteller_f.a)
     * 用于引用其他用户、社区、房间、帖子等
     */
    @Serializable
    data class RefBlock(
        override val id: String,
        val refPath: String // 如 /user/123, /community/456
    ) : ContentBlock {
        override val type: BlockType = BlockType.REF
    }

    /**
     * 数学公式块
     */
    @Serializable
    data class MathBlock(
        override val id: String,
        val content: String,
        val inline: Boolean = false
    ) : ContentBlock {
        override val type = if (inline) BlockType.MATH_INLINE else BlockType.MATH
    }

    /**
     * 分割线
     */
    @Serializable
    data class Divider(
        override val id: String
    ) : ContentBlock {
        override val type: BlockType = BlockType.DIVIDER
    }
}

/**
 * Block 类型枚举
 */
enum class BlockType {
    PARAGRAPH,
    LIST_ITEM,
    QUOTE,
    CODE,
    IMAGE,
    OBJECT,
    REF,
    MATH,
    MATH_INLINE,
    DIVIDER
}

/**
 * 生成唯一 Block ID
 */
fun generateBlockId(): String = "block_${java.util.UUID.randomUUID().toString().take(8)}"

/**
 * 创建默认的空段落块
 */
fun createEmptyParagraphBlock(): ContentBlock.Paragraph {
    return ContentBlock.Paragraph(
        id = generateBlockId(),
        content = ""
    )
}
