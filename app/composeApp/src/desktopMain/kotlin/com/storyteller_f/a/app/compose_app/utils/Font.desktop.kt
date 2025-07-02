package com.storyteller_f.a.app.compose_app.utils

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import java.io.File

actual fun loadFontFromLocal(path: String): FontFamily {
    val file = File(path)
    val fonts = file.listFiles()?.mapNotNull {
        when {
            it.name.endsWith("Bold.ttf") -> Font(it, weight = FontWeight.Bold)
            it.name.endsWith("BoldItalic.ttf") -> Font(
                it,
                weight = FontWeight.Bold,
                style = FontStyle.Italic
            )

            it.name.endsWith("ExtraBold.ttf") -> Font(it, weight = FontWeight.ExtraBold)
            it.name.endsWith("ExtraBoldItalic.ttf") -> Font(
                it,
                weight = FontWeight.ExtraBold,
                style = FontStyle.Italic
            )

            it.name.endsWith("ExtraLight.ttf") -> Font(it, weight = FontWeight.ExtraLight)
            it.name.endsWith("ExtraLightItalic.ttf") -> Font(
                it,
                weight = FontWeight.ExtraLight,
                style = FontStyle.Italic
            )

            it.name.endsWith("Italic.ttf") -> Font(
                it,
                weight = FontWeight.Normal,
                style = FontStyle.Italic
            )

            it.name.endsWith("Light.ttf") -> Font(it, weight = FontWeight.Light)
            it.name.endsWith("LightItalic.ttf") -> Font(
                it,
                weight = FontWeight.Light,
                style = FontStyle.Italic
            )

            it.name.endsWith("Medium.ttf") -> Font(it, weight = FontWeight.Medium)
            it.name.endsWith("MediumItalic.ttf") -> Font(
                it,
                weight = FontWeight.Medium,
                style = FontStyle.Italic
            )

            it.name.endsWith("Regular.ttf") -> Font(it, weight = FontWeight.Normal)
            it.name.endsWith("SemiBold.ttf") -> Font(it, weight = FontWeight.SemiBold)
            it.name.endsWith("SemiBoldItalic.ttf") -> Font(
                it,
                weight = FontWeight.SemiBold,
                style = FontStyle.Italic
            )

            it.name.endsWith("Thin.ttf") -> Font(it, weight = FontWeight.Thin)
            it.name.endsWith("ThinItalic.ttf") -> Font(
                it,
                weight = FontWeight.Thin,
                style = FontStyle.Italic
            )

            else -> null
        }
    }
    return FontFamily(fonts.orEmpty())
}
