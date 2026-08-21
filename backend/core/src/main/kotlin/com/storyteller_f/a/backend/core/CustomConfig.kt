/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.backend.core

data class CustomKeyStore(val path: String, val pass: String)

data class CustomConfig(
    val buildType: String,
    val flavor: String,
    val snapshotKeyStore: CustomKeyStore?,
    val enableSignUp: Boolean = true,
)
