/*
 * This is a private project. All rights reserved.
 */

@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package js.core

import kotlin.js.JsNumber
import kotlin.js.JsString
import kotlin.js.toJsNumber

/**
 * Bridges Ktor 3.6.0's legacy conversion ABI without downgrading the recorder's wrappers.
 * Wrappers 2026.4 removed this stateless helper. Remove after Ktor rebuilds against that ABI.
 * See https://github.com/JetBrains/kotlin-wrappers/tree/2026.1.9/kotlin-js-core
 */
internal object JsPrimitives {
    fun JsString.toKotlinString(): String = toString()
    fun JsNumber.toKotlinDouble(): Double = toDouble()
    fun Double.toJsDouble(): JsNumber = toJsNumber()
    fun Int.toJsInt(): JsNumber = toJsNumber()
}
