/*
 * This is a private project. All rights reserved.
 */

@file:JsModule("@noble/hashes/sha3")

package com.storyteller_f.shared.hashes

import kotlin.js.JsName

@JsName("keccak_256")
external fun keccak256(data: String): String
