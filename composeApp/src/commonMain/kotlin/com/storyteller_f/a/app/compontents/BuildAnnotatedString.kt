package com.storyteller_f.a.app.compontents

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
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
import com.mikepenz.markdown.annotator.AnnotatorSettings
import com.mikepenz.markdown.annotator.appendAutoLink
import com.mikepenz.markdown.annotator.appendMarkdownLink
import com.mikepenz.markdown.compose.LocalImageTransformer
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.LocalMarkdownExtendedSpans
import com.mikepenz.markdown.compose.LocalMarkdownTypography
import com.mikepenz.markdown.compose.extendedspans.ExtendedSpans
import com.mikepenz.markdown.compose.extendedspans.drawBehind
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import com.storyteller_f.shared.model.MediaInfo
import com.storyteller_f.shared.utils.readInlineMath
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
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

fun AnnotatedString.Builder.customBuildMarkdownAnnotatedString(
    content: String,
    children: List<ASTNode>,
    annotatorSettings: AnnotatorSettings,
    density: Density,
    inlineContentMap: MutableMap<String, String>
) {
    val annotate = annotatorSettings.annotator?.annotate
    var skipIfNext: Any? = null
    children.forEach { child ->
        if (skipIfNext == null || skipIfNext != child.type) {
            if (annotate == null || !annotate(content, child)) {
                val parentType = child.parent?.type

                processCustomMarkdown(
                    child,
                    content,
                    annotatorSettings,
                    inlineContentMap,
                    parentType,
                    density
                )?.let {
                    skipIfNext = it
                }
            }
        } else {
            skipIfNext = null
        }
    }
}

