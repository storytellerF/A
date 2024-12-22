package com.storyteller_f.shared.utils

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.accept
import org.intellij.markdown.ast.visitors.Visitor
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

fun extractMarkdownHeadline(markdownText: String): String {
    val parsedTree = astNode(markdownText)

    val paragraphs = StringBuilder()
    var headline = ""
    var captureContent = false

    parsedTree.accept(object : Visitor {
        override fun visitNode(node: ASTNode) {
            when {
                node.type == MarkdownElementTypes.ATX_1 -> {
                    // Extract the first level header content
                    headline = markdownText.substring(node.startOffset, node.endOffset).trim().take(50)
                    captureContent = true
                }

                node.type == MarkdownElementTypes.PARAGRAPH -> {
                    if (captureContent) {
                        val content = markdownText.substring(node.startOffset, node.endOffset).trim()
                        if (content.isNotEmpty()) {
                            paragraphs.appendLine(content)
                        }
                    }
                }

                MarkdownElementTypes::class.java.fields.any {
                    node.type.name == it.name
                } -> {
                    // Stop capturing when encountering the first second-level header
                    captureContent = false
                }
            }

            node.children.forEach { it.accept(this) }
        }
    })
    return if (headline.isNotBlank()) {
        val trim = "$headline\n${paragraphs.toString().trim().take(150)}"
        trim
    } else {
        extractHeadParagraph(markdownText)
    }
}

fun extractHeadParagraph(
    markdownText: String,
): String {
    val paragraphs = StringBuilder()
    val parsedTree = astNode(markdownText)
    var captureContent1 = false
    parsedTree.accept(object : Visitor {
        override fun visitNode(node: ASTNode) {
            println(node.type)
            when {
                node.type == MarkdownElementTypes.MARKDOWN_FILE -> captureContent1 = true
                node.type == MarkdownElementTypes.PARAGRAPH -> {
                    if (captureContent1) {
                        val content = markdownText.substring(node.startOffset, node.endOffset).trim()
                        if (content.isNotEmpty()) {
                            if (node.children.size == 1 && node.children.first().type == MarkdownElementTypes.IMAGE) {
                                paragraphs.appendLine(content)
                            } else {
                                paragraphs.appendLine(content.take(200))
                            }
                        }
                    }
                }

                MarkdownElementTypes::class.java.fields.any {
                    node.type.name == it.name
                } -> {
                    if (paragraphs.length <= 100) {
                        val content = markdownText.substring(node.startOffset, node.endOffset).trim()
                        paragraphs.appendLine(content)
                    }
                    // Stop capturing when encountering the first second-level header
                    captureContent1 = false
                }
            }

            node.children.forEach { it.accept(this) }
        }
    })
    return paragraphs.toString().trim()
}

fun extractMarkdownMediaLink(markdownText: String): MutableList<String> {
    val parsedTree = astNode(markdownText)

    val list = mutableListOf<String>()

    parsedTree.accept(object : Visitor {
        override fun visitNode(node: ASTNode) {
            when (node.type) {
                MarkdownElementTypes.IMAGE -> {
                    // Extract the first level header content
                    val markdownImage = markdownText.substring(node.startOffset, node.endOffset)

                    // 正则表达式匹配 ![alt text](image path "title")
                    val regex = Regex("""!\[([^]]*)]\(([^ )]+)(?:\s+"([^"]*)")?\)""")

                    val matchResult = regex.find(markdownImage)
                    if (matchResult != null) {
                        val imagePath = matchResult.groupValues[2] // 提取图片路径
                        list.add(imagePath)
                    }
                }
            }

            node.children.forEach { it.accept(this) }
        }
    })
    return list
}

private fun astNode(markdownText: String): ASTNode {
    val flavour = CommonMarkFlavourDescriptor()
    val parser = MarkdownParser(flavour)
    val parsedTree = parser.buildMarkdownTreeFromString(markdownText)
    return parsedTree
}
