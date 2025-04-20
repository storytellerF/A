package com.storyteller_f.shared

class WasmPlatform : Platform {
    override val name: String = "Web with Kotlin/Wasm"
    override val id: String
        get() = ""
}

actual fun getPlatform(): Platform = WasmPlatform()
