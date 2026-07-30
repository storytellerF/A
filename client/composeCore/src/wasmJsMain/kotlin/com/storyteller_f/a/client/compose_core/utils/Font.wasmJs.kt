package com.storyteller_f.a.client.compose_core.utils

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.SystemFont
import kotlinx.browser.document
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.hostOs
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.math.abs

actual fun loadFontFromLocal(path: String): FontFamily? = null

/**
 * Selects the first installed CJK-capable system font for Compose Web.
 */
@OptIn(ExperimentalTextApi::class)
@Suppress("LibraryEntitiesShouldNotBePublic")
actual fun getPlatformDefaultFontFamily(): FontFamily? {
    val fontName = systemFontCandidates().firstOrNull(::isSystemFontInstalled) ?: return null
    return FontFamily(SystemFont(fontName))
}

private const val FONT_PROBE = "72px"
private const val FONT_PROBE_TEXT = "mmmmmmmmmmWWWWWWWWWW中文字体"
private const val FONT_PROBE_TOLERANCE = 0.01

@OptIn(ExperimentalWasmJsInterop::class)
private val fontProbeContext: CanvasRenderingContext2D by lazy {
    val canvas = document.createElement("canvas") as HTMLCanvasElement
    canvas.getContext("2d") as CanvasRenderingContext2D
}

private fun isSystemFontInstalled(fontFamily: String): Boolean =
    listOf("monospace", "sans-serif", "serif").any { fallback ->
        fontProbeContext.font = "$FONT_PROBE $fallback"
        val fallbackWidth = fontProbeContext.measureText(FONT_PROBE_TEXT).width
        fontProbeContext.font = "$FONT_PROBE \"$fontFamily\", $fallback"
        abs(fontProbeContext.measureText(FONT_PROBE_TEXT).width - fallbackWidth) > FONT_PROBE_TOLERANCE
    }

private fun systemFontCandidates(): List<String> {
    val prioritizedCandidates =
        when (hostOs) {
            OS.Windows -> windowsFontCandidates
            OS.MacOS, OS.Ios -> appleFontCandidates
            OS.Linux -> linuxFontCandidates
            OS.Android -> androidFontCandidates
            OS.Tvos, OS.Unknown -> emptyList()
        }
    return (prioritizedCandidates + commonFontCandidates).distinct()
}

private val windowsFontCandidates =
    listOf(
        "Microsoft YaHei UI",
        "Microsoft YaHei",
        "DengXian",
        "SimHei",
        "SimSun",
    )

private val appleFontCandidates =
    listOf(
        "PingFang SC",
        "Hiragino Sans GB",
        "Heiti SC",
        "Songti SC",
    )

private val linuxFontCandidates =
    listOf(
        "Noto Sans CJK SC",
        "Noto Sans SC",
        "WenQuanYi Micro Hei",
        "Droid Sans Fallback",
        "AR PL UMing CN",
        "AR PL UKai CN",
    )

private val androidFontCandidates =
    listOf(
        "Noto Sans CJK SC",
        "Noto Sans SC",
        "Droid Sans Fallback",
    )

private val commonFontCandidates =
    listOf(
        "Noto Sans CJK SC",
        "Noto Sans SC",
        "Source Han Sans SC",
        "Source Han Sans CN",
        "HarmonyOS Sans SC",
        "MiSans",
        "Microsoft YaHei UI",
        "Microsoft YaHei",
        "PingFang SC",
        "Hiragino Sans GB",
        "WenQuanYi Micro Hei",
        "Droid Sans Fallback",
        "Arial Unicode MS",
    )
