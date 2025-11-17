package com.storyteller_f.shared.utils

import com.storyteller_f.shared.commonJson
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.type.ObjectType
import kotlinx.serialization.Serializable
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.accept
import org.intellij.markdown.ast.acceptChildren
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.ast.visitors.Visitor
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser

fun extractMarkdownHeadline(markdownText: String): String {
    val parsedTree = astNode(markdownText)

    val paragraphs = StringBuilder()
    var headline: CharSequence = ""
    var captureContent = false

    val typeList = markdownElementTypes()

    parsedTree.accept(object : Visitor {
        override fun visitNode(node: ASTNode) {
            val type = node.type
            when {
                type == MarkdownElementTypes.MARKDOWN_FILE -> captureContent = true
                type == MarkdownElementTypes.ATX_1 -> {
                    // Extract the first level header content
                    headline = node.getTextInNode(markdownText).trim().take(50)
                }

                type == MarkdownElementTypes.PARAGRAPH -> {
                    if (captureContent) {
                        val content = node.getTextInNode(markdownText).trim()
                        if (content.isNotEmpty()) {
                            paragraphs.appendLine(content)
                        }
                    }
                }

                typeList.any {
                    type.name == it.name
                } -> {
                    // Stop capturing when encountering the first second-level header
                    captureContent = false
                }
            }

            if (captureContent) {
                node.acceptChildren(this)
            }
        }
    })
    return if (headline.isNotBlank()) {
        "$headline\n${paragraphs.toString().trim().take(150)}"
    } else {
        extractHeadParagraph(markdownText)
    }
}

fun extractHeadParagraph(
    markdownText: String,
): String {
    val paragraphs = StringBuilder()
    val parsedTree = astNode(markdownText)
    var captureContent = false
    val typeList = markdownElementTypes()
    parsedTree.accept(object : Visitor {
        override fun visitNode(node: ASTNode) {
            val type = node.type
            val children = node.children
            when {
                type == MarkdownElementTypes.MARKDOWN_FILE -> captureContent = true
                type == MarkdownElementTypes.PARAGRAPH -> {
                    if (captureContent) {
                        val content = node.getTextInNode(markdownText).trim()
                        if (content.isNotEmpty()) {
                            if (children.size == 1 && children.first().type == MarkdownElementTypes.IMAGE) {
                                paragraphs.appendLine(content)
                            } else {
                                paragraphs.appendLine(content.take(200))
                            }
                            captureContent = false
                        }
                    }
                }

                typeList.any {
                    it.name == type.name
                } -> {
                    if (paragraphs.length <= 100) {
                        val content = node.getTextInNode(markdownText).trim()
                        paragraphs.appendLine(content)
                    }
                    captureContent = false
                }
            }

            if (captureContent) {
                node.acceptChildren(this)
            }
        }
    })
    return paragraphs.toString().trim()
}

fun markdownElementTypes(): List<IElementType> {
    return listOf(
        MarkdownElementTypes.MARKDOWN_FILE,
        MarkdownElementTypes.UNORDERED_LIST,
        MarkdownElementTypes.ORDERED_LIST,
        MarkdownElementTypes.LIST_ITEM,
        MarkdownElementTypes.BLOCK_QUOTE,
        MarkdownElementTypes.CODE_FENCE,
        MarkdownElementTypes.CODE_BLOCK,
        MarkdownElementTypes.CODE_SPAN,
        MarkdownElementTypes.HTML_BLOCK,
        MarkdownElementTypes.PARAGRAPH,
        MarkdownElementTypes.EMPH,
        MarkdownElementTypes.STRONG,

        MarkdownElementTypes.LINK_DEFINITION,
        MarkdownElementTypes.LINK_LABEL,
        MarkdownElementTypes.LINK_DESTINATION,
        MarkdownElementTypes.LINK_TITLE,
        MarkdownElementTypes.LINK_TEXT,
        MarkdownElementTypes.INLINE_LINK,
        MarkdownElementTypes.FULL_REFERENCE_LINK,
        MarkdownElementTypes.SHORT_REFERENCE_LINK,
        MarkdownElementTypes.IMAGE,

        MarkdownElementTypes.AUTOLINK,

        MarkdownElementTypes.SETEXT_1,
        MarkdownElementTypes.SETEXT_2,

        MarkdownElementTypes.ATX_1,
        MarkdownElementTypes.ATX_2,
        MarkdownElementTypes.ATX_3,
        MarkdownElementTypes.ATX_4,
        MarkdownElementTypes.ATX_5,
        MarkdownElementTypes.ATX_6,
    )
}

