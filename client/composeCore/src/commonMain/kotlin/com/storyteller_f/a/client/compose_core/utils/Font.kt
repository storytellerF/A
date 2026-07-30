package com.storyteller_f.a.client.compose_core.utils

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily

expect fun loadFontFromLocal(path: String): FontFamily?

/**
 * Returns the preferred installed font family for the current platform, or null to use Compose defaults.
 */
@Suppress("LibraryEntitiesShouldNotBePublic")
expect fun getPlatformDefaultFontFamily(): FontFamily?

/**
 * Applies [fontFamily] to every Material typography style, keeping the receiver unchanged when it is null.
 */
@Suppress("LibraryEntitiesShouldNotBePublic")
fun Typography.withDefaultFontFamily(fontFamily: FontFamily?): Typography {
    val resolvedFontFamily = fontFamily ?: return this
    return copy(
        displayLarge = displayLarge.copy(fontFamily = resolvedFontFamily),
        displayMedium = displayMedium.copy(fontFamily = resolvedFontFamily),
        displaySmall = displaySmall.copy(fontFamily = resolvedFontFamily),
        headlineLarge = headlineLarge.copy(fontFamily = resolvedFontFamily),
        headlineMedium = headlineMedium.copy(fontFamily = resolvedFontFamily),
        headlineSmall = headlineSmall.copy(fontFamily = resolvedFontFamily),
        titleLarge = titleLarge.copy(fontFamily = resolvedFontFamily),
        titleMedium = titleMedium.copy(fontFamily = resolvedFontFamily),
        titleSmall = titleSmall.copy(fontFamily = resolvedFontFamily),
        bodyLarge = bodyLarge.copy(fontFamily = resolvedFontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = resolvedFontFamily),
        bodySmall = bodySmall.copy(fontFamily = resolvedFontFamily),
        labelLarge = labelLarge.copy(fontFamily = resolvedFontFamily),
        labelMedium = labelMedium.copy(fontFamily = resolvedFontFamily),
        labelSmall = labelSmall.copy(fontFamily = resolvedFontFamily),
    )
}
