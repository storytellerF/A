package com.storyteller_f.a.app.core.components

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import com.hrm.latex.renderer.Latex
import com.hrm.latex.renderer.model.LatexConfig
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import com.storyteller_f.shared.model.Dimension
import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.model.TopicContent
import com.storyteller_f.shared.model.TopicInfo
import com.storyteller_f.shared.utils.readInlineMath
import kotlinx.collections.immutable.ImmutableMap
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes

fun ASTNode.findChildOfTypeRecursive(type: IElementType): ASTNode? {
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

fun mathImageInlineContent(
    uri: String,
    mediaMap: ImmutableMap<String, Dimension?>,
    maxWidth: Dp,
    density: Density,
    isEmbed: Boolean,
    transformer: ImageTransformer
): InlineTextContent {
    val dimension = mediaMap[uri]
    if (maxWidth <= 10.dp || dimension == null) {
        return InlineTextContent(Placeholder(0.sp, 0.sp, PlaceholderVerticalAlign.Center)) {}
    }
    val imageWidth = pxToDp(dimension.width, density.density)
    val imageHeight = pxToDp(dimension.height, density.density)
    val width = minOf(maxWidth, imageWidth)
    val height = minOf((width.value / imageWidth.value) * imageHeight, if (isEmbed) 300.dp else width * 2)
    val recalculatedWidth = height.value * imageWidth / imageHeight.value
    return InlineTextContent(
        Placeholder(recalculatedWidth.value.sp, height.value.sp, PlaceholderVerticalAlign.Center)
    ) {
        CompositionLocalProvider(LocalInspectionMode provides false) {
            transformer.transform(uri)?.let { imageData ->
                CustomMarkdownImage(imageData)
            }
        }
    }
}

fun generateMathIfNeed(
    info: TopicInfo,
    textStyle: TextStyle,
    inlineCodeTextStyle: TextStyle,
    density: Density
): TopicInfo {
    // No longer pre-renders LaTeX to PNG images.
    // Math is now rendered directly via Compose Latex component at render time.
    return info
}

@Composable
fun customMarkdownTypography(colors: MarkdownColors): MarkdownTypography = markdownTypography(
    code = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, color = colors.text),
    inlineCode = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, color = colors.text)
)

@Composable
fun LatexInlineContent(
    tex: String,
    style: TextStyle
) {
    Latex(
        latex = tex,
        config = LatexConfig(
            fontSize = style.fontSize,
            color = style.color,
            darkColor = style.color
        )
    )
}

fun AnnotatedString.Builder.imageAnnotator(
    child: ASTNode,
    content: String,
    inlineContentMap: MutableMap<String, InlineTextContent>,
    dimensionMap: ImmutableMap<String, Dimension?>,
    maxWidth: Dp,
    density: Density,
    isEmbed: Boolean,
    imageTransformer: ImageTransformer,
    typography: MarkdownTypography,
    colors: MarkdownColors
): Boolean = when (child.type) {
    MarkdownElementTypes.IMAGE -> {
        val id = "image${child.startOffset}-${child.endOffset}"
        val name = child.findChildOfTypeRecursive(MarkdownElementTypes.LINK_DESTINATION)
            ?.getUnescapedTextInNode(content)
        if (name != null) {
            inlineContentMap[id] = mathImageInlineContent(
                uri = name,
                mediaMap = dimensionMap,
                maxWidth = maxWidth,
                density = density,
                isEmbed = isEmbed,
                transformer = imageTransformer
            )
            appendInlineContent(id, name)
            true
        } else {
            false
        }
    }

    GFMElementTypes.INLINE_MATH, GFMElementTypes.BLOCK_MATH -> {
        val tex = readInlineMath(child, content)
        val id = "math${child.startOffset}-${child.endOffset}"
        val style =
            if (child.type == GFMElementTypes.INLINE_MATH) {
                typography.inlineCode.copy(background = colors.inlineCodeBackground)
            } else {
                typography.code
            }

        // Estimate placeholder size based on font metrics
        val fontSizePx = with(density) { style.fontSize.toPx() }
        val lineHeight = fontSizePx * 1.5f
        val estimatedWidth = if (child.type == GFMElementTypes.INLINE_MATH) {
            fontSizePx * (1 + tex.length * 0.6f) // rough width estimate
        } else {
            with(density) { maxWidth.toPx() }
        }

        inlineContentMap[id] = InlineTextContent(
            Placeholder(
                width = (estimatedWidth / density.density).sp,
                height = (lineHeight / density.density).sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
            )
        ) {
            LatexInlineContent(tex, style)
        }

        if (child.type == GFMElementTypes.INLINE_MATH) {
            appendInlineContent(id, tex)
        } else {
            val paragraphStyle = ParagraphStyle()
            pushStyle(paragraphStyle)
            appendInlineContent(id, tex)
            pop()
        }
        true
    }

    else -> {
        false
    }
}

@Composable
fun <T> buildByMarkdown(block: @Composable (typography: MarkdownTypography, density: Density) -> T): T {
    val colors = markdownColor()
    val typography = customMarkdownTypography(colors)
    val density = LocalDensity.current
    return block(
        markdownTypography(
            code = typography.code,
            inlineCode = typography.inlineCode.copy(background = colors.inlineCodeBackground)
        ),
        density
    )
}
