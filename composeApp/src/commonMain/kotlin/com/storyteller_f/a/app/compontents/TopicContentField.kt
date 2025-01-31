package com.storyteller_f.a.app.compontents

import a.composeapp.generated.resources.Res
import a.composeapp.generated.resources.permission_denied
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashampoo.kim.Kim
import com.ashampoo.kim.common.convertToPhotoMetadata
import com.mikepenz.markdown.compose.LocalMarkdownAnnotator
import com.mikepenz.markdown.compose.LocalMarkdownTypography
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownText
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownAnnotator
import com.mikepenz.markdown.model.markdownAnimations
import com.mikepenz.markdown.utils.MARKDOWN_TAG_IMAGE_URL
import com.mikepenz.markdown.utils.appendAutoLink
import com.mikepenz.markdown.utils.appendMarkdownLink
import com.mikepenz.markdown.utils.buildMarkdownAnnotatedString
import com.mikepenz.markdown.utils.codeSpanStyle
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import com.mikepenz.markdown.utils.linkTextSpanStyle
import com.storyteller_f.a.app.model.createMediaListViewModel
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.utils.readInlineMath
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.jetbrains.compose.resources.stringResource

@Composable
fun TopicContentField(
    topicInfo: TopicInfo,
) {
    when (val content = topicInfo.content) {
        is TopicContent.Plain -> {
            TopicContentFieldInternal(topicInfo.isPrivate, topicInfo, content.list, content.plain)
        }

        is TopicContent.Extracted -> {
            TopicContentFieldInternal(topicInfo.isPrivate, topicInfo, content.list, content.plain)
        }

        is TopicContent.DecryptFailed, is TopicContent.Encrypted -> {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.permission_denied))
            }
        }

        else -> {
        }
    }
}

@Composable
private fun TopicContentFieldInternal(
    isPrivate: Boolean,
    topicInfo: TopicInfo,
    rawMediaList: List<MediaInfo>,
    plain: String,
) {
    val mediaList = if (isPrivate) {
        val list = createMediaListViewModel(topicInfo.rootId, 0)
        val media by list.handler.data.collectAsState()
        media?.data.orEmpty()
    } else {
        rawMediaList
    }
    val mediaMap = mediaList.associateBy { it.item.noPrefixName }
    Markdown(
        plain,
        modifier = Modifier.fillMaxWidth().testTag("content"),
        colors = markdownColor(),
        typography = markdownTypography(),
        imageTransformer = CustomCoil3ImageTransformerImpl(mediaMap),
        components = markdownComponents(codeFence = {
            CustomCodeFence(it, mediaMap)
        }, codeBlock = { HighlightCodeBlock(it) }, paragraph = {
            MarkdownParagraph(it.content, it.node)
        }),
        animations = markdownAnimations(animateTextSize = {
            this
        })
    )
}

@Composable
fun MarkdownParagraph(
    content: String,
    node: ASTNode,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalMarkdownTypography.current.paragraph,
) {
    val annotator = LocalMarkdownAnnotator.current
    val linkTextSpanStyle = LocalMarkdownTypography.current.linkTextSpanStyle
    val codeSpanStyle = LocalMarkdownTypography.current.codeSpanStyle
    val density = LocalDensity.current
    val styledText by produceState<AnnotatedString?>(
        null,
        style,
        content,
        node,
        linkTextSpanStyle,
        codeSpanStyle,
        annotator
    ) {
        value = buildAnnotatedString {
            pushStyle(style.toSpanStyle())
            customBuildMarkdownAnnotatedString(
                content,
                node.children,
                linkTextSpanStyle,
                codeSpanStyle,
                annotator,
                density
            )
            pop()
        }
    }

    styledText?.let {
        MarkdownText(
            it,
            modifier = modifier,
            style = style,
        )
    }
}

