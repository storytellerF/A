package com.storyteller_f.shared.utils

import kotlin.js.ExperimentalWasmJsInterop

@OptIn(ExperimentalWasmJsInterop::class)
@JsModule("ml-crypto")
private external object MlHash {
    @Suppress("UnusedParameter")
    fun md5Hex(data: String): String
}

internal actual fun md5Platform(input: ByteArray): ByteArray =
    MlHash.md5Hex(input.toHexString()).hexToByteArray()
