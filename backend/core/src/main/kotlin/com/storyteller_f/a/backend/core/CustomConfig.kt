package com.storyteller_f.a.backend.core

class CustomKeyStore(val path: String, val pass: String)

class CustomConfig(
    val buildType: String,
    val flavor: String,
    val snapshotKeyStore: CustomKeyStore?,
)