@Suppress("LongMethod")
private fun AnnotatedString.Builder.processCustomMarkdown(
    child: ASTNode,
    content: String,
    annotatorSettings: AnnotatorSettings,
    inlineContentMap: MutableMap<String, String>,
    parentType: IElementType?,
    density: Density
): Any? {
    when (child.type) {
        // Element types
        MarkdownElementTypes.PARAGRAPH -> customBuildMarkdownAnnotatedString(
            content = content,
            node = child,
            annotatorSettings = annotatorSettings,
            density,
            inlineContentMap
        )

        MarkdownElementTypes.IMAGE -> child.findChildOfTypeRecursive(MarkdownElementTypes.LINK_DESTINATION)?.let {
            val id = "image${child.startOffset}-${child.endOffset}"
            val url = it.getUnescapedTextInNode(content)
            inlineContentMap[id] = url
            appendInlineContent(id, url)
        }

        MarkdownElementTypes.EMPH -> {
            pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
            customBuildMarkdownAnnotatedString(content, child, annotatorSettings, density, inlineContentMap)
            pop()
        }

        MarkdownElementTypes.STRONG -> {
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            customBuildMarkdownAnnotatedString(content, child, annotatorSettings, density, inlineContentMap)
            pop()
        }

        GFMElementTypes.STRIKETHROUGH -> {
            pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
            customBuildMarkdownAnnotatedString(content, child, annotatorSettings, density, inlineContentMap)
            pop()
        }

        MarkdownElementTypes.CODE_SPAN -> {
            pushStyle(annotatorSettings.codeSpanStyle)
            append(' ')
            customBuildMarkdownAnnotatedString(
                content,
                child.children.innerList(),
                annotatorSettings,
                density,
                inlineContentMap
            )
            append(' ')
            pop()
        }

        MarkdownElementTypes.AUTOLINK -> appendAutoLink(content, child, annotatorSettings)
        MarkdownElementTypes.INLINE_LINK -> appendMarkdownLink(content, child, annotatorSettings)
        MarkdownElementTypes.SHORT_REFERENCE_LINK -> appendMarkdownLink(content, child, annotatorSettings)
        MarkdownElementTypes.FULL_REFERENCE_LINK -> appendMarkdownLink(content, child, annotatorSettings)

        // Token Types
        MarkdownTokenTypes.TEXT -> append(child.getUnescapedTextInNode(content))
        GFMTokenTypes.GFM_AUTOLINK -> if (child.parent == MarkdownElementTypes.LINK_TEXT) {
            append(child.getUnescapedTextInNode(content))
        } else {
            appendAutoLink(content, child, annotatorSettings)
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
        MarkdownTokenTypes.HARD_LINE_BREAK -> {
            append('\n')
            return MarkdownTokenTypes.EOL
        }

        MarkdownTokenTypes.EMPH -> when {
            parentType != MarkdownElementTypes.EMPH && parentType != MarkdownElementTypes.STRONG -> {
                append('*')
            }
        }

        MarkdownTokenTypes.EOL -> append('\n')
        MarkdownTokenTypes.WHITE_SPACE -> if (length > 0) append(' ')
        MarkdownTokenTypes.BLOCK_QUOTE -> {
            return MarkdownTokenTypes.WHITE_SPACE
        }

        GFMElementTypes.INLINE_MATH, GFMElementTypes.BLOCK_MATH -> {
            appendMathContent(child, content, density, inlineContentMap)
        }
    }
    return null
}

private fun AnnotatedString.Builder.appendMathContent(
    child: ASTNode,
    content: String,
    density: Density,
    inlineContentMap: MutableMap<String, String>
) {
    val tex = readInlineMath(child, content)
    val size = textUnitToPx(13.sp, density)
    generateLatexImage(
        if (child.type == GFMElementTypes.INLINE_MATH) Color.LightGray.toArgb() else 0,
        Color.Black.toArgb(),
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
private fun buildInlineContentMap(
    inlineContentMap: ImmutableMap<String, String>,
    maxWidth: Int,
    mediaMap: Map<String, MediaInfo>,
    transformer: ImageTransformer,
    isEmbed: Boolean
): ImmutableMap<String, InlineTextContent> {
    val dimensionMap = buildInlineContentDimensions(inlineContentMap, mediaMap)
    val density = LocalDensity.current.density

    return remember(inlineContentMap, maxWidth, mediaMap) {
        buildMap {
            dimensionMap.forEach { (key, pair) ->
                if (maxWidth == 0) {
                    InlineTextContent(Placeholder(0.sp, 0.sp, PlaceholderVerticalAlign.Bottom)) {}
                } else if (pair != null) {
                    val width = minOf(maxWidth, pair.first)
                    val height =
                        minOf(width * pair.second / pair.first, if (isEmbed) dpToPx(300.dp, density) else width * 2)
                    val recalculatedWidth = height * pair.first / pair.second
                    put(
                        key, InlineTextContent(
                            Placeholder(
                                pxToSp(recalculatedWidth, density),
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
                        })
                }
            }
        }.toImmutableMap()
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

fun AnnotatedString.Builder.customBuildMarkdownAnnotatedString(
    content: String,
    node: ASTNode,
    annotatorSettings: AnnotatorSettings,
    density: Density,
    inlineContentMap: MutableMap<String, String>
) = customBuildMarkdownAnnotatedString(
    content,
    node.children,
    annotatorSettings,
    density,
    inlineContentMap
)

@Composable
fun CustomMarkdownText(
    content: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalMarkdownTypography.current.text,
    extendedSpans: ExtendedSpans? = LocalMarkdownExtendedSpans.current.extendedSpans?.invoke(),
    inlineContentMap: ImmutableMap<String, String>,
    mediaMap: ImmutableMap<String, MediaInfo>,
    isEmbed: Boolean
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

    CustomMarkdownTextInternal(
        extendedStyledText,
        extendedModifier,
        style,
        onTextLayout,
        inlineContentMap,
        mediaMap,
        isEmbed
    )
}

@Composable
private fun CustomMarkdownTextInternal(
    content: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalMarkdownTypography.current.text,
    onTextLayout: (TextLayoutResult) -> Unit,
    inlineContentMap: ImmutableMap<String, String>,
    mediaMap: ImmutableMap<String, MediaInfo>,
    isEmbed: Boolean
) {
    val baseColor = LocalMarkdownColors.current.text
    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }

    val transformer = LocalImageTransformer.current

    BoxWithConstraints {
        val width = convertDpToPx(this.maxWidth)
        val inlineTextContentMap = buildInlineContentMap(inlineContentMap, width, mediaMap, transformer, isEmbed)
        CustomMarkdownBasicText(
            text = content,
            modifier = modifier,
            style = style,
            color = baseColor,
            inlineContent = inlineTextContentMap,
            onTextLayout = {
                layoutResult.value = it
                onTextLayout.invoke(it)
            }
        )
    }
}

@Composable
fun CustomMarkdownBasicText(
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
    inlineContent: ImmutableMap<String, InlineTextContent> = mapOf<String, InlineTextContent>().toImmutableMap(),
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
