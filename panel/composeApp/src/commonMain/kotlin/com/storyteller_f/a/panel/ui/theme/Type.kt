package com.example.ui.theme

import androidx.compose.material3.Typography
import com.storyteller_f.a.client.compose_core.utils.getPlatformDefaultFontFamily
import com.storyteller_f.a.client.compose_core.utils.withDefaultFontFamily

/** Global Material typography with the platform-preferred font family. */
val AppTypography: Typography = Typography().withDefaultFontFamily(getPlatformDefaultFontFamily())
