package com.storyteller_f.a.app.compontents

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.annotator.AnnotatorSettings
import com.mikepenz.markdown.annotator.appendAutoLink
import com.mikepenz.markdown.annotator.appendMarkdownLink
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.LocalMarkdownExtendedSpans
import com.mikepenz.markdown.compose.LocalMarkdownTypography
import com.mikepenz.markdown.compose.elements.material.MarkdownBasicText
import com.mikepenz.markdown.compose.extendedspans.ExtendedSpans
import com.mikepenz.markdown.compose.extendedspans.drawBehind
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import com.storyteller_f.shared.utils.readInlineMath
import kotlinx.collections.immutable.ImmutableMap
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
                    maxWidth
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
): Any? {
    when (child.type) {
        // Element types
        MarkdownElementTypes.PARAGRAPH -> customBuildMarkdownAnnotatedString(
            content = content,
            children = child.children,
            annotatorSettings = annotatorSettings,
            density = density,
            inlineContentMap = inlineContentMap,
            width
        )

        MarkdownElementTypes.IMAGE -> child.findChildOfTypeRecursive(MarkdownElementTypes.LINK_DESTINATION)?.let {
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
                width
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
                width
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
                width
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
                width
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
    inlineContentMap: MutableMap<String, String>,
) {
    val tex = readInlineMath(child, content)
    val size = textUnitToPx(13.sp, density)
    val path = generateLatexImage(
        if (child.type == GFMElementTypes.INLINE_MATH) Color.LightGray.toArgb() else 0,
        Color.Black.toArgb(),
        size,
        tex
    ).getOrNull()
    if (path == null) {
        append(tex)
        return
    }
    val id = "math${child.startOffset}-${child.endOffset}"
    val url = "file:///$path"
    inlineContentMap[id] = url
    if (child.type != GFMElementTypes.BLOCK_MATH) {
        appendInlineContent(id, url)
        return
    }
    val style = ParagraphStyle()
    pushStyle(style)
    appendInlineContent(id, url)
    pop()
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