suspend fun AnnotatedString.Builder.customBuildMarkdownAnnotatedString(
    content: String,
    children: List<ASTNode>,
    linkTextStyle: SpanStyle,
    codeStyle: SpanStyle,
    annotator: MarkdownAnnotator? = null,
    density: Density,
) {
    val annotate = annotator?.annotate
    var skipIfNext: Any? = null
    children.forEach { child ->
        if (skipIfNext == null || skipIfNext != child.type) {
            if (annotate == null || !annotate(content, child)) {
                val parentType = child.parent?.type

                when (child.type) {
                    // Element types
                    MarkdownElementTypes.PARAGRAPH -> buildMarkdownAnnotatedString(
                        content,
                        child,
                        linkTextStyle,
                        codeStyle,
                        annotator
                    )

                    MarkdownElementTypes.IMAGE -> child.findChildOfTypeRecursive(
                        MarkdownElementTypes.LINK_DESTINATION
                    )?.let {
                        appendInlineContent(MARKDOWN_TAG_IMAGE_URL, it.getUnescapedTextInNode(content))
                    }

                    MarkdownElementTypes.EMPH -> {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        buildMarkdownAnnotatedString(content, child, linkTextStyle, codeStyle, annotator)
                        pop()
                    }

                    MarkdownElementTypes.STRONG -> {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        buildMarkdownAnnotatedString(content, child, linkTextStyle, codeStyle, annotator)
                        pop()
                    }

                    GFMElementTypes.STRIKETHROUGH -> {
                        pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                        buildMarkdownAnnotatedString(content, child, linkTextStyle, codeStyle, annotator)
                        pop()
                    }

                    MarkdownElementTypes.CODE_SPAN -> {
                        pushStyle(codeStyle)
                        append(' ')
                        buildMarkdownAnnotatedString(
                            content,
                            child.children.innerList(),
                            linkTextStyle,
                            codeStyle,
                            annotator
                        )
                        append(' ')
                        pop()
                    }

                    MarkdownElementTypes.AUTOLINK -> appendAutoLink(content, child, linkTextStyle)
                    MarkdownElementTypes.INLINE_LINK -> appendMarkdownLink(
                        content,
                        child,
                        linkTextStyle,
                        codeStyle,
                        annotator
                    )

                    MarkdownElementTypes.SHORT_REFERENCE_LINK -> appendMarkdownLink(
                        content,
                        child,
                        linkTextStyle,
                        codeStyle,
                        annotator
                    )

                    MarkdownElementTypes.FULL_REFERENCE_LINK -> appendMarkdownLink(
                        content,
                        child,
                        linkTextStyle,
                        codeStyle,
                        annotator
                    )

                    // Token Types
                    MarkdownTokenTypes.TEXT -> append(child.getUnescapedTextInNode(content))
                    GFMTokenTypes.GFM_AUTOLINK -> if (child.parent == MarkdownElementTypes.LINK_TEXT) {
                        append(child.getUnescapedTextInNode(content))
                    } else appendAutoLink(content, child, linkTextStyle)

                    MarkdownTokenTypes.SINGLE_QUOTE -> append('\'')
                    MarkdownTokenTypes.DOUBLE_QUOTE -> append('\"')
                    MarkdownTokenTypes.LPAREN -> append('(')
                    MarkdownTokenTypes.RPAREN -> append(')')
                    MarkdownTokenTypes.LBRACKET -> append('[')
                    MarkdownTokenTypes.RBRACKET -> append(']')
                    MarkdownTokenTypes.LT -> append('<')
                    MarkdownTokenTypes.GT -> append('>')
                    MarkdownTokenTypes.COLON -> append(':')
                    MarkdownTokenTypes.EXCLAMATION_MARK -> append('!')
                    MarkdownTokenTypes.BACKTICK -> append('`')
                    MarkdownTokenTypes.HARD_LINE_BREAK -> append("\n\n")
                    MarkdownTokenTypes.EMPH -> if (parentType != MarkdownElementTypes.EMPH && parentType != MarkdownElementTypes.STRONG) append(
                        '*'
                    )

                    MarkdownTokenTypes.EOL -> append('\n')
                    MarkdownTokenTypes.WHITE_SPACE -> if (length > 0) {
                        append(' ')
                    }

                    MarkdownTokenTypes.BLOCK_QUOTE -> {
                        skipIfNext = MarkdownTokenTypes.WHITE_SPACE
                    }

                    GFMElementTypes.INLINE_MATH, GFMElementTypes.BLOCK_MATH -> {
                        val tex = readInlineMath(child, content)
                        val size = textUnitToPx(codeStyle.fontSize, density)
                        generateLatexImage(
                            if (child.type == GFMElementTypes.INLINE_MATH) codeStyle.background.toArgb() else 0,
                            codeStyle.color.toArgb(),
                            size,
                            tex
                        ).getOrNull()?.let { (r, path) ->
                            if (r) {
                                if (child.type == GFMElementTypes.BLOCK_MATH) {
                                    val style = ParagraphStyle()
                                    pushStyle(style)
                                    appendInlineContent(MARKDOWN_TAG_IMAGE_URL, "file:///$path")
                                    pop()
                                } else {
                                    appendInlineContent(MARKDOWN_TAG_IMAGE_URL, "file:///$path")
                                }

                            } else {
                                append(tex)
                            }
                        }

                    }
                }
            }
        } else {
            skipIfNext = null
        }
    }
}

internal fun ASTNode.findChildOfTypeRecursive(type: IElementType): ASTNode? {
    children.forEach {
        if (it.type == type) {
            return it
        } else {
            val found = it.findChildOfTypeRecursive(type)
            if (found != null) {
                return found
            }
        }
    }
    return null
}

internal fun List<ASTNode>.innerList(): List<ASTNode> = this.subList(1, this.size - 1)
