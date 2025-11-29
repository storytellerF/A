package com.storyteller_f.a.app.core.components

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toArgb
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
import com.ashampoo.kim.Kim
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
import com.storyteller_f.shared.utils.extractMath
import com.storyteller_f.shared.utils.readInlineMath
import io.github.aakira.napier.Napier
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import kotlin.use

fun mathContent(child: ASTNode, content: String, style: TextStyle, density: Density): Pair<String, String> {
    val tex = readInlineMath(child, content)
    val size = textUnitToPx(style.fontSize, density)
    val backgroundColor = style.background.toArgb()
    val textColor = style.color.toArgb()
    val path = getTexPath(tex, backgroundColor, textColor, size)
    val id = "math${child.startOffset}-${child.endOffset}"
    val uri = "file://$path"
    return Pair(id, uri)
}

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

fun imageInlineContent(
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
    Napier.i {
        "generateMathIfNeed $info"
    }
    val content = info.content
    if (content !is TopicContent.Plain) {
        return info
    }
    val mathContexts = extractMath(content.plain)
    Napier.i {
        "mathContexts $mathContexts"
    }
    val fileInfos = mathContexts.mapNotNull { mathContext ->
        val style = if (mathContext.isInline) inlineCodeTextStyle else textStyle
        generateLatexImage(style, density, mathContext).getOrNull()?.let { path ->
            val filePath = path.toString()
            val dimension = SystemFileSystem.source(path).buffered().use {
                Kim.readMetadata(it.readByteArray())?.imageSize
            }?.let {
                val widthPx = it.width
                val heightPx = it.height
                Dimension(widthPx, heightPx)
            }
            FileInfo.EMPTY.copy(
                url = filePath,
                fullName = filePath,
                name = "file://$filePath",
                dimension = dimension
            )
        }
    }
    return info.copy(content = TopicContent.Plain(content.plain, content.fileInfos + fileInfos,))
}

@Composable
fun customMarkdownTypography(colors: MarkdownColors): MarkdownTypography = markdownTypography(
    code = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, color = colors.text),
    inlineCode = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, color = colors.text)
)

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
        val name =
            child.findChildOfTypeRecursive(MarkdownElementTypes.LINK_DESTINATION)
                ?.getUnescapedTextInNode(content)
        if (name != null) {
            inlineContentMap[id] = imageInlineContent(
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
        val style =
            if (child.type == GFMElementTypes.INLINE_MATH) {
                typography.inlineCode.copy(background = colors.inlineCodeBackground,)
            } else {
                typography.code
            }
        val (id, uri) = mathContent(child, content, style, density)
        inlineContentMap[id] = imageInlineContent(
            uri = uri,
            mediaMap = dimensionMap,
            maxWidth = maxWidth,
            density = density,
            isEmbed = isEmbed,
            transformer = imageTransformer
        )
        if (child.type == GFMElementTypes.INLINE_MATH) {
            appendInlineContent(id, uri)
        } else {
            val paragraphStyle = ParagraphStyle()
            pushStyle(paragraphStyle)
            appendInlineContent(id, uri)
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
