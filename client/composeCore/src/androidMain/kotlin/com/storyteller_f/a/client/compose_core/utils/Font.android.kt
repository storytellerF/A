/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.client.compose_core.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import java.io.File

actual fun loadFontFromLocal(path: String): FontFamily? {
    val file = File(path)
    val fonts =
        file.listFiles()?.mapNotNull(::loadFont) ?: return null
    if (fonts.isEmpty()) return null
    return FontFamily(fonts)
}

private fun loadFont(file: File): Font? {
    val (weight, style) =
        when (file.nameWithoutExtension) {
            "Bold" -> FontWeight.Bold to FontStyle.Normal
            "BoldItalic" -> FontWeight.Bold to FontStyle.Italic
            "ExtraBold" -> FontWeight.ExtraBold to FontStyle.Normal
            "ExtraBoldItalic" -> FontWeight.ExtraBold to FontStyle.Italic
            "ExtraLight" -> FontWeight.ExtraLight to FontStyle.Normal
            "ExtraLightItalic" -> FontWeight.ExtraLight to FontStyle.Italic
            "Italic" -> FontWeight.Normal to FontStyle.Italic
            "Light" -> FontWeight.Light to FontStyle.Normal
            "LightItalic" -> FontWeight.Light to FontStyle.Italic
            "Medium" -> FontWeight.Medium to FontStyle.Normal
            "MediumItalic" -> FontWeight.Medium to FontStyle.Italic
            "Regular" -> FontWeight.Normal to FontStyle.Normal
            "SemiBold" -> FontWeight.SemiBold to FontStyle.Normal
            "SemiBoldItalic" -> FontWeight.SemiBold to FontStyle.Italic
            "Thin" -> FontWeight.Thin to FontStyle.Normal
            "ThinItalic" -> FontWeight.Thin to FontStyle.Italic
            else -> return null
        }
    return Font(file, weight = weight, style = style)
}

/**
 * Android already provides its platform font through Compose defaults.
 */
@Composable
@Suppress("LibraryEntitiesShouldNotBePublic")
actual fun rememberPlatformDefaultFontFamily(): FontFamily? = null
