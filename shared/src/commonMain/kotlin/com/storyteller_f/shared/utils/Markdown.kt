package com.storyteller_f.shared.utils

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.accept
import org.intellij.markdown.ast.visitors.Visitor
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

fun extractMarkdownHeadline(markdownText: String): String {
    val flavour = CommonMarkFlavourDescriptor()
    val parser = MarkdownParser(flavour)
    val parsedTree = parser.buildMarkdownTreeFromString(markdownText)

    val result = StringBuilder()
    var captureContent = false

    parsedTree.accept(object : Visitor {
        override fun visitNode(node: ASTNode) {
            when (node.type) {
                MarkdownElementTypes.ATX_1 -> {
                    // Extract the first level header content
                    result.appendLine(markdownText.substring(node.startOffset, node.endOffset))
                    captureContent = true
                }

                MarkdownElementTypes.ATX_2 -> {
                    // Stop capturing when encountering the first second-level header
                    captureContent = false
                }

                MarkdownElementTypes.PARAGRAPH -> {
                    if (captureContent) {
                        val content = markdownText.substring(node.startOffset, node.endOffset).trim()
                        if (content.isNotEmpty()) {
                            result.appendLine(content)
                        }
                    }
                }
            }

            node.children.forEach { it.accept(this) }
        }
    })
    val trim = result.toString().trim()
    return trim.ifEmpty {
        markdownText.take(100)
    }
}