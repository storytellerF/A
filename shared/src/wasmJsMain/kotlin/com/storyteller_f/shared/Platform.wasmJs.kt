/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.shared

class WasmPlatform : Platform {
    override val name: String = "Web with Kotlin/Wasm"
}

actual fun getPlatform(): Platform = WasmPlatform()
