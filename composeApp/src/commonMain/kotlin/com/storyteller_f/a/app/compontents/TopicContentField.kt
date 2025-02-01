package com.storyteller_f.a.app.compontents

import a.composeapp.generated.resources.Res
import a.composeapp.generated.resources.permission_denied
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashampoo.kim.Kim
import com.ashampoo.kim.common.convertToPhotoMetadata
import com.mikepenz.markdown.compose.LocalImageTransformer
import com.mikepenz.markdown.compose.LocalMarkdownAnnotator
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.LocalMarkdownExtendedSpans
import com.mikepenz.markdown.compose.LocalMarkdownTypography
import com.mikepenz.markdown.compose.LocalReferenceLinkHandler
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.extendedspans.ExtendedSpans
import com.mikepenz.markdown.compose.extendedspans.drawBehind
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.MarkdownAnnotator
import com.mikepenz.markdown.utils.MARKDOWN_TAG_URL
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
import kotlinx.io.files.Path
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
            CustomMarkdownParagraph(it.content, it.node, mediaMap = mediaMap)
        })
    )
}

@Composable
fun CustomMarkdownParagraph(
    content: String,
    node: ASTNode,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalMarkdownTypography.current.paragraph,
    mediaMap: Map<String, MediaInfo>
) {
    val annotator = LocalMarkdownAnnotator.current
    val linkTextSpanStyle = LocalMarkdownTypography.current.linkTextSpanStyle
    val codeSpanStyle = LocalMarkdownTypography.current.codeSpanStyle
    val density = LocalDensity.current
    val inlineContentMap = remember {
        mutableMapOf<String, String>()
    }
    val styledText = remember(
        style,
        content,
        node.children,
        linkTextSpanStyle,
        codeSpanStyle,
        annotator,
        inlineContentMap,
    ) {
        buildAnnotatedString {
            pushStyle(style.toSpanStyle())
            customBuildMarkdownAnnotatedString(
                content,
                node.children,
                linkTextSpanStyle,
                codeSpanStyle,
                annotator,
                density,
                inlineContentMap
            )
            pop()
        }
    }

    CustomMarkdownText(
        styledText,
        modifier = modifier,
        style = style,
        inlineContentMap = inlineContentMap,
        mediaMap = mediaMap
    )
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
fun AnnotatedString.Builder.customBuildMarkdownAnnotatedString(
    content: String,
    children: List<ASTNode>,
    linkTextStyle: SpanStyle,
    codeStyle: SpanStyle,
    annotator: MarkdownAnnotator? = null,
    density: Density,
    inlineContentMap: MutableMap<String, String>
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
                        val id = "image${child.startOffset}-${child.endOffset}"
                        val url = it.getUnescapedTextInNode(content)
                        inlineContentMap[id] = url
                        appendInlineContent(id, url)
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
                    } else {
                        appendAutoLink(content, child, linkTextStyle)
                    }

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
                    MarkdownTokenTypes.EMPH -> if (parentType != MarkdownElementTypes.EMPH &&
                        parentType != MarkdownElementTypes.STRONG
                    ) {
                        append(
                            '*'
                        )
                    }

                    MarkdownTokenTypes.EOL -> append('\n')
                    MarkdownTokenTypes.WHITE_SPACE -> if (length > 0) {
                        append(' ')
                    }

                    MarkdownTokenTypes.BLOCK_QUOTE -> {
                        skipIfNext = MarkdownTokenTypes.WHITE_SPACE
                    }

                    GFMElementTypes.INLINE_MATH, GFMElementTypes.BLOCK_MATH -> {
                        appendMathContent(child, content, codeStyle, density, inlineContentMap)
                    }
                }
            }
        } else {
            skipIfNext = null
        }
    }
}

