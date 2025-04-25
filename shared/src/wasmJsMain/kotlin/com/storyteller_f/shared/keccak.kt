package com.storyteller_f.shared

@JsModule("keccak")
external fun createKeccakHash(type: String): Keccak

@Suppress("UnusedParameter")
external object Keccak {
    fun update(data: String): Keccak
    fun digest(type: String): String
}
