package com.storyteller_f.a.app.core.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import com.mikepenz.markdown.annotator.AnnotatorSettings
import com.mikepenz.markdown.annotator.annotatorSettings
import com.mikepenz.markdown.annotator.appendAutoLink
import com.mikepenz.markdown.annotator.appendMarkdownLink
import com.mikepenz.markdown.compose.LocalImageTransformer
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.LocalMarkdownExtendedSpans
import com.mikepenz.markdown.compose.LocalMarkdownTypography
import com.mikepenz.markdown.compose.elements.material.MarkdownBasicText
import com.mikepenz.markdown.compose.extendedspans.ExtendedSpans
import com.mikepenz.markdown.compose.extendedspans.drawBehind
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.utils.readInlineMath
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
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
    inlineContentMap: MutableMap<String, String>,
    maxWidth: Dp,
    textStyle: TextStyle,
) {
    val annotate = annotatorSettings.annotator.annotate
    var skipIfNext: Any? = null
    children.forEach { child ->
        if (skipIfNext == null || skipIfNext != child.type) {
            if (annotate == null || !annotate(content, child)) {
                val parentType = child.parent?.type

                processCustomMarkdownElement(
                    child,
                    content,
                    annotatorSettings,
                    inlineContentMap,
                    parentType,
                    density,
                    maxWidth,
                    textStyle
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
private fun AnnotatedString.Builder.processCustomMarkdownElement(
    child: ASTNode,
    content: String,
    annotatorSettings: AnnotatorSettings,
    inlineContentMap: MutableMap<String, String>,
    parentType: IElementType?,
    density: Density,
    width: Dp,
    textStyle: TextStyle,
): Any? {
    when (child.type) {
        // Element types
        MarkdownElementTypes.PARAGRAPH -> customBuildMarkdownAnnotatedString(
            content = content,
            children = child.children,
            annotatorSettings = annotatorSettings,
            density = density,
            inlineContentMap = inlineContentMap,
            width,
            textStyle
        )

        MarkdownElementTypes.IMAGE -> child.findChildOfTypeRecursive(MarkdownElementTypes.LINK_DESTINATION)
            ?.let {
                val id = "image${child.startOffset}-${child.endOffset}"
                val url = it.getUnescapedTextInNode(content)
                inlineContentMap[id] = url
                appendInlineContent(id, url)
            }

        MarkdownElementTypes.EMPH -> {
            pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
            customBuildMarkdownAnnotatedString(
                content,
                child.children,
                annotatorSettings,
                density,
                inlineContentMap,
                width,
                textStyle
            )
            pop()
        }

        MarkdownElementTypes.STRONG -> {
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            customBuildMarkdownAnnotatedString(
                content,
                child.children,
                annotatorSettings,
                density,
                inlineContentMap,
                width,
                textStyle
            )
            pop()
        }

        GFMElementTypes.STRIKETHROUGH -> {
            pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
            customBuildMarkdownAnnotatedString(
                content,
                child.children,
                annotatorSettings,
                density,
                inlineContentMap,
                width,
                textStyle
            )
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
                inlineContentMap,
                width,
                textStyle
            )
            append(' ')
            pop()
        }

        MarkdownElementTypes.AUTOLINK -> appendAutoLink(content, child, annotatorSettings)
        MarkdownElementTypes.INLINE_LINK -> appendMarkdownLink(content, child, annotatorSettings)
        MarkdownElementTypes.SHORT_REFERENCE_LINK -> appendMarkdownLink(
            content,
            child,
            annotatorSettings
        )

        MarkdownElementTypes.FULL_REFERENCE_LINK -> appendMarkdownLink(
            content,
            child,
            annotatorSettings
        )

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

        GFMElementTypes.INLINE_MATH -> {
            val style = getInlineMathTextStyle(annotatorSettings, textStyle.color)
            val (id, url) = mathContent(child, content, style, density)
            inlineContentMap[id] = url
            appendInlineContent(id, url)
        }

        GFMElementTypes.BLOCK_MATH -> {
            val style = getBlockMathTextStyle(textStyle)
            val (id, url) = mathContent(child, content, style, density)
            inlineContentMap[id] = url
            val paragraphStyle = ParagraphStyle()
            pushStyle(paragraphStyle)
            appendInlineContent(id, url)
            pop()
        }
    }
    return null
}

private fun mathContent(
    child: ASTNode,
    content: String,
    style: TextStyle,
    density: Density
): Pair<String, String> {
    val tex = readInlineMath(child, content)
    val size = textUnitToPx(style.fontSize, density)
    val backgroundColor = style.background.toArgb()
    val textColor = style.color.toArgb()
    val path = getTexPath(tex, backgroundColor, textColor, size)
    val id = "math${child.startOffset}-${child.endOffset}"
    val url = "file://$path"
    return Pair(id, url)
}

fun getInlineMathTextStyle(annotatorSettings: AnnotatorSettings, contentColor: Color): TextStyle {
    return TextStyle(
        color = contentColor,
        background = annotatorSettings.codeSpanStyle.background,
        fontSize = annotatorSettings.codeSpanStyle.fontSize
    )
}

fun getBlockMathTextStyle(textStyle: TextStyle): TextStyle {
    return TextStyle(
        color = textStyle.color,
        background = Color.Transparent,
        fontSize = textStyle.fontSize
    )
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
    inlineContentMap: ImmutableMap<String, InlineTextContent>,
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
        inlineContentMap
    )
}

@Composable
private fun CustomMarkdownTextInternal(
    content: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalMarkdownTypography.current.text,
    onTextLayout: (TextLayoutResult) -> Unit,
    inlineContentMap: ImmutableMap<String, InlineTextContent>,
) {
    val baseColor = LocalMarkdownColors.current.text
    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }

    MarkdownBasicText(
        text = content,
        modifier = modifier,
        style = style,
        color = baseColor,
        inlineContent = inlineContentMap,
        onTextLayout = {
            layoutResult.value = it
            onTextLayout.invoke(it)
        }
    )
}

@Composable
fun CustomMarkdownParagraph(
    content: String,
    node: ASTNode,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalMarkdownTypography.current.paragraph,
    mediaMap: ImmutableMap<String, FileInfo>,
    isEmbed: Boolean,
) {
    val density = LocalDensity.current
    val annotatorSettings = annotatorSettings()
    val transformer = LocalImageTransformer.current
    val textStyle = LocalTextStyle.current.copy(color = LocalContentColor.current)
    BoxWithConstraints {
        val width = this.maxWidth
        val (styledText, inlineContentMap) = remember(mediaMap, content) {
            val inlineContentMap = mutableMapOf<String, String>()
            val text = buildAnnotatedString {
                pushStyle(style.toSpanStyle())
                customBuildMarkdownAnnotatedString(
                    content,
                    node.children,
                    annotatorSettings,
                    density,
                    inlineContentMap,
                    width,
                    textStyle
                )
                pop()
            }
            val immutableMap = buildInlineTextContentFromMap(
                inlineContentMap,
                mediaMap,
                width,
                isEmbed,
                density,
                transformer
            )
            text to immutableMap
        }

        CustomMarkdownText(
            styledText,
            modifier = modifier,
            style = style,
            inlineContentMap = inlineContentMap
        )
    }
}

private fun buildInlineTextContentFromMap(
    inlineContentMap: MutableMap<String, String>,
    mediaMap: ImmutableMap<String, FileInfo>,
    width: Dp,
    isEmbed: Boolean,
    density: Density,
    transformer: ImageTransformer,
): ImmutableMap<String, InlineTextContent> {
    return inlineContentMap.mapValues { (_, value) ->
        val dimension = getImageDimension(value, mediaMap)
        if (width > 10.dp && dimension != null) {
            val imageWidth = pxToDp(dimension.width, density.density)
            val imageHeight = pxToDp(dimension.height, density.density)
            val width = minOf(width, imageWidth)
            val height = minOf(
                (width.value / imageWidth.value) * imageHeight,
                if (isEmbed) 300.dp else width * 2
            )
            val recalculatedWidth = height.value * imageWidth / imageHeight.value
            InlineTextContent(
                Placeholder(
                    recalculatedWidth.value.sp,
                    height.value.sp,
                    PlaceholderVerticalAlign.Center
                )
            ) {
                CompositionLocalProvider(LocalInspectionMode provides false) {
                    transformer.transform(value)?.let { imageData ->
                        CustomMarkdownImage(imageData)
                    }
                }
            }
        } else {
            InlineTextContent(Placeholder(0.sp, 0.sp, PlaceholderVerticalAlign.Center)) {}
        }
    }.toImmutableMap()
}