private fun AnnotatedString.Builder.appendMathContent(
    child: ASTNode,
    content: String,
    codeStyle: SpanStyle,
    density: Density,
    inlineContentMap: MutableMap<String, String>
) {
    val tex = readInlineMath(child, content)
    val size = textUnitToPx(codeStyle.fontSize, density)
    generateLatexImage(
        if (child.type == GFMElementTypes.INLINE_MATH) codeStyle.background.toArgb() else 0,
        codeStyle.color.toArgb(),
        size,
        tex
    ).getOrNull()?.let { (r, path) ->
        val id = "math${child.startOffset}-${child.endOffset}"
        val url = "file:///$path"
        inlineContentMap[id] = url
        when {
            !r -> append(tex)
            child.type == GFMElementTypes.BLOCK_MATH -> {
                val style = ParagraphStyle()
                pushStyle(style)
                appendInlineContent(id, url)
                pop()
            }

            else -> {
                appendInlineContent(id, url)
            }
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

@Composable
fun CustomMarkdownText(
    content: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalMarkdownTypography.current.text,
    extendedSpans: ExtendedSpans? = LocalMarkdownExtendedSpans.current.extendedSpans?.invoke(),
    inlineContentMap: Map<String, String>,
    mediaMap: Map<String, MediaInfo>
) {
    // extend the annotated string with `extended-spans` styles if provided
    val extendedStyledText = if (extendedSpans != null) {
        remember(content) {
            extendedSpans.extend(content)
        }
    } else {
        content
    }

    // forward the `onTextLayout` to `extended-spans` if provided
    val onTextLayout: (TextLayoutResult) -> Unit = if (extendedSpans != null) {
        { result ->
            extendedSpans.onTextLayout(result)
        }
    } else {
        {}
    }

    // call drawBehind with the `extended-spans` if provided
    val extendedModifier = if (extendedSpans != null) {
        modifier.drawBehind(extendedSpans)
    } else {
        modifier
    }

    CustomMarkdownText(extendedStyledText, extendedModifier, style, onTextLayout, inlineContentMap, mediaMap)
}

@Composable
fun CustomMarkdownText(
    content: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalMarkdownTypography.current.text,
    onTextLayout: (TextLayoutResult) -> Unit,
    inlineContentMap: Map<String, String>,
    mediaMap: Map<String, MediaInfo>
) {
    val uriHandler = LocalUriHandler.current
    val referenceLinkHandler = LocalReferenceLinkHandler.current
    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }

    val hasUrl = content.getStringAnnotations(MARKDOWN_TAG_URL, 0, content.length).any()
    val textModifier = if (hasUrl) {
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val pointer = awaitFirstDown()
                val pos = pointer.position // current position

                val foundReference = layoutResult.value?.let { layoutResult ->
                    val position = layoutResult.getOffsetForPosition(pos)
                    content.getStringAnnotations(MARKDOWN_TAG_URL, position, position).reversed().firstOrNull()
                        ?.let { referenceLinkHandler.find(it.item) }
                }

                if (foundReference != null) {
                    pointer.consume() // consume if we clicked on a link

                    val up = waitForUpOrCancellation()
                    if (up != null) {
                        up.consume()

                        // wait for finger up to navigate to the link
                        try {
                            uriHandler.openUri(foundReference)
                        } catch (_: Throwable) {
                            println("Could not open the provided url: $foundReference")
                        }
                    }
                }
            }
        }
    } else {
        modifier
    }

    val transformer = LocalImageTransformer.current

    BoxWithConstraints {
        val width = convertDpToPx(maxWidth)
        val inlineTextContentMap = buildInlineContentMap(inlineContentMap, width, mediaMap, transformer)
        MarkdownBasicText1(
            text = content,
            modifier = textModifier,
            style = style,
            color = LocalMarkdownColors.current.text,
            inlineContent = inlineTextContentMap,
            onTextLayout = {
                layoutResult.value = it
                onTextLayout.invoke(it)
            }
        )
    }
}

@Composable
private fun buildInlineContentMap(
    inlineContentMap: Map<String, String>,
    width: Int,
    mediaMap: Map<String, MediaInfo>,
    transformer: ImageTransformer
): Map<String, InlineTextContent> {
    val dimensionMap = buildInlineContentDimensions(inlineContentMap, mediaMap)
    val density = LocalDensity.current.density

    return remember {
        val map = mutableMapOf<String, InlineTextContent>()
        dimensionMap.forEach { (key, pair) ->
            if (width == 0) {
                InlineTextContent(Placeholder(0.sp, 0.sp, PlaceholderVerticalAlign.Bottom)) {}
            } else if (pair != null) {
                val width = minOf(width, pair.first)
                val height = width * pair.second / pair.first
                map[key] = InlineTextContent(
                    Placeholder(
                        pxToSp(width, density),
                        pxToSp(height, density),
                        PlaceholderVerticalAlign.Bottom
                    )
                ) {
                    val value = inlineContentMap[key]
                    transformer.transform(value.orEmpty())?.let { imageData ->
                        Image(
                            painter = imageData.painter,
                            contentDescription = imageData.contentDescription,
                            modifier = imageData.modifier,
                            alignment = imageData.alignment,
                            contentScale = imageData.contentScale,
                            alpha = imageData.alpha,
                            colorFilter = imageData.colorFilter
                        )
                    }
                }
            }
        }
        map
    }
}

@Composable
private fun buildInlineContentDimensions(
    inlineContentMap: Map<String, String>,
    mediaMap: Map<String, MediaInfo>
): Map<String, Pair<Int, Int>?> = remember(inlineContentMap, mediaMap) {
    inlineContentMap.mapValues { (_, value) ->
        if (value.startsWith("file:///")) {
            val metadata = SystemFileSystem.source(Path(value.substring(7))).buffered().use {
                Kim.readMetadata(it.readByteArray())?.convertToPhotoMetadata()
            }
            if (metadata != null) {
                val widthPx = metadata.widthPx
                val heightPx = metadata.heightPx
                if (widthPx != null && heightPx != null) {
                    widthPx to heightPx
                } else {
                    null
                }
            } else {
                null
            }
        } else {
            val info = mediaMap[value]
            val dimension = info?.dimension
            if (dimension != null) {
                dimension.width to dimension.height
            } else {
                null
            }
        }
    }
}

@Composable
fun MarkdownBasicText1(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign = TextAlign.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    // Note: This component is ported over from Material2 Text - to remove the dependency on Material
    val overrideColorOrUnspecified = if (color.isSpecified) {
        color
    } else if (style.color.isSpecified) {
        style.color
    } else {
        LocalMarkdownColors.current.text
    }

    BasicText(
        text = text,
        modifier = modifier,
        style = style.merge(
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            lineHeight = lineHeight,
            fontFamily = fontFamily,
            textDecoration = textDecoration,
            fontStyle = fontStyle,
            letterSpacing = letterSpacing
        ),
        onTextLayout = onTextLayout,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        inlineContent = inlineContent,
        color = { overrideColorOrUnspecified }
    )
}