fun extractMarkdownMediaLink(markdownText: String): MutableList<String> {
    val parsedTree = astNode(markdownText)

    val list = mutableListOf<String>()

    parsedTree.accept(object : Visitor {
        override fun visitNode(node: ASTNode) {
            when (node.type) {
                MarkdownElementTypes.IMAGE -> {
                    val imagePath = extractImageUrl(node, markdownText)
                    imagePath?.let { list.add(it) }
                }

                MarkdownElementTypes.CODE_FENCE -> {
                    val lang = getLang(node, markdownText)
                    if (lang == "object") {
                        try {
                            val content = readCodeFence(node, markdownText)
                            val obj = commonJson.decodeFromString<MarkdownObject>(content)
                            if (obj.name.isNotBlank() && obj.url.isBlank()) {
                                list.add(obj.name)
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            }

            node.acceptChildren(this)
        }
    })
    return list
}

fun extractImageUrl(node: ASTNode, markdownText: String): String? {
    // Extract the first level header content
    val markdownImage = node.getTextInNode(markdownText)

    // 正则表达式匹配 ![alt text](image path "title")
    val regex = Regex("""!\[([^]]*)]\(([^ )]+)(?:\s+"([^"]*)")?\)""")

    val matchResult = regex.find(markdownImage)
    val imagePath = if (matchResult != null) {
        matchResult.groupValues[2] // 提取图片路径
    } else {
        null
    }
    return imagePath
}

fun trimMarkdownUnusedContent(markdownText: String): String {
    val parsedTree = astNode(markdownText)
    return buildString {
        parsedTree.accept(object : Visitor {
            /**
             * 容量为3
             */
            val queue = mutableListOf<ASTNode>()

            override fun visitNode(node: ASTNode) {
                println("${node.type}")
                queue.add(node)
                if (queue.size > 3) {
                    queue.removeAt(0)
                }
                if (node.type == MarkdownElementTypes.CODE_FENCE) {
                    val lang = getLang(node, markdownText)
                    val content = readCodeFence(node, markdownText)
                    if (lang == "object") {
                        try {
                            val obj = commonJson.decodeFromString<MarkdownObject>(content)
                            appendLine("```object")
                            appendLine(commonJson.encodeToString<MarkdownObject>(obj))
                            append("```")
                        } catch (_: Exception) {
                        }
                    } else if (lang == "csa") {
                        content.trim().lineSequence().firstNotNullOfOrNull {
                            it.takeIf { it.isNotBlank() }?.trim()
                        }?.let {
                            appendLine("```csa")
                            appendLine(it)
                            append("```")
                        }
                    } else {
                        append(content)
                    }
                } else if (node.type == MarkdownElementTypes.MARKDOWN_FILE) {
                    node.acceptChildren(this)
                } else if (node.type == MarkdownTokenTypes.EOL) {
                    if (queue.any {
                            it.type != MarkdownTokenTypes.EOL
                        }) {
                        appendLine()
                    }
                } else {
                    val value = node.getTextInNode(markdownText)
                    println("else ${value.chars().toArray().joinToString(",")}")
                    append(value)
                }
            }
        })
    }.trim()
}

class MathContext(val content: String, val start: Int, val end: Int, val isInline: Boolean)

fun extractMath(markdownText: String): MutableList<MathContext> {
    val parsedTree = astNode(markdownText)
    val images = mutableListOf<MathContext>()
    parsedTree.accept(object : Visitor {
        override fun visitNode(node: ASTNode) {
            when (node.type) {
                GFMElementTypes.INLINE_MATH, GFMElementTypes.BLOCK_MATH -> {
                    val mathContent = readInlineMath(node, markdownText)
                    images.add(
                        MathContext(
                            mathContent,
                            node.startOffset,
                            node.endOffset,
                            node.type == GFMElementTypes.INLINE_MATH
                        )
                    )
                }

                MarkdownElementTypes.CODE_FENCE -> {
                    val lang = getLang(node, markdownText)
                    if (lang == "math") {
                        val content = readCodeFence(node, markdownText)
                        images.add(MathContext(content, node.startOffset, node.endOffset, false))
                    }
                }
            }

            node.acceptChildren(this)
        }
    })
    return images
}

fun astNode(markdownText: String): ASTNode {
    val flavour = GFMFlavourDescriptor()
    val parser = MarkdownParser(flavour)
    return parser.buildMarkdownTreeFromString(markdownText)
}

fun readCodeFence(node: ASTNode, content: String): String {
    val children = node.children
    val langOffset = children.indexOfFirst {
        it.type == MarkdownTokenTypes.FENCE_LANG
    }
    val start = children.subList(langOffset + 1, children.size).first {
        it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT
    }.startOffset
    val end = children.last {
        it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT
    }.endOffset
    return content.substring(start, end)
}

fun readInlineMath(node: ASTNode, content: String): String {
    val children = node.children
    val langOffset = children.first {
        it.type == GFMTokenTypes.DOLLAR
    }.endOffset
    val end = children.last {
        it.type == GFMTokenTypes.DOLLAR
    }.startOffset
    return content.substring(langOffset, end).removeSurrounding("`")
}

fun getLang(node: ASTNode, content: String): String {
    val children = node.children
    val langOffset = children.indexOfFirst {
        it.type == MarkdownTokenTypes.FENCE_LANG
    }
    return children.getOrNull(langOffset)?.getTextInNode(content).toString().lowercase()
}

@Serializable
data class MarkdownObject(
    val name: String = "",
    val url: String = "",
    val isPlayList: Boolean? = null,
    val contentType: String? = null,
    val cover: String? = null,
    val title: String? = null,
)

fun generateImageMarkdownContent(info: FileInfo): String =
    """![${info.name}](${info.name} "${info.name}")"""

fun generateObjectMarkdownContent(info: FileInfo): String = """```object
{"name": "${info.name}","contentType": "${info.contentType}"}
```"""

fun generateModelMarkdownContent(objectTuple: ObjectTuple): String = """```csa
/${
    when (objectTuple.objectType) {
        ObjectType.USER -> "user"
        ObjectType.COMMUNITY -> "community"
        ObjectType.ROOM -> "room"
        ObjectType.TOPIC -> "topic"
        else -> throw Exception("unknown object type ${objectTuple.objectType}")
    }
}/${objectTuple.objectId}
```"""
