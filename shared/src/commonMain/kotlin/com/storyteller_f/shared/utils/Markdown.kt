package com.storyteller_f.shared.utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.accept
import org.intellij.markdown.ast.acceptChildren
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.ast.visitors.Visitor
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

fun extractMarkdownHeadline(markdownText: String): String {
    val parsedTree = astNode(markdownText)

    val paragraphs = StringBuilder()
    var headline = ""
    var captureContent = false

    val typeList = markdownTypes()

    parsedTree.accept(object : Visitor {
        override fun visitNode(node: ASTNode) {
            val type = node.type
            when {
                type == MarkdownElementTypes.MARKDOWN_FILE -> captureContent = true
                type == MarkdownElementTypes.ATX_1 -> {
                    // Extract the first level header content
                    headline = markdownText.substring(node.startOffset, node.endOffset).trim().take(50)
                }

                type == MarkdownElementTypes.PARAGRAPH -> {
                    if (captureContent) {
                        val content = markdownText.substring(node.startOffset, node.endOffset).trim()
                        if (content.isNotEmpty()) {
                            paragraphs.appendLine(content)
                        }
                    }
                }

                typeList.any {
                    type.name == it
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
    val typeList = markdownTypes()
    parsedTree.accept(object : Visitor {
        override fun visitNode(node: ASTNode) {
            val type = node.type
            val children = node.children
            when {
                type == MarkdownElementTypes.MARKDOWN_FILE -> captureContent = true
                type == MarkdownElementTypes.PARAGRAPH -> {
                    if (captureContent) {
                        val content = markdownText.substring(node.startOffset, node.endOffset).trim()
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
                    it == type.name
                } -> {
                    if (paragraphs.length <= 100) {
                        val content = markdownText.substring(node.startOffset, node.endOffset).trim()
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

fun markdownTypes(): List<String> {
    return MarkdownElementTypes::class.java.fields.mapNotNull {
        (it.get(MarkdownElementTypes) as? IElementType)?.name
    }
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
                        val content = readCodeFence(node, markdownText)
                        val obj = Json.decodeFromString<MarkdownObject>(content)
                        list.add(obj.name)
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
    val markdownImage = markdownText.substring(node.startOffset, node.endOffset)

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

fun astNode(markdownText: String): ASTNode {
    val flavour = CommonMarkFlavourDescriptor()
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

fun getLang(node: ASTNode, content: String): String {
    val children = node.children
    val langOffset = children.indexOfFirst {
        it.type == MarkdownTokenTypes.FENCE_LANG
    }
    return children.getOrNull(langOffset)?.getTextInNode(content).toString().lowercase()
}

@Serializable
data class MarkdownObject(val contentType: String, val name: String = "", val url: String = "")
