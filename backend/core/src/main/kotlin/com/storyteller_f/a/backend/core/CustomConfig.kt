package com.storyteller_f.a.backend.core

import com.storyteller_f.shared.model.Dimension
import com.storyteller_f.shared.type.PrimaryKey
import java.io.File

class CustomKeyStore(val path: String, val pass: String)

class CustomConfig(
    val buildType: String,
    val flavor: String,
    val snapshotKeyStore: CustomKeyStore?,
)

data class UploadPack(
    val path: File,
    val name: String,
    val size: Long,
    val fullName: String
)

data class ProcessedUploadPack(
    val pack: UploadPack,
    val contentType: String = "",
    val dimension: Dimension? = null
)

data class CopyPack(val originFullName: String, val newFullName: String)

sealed interface JoinSearch {
    data class Joined(val uid: PrimaryKey) : JoinSearch
    data class NotJoined(val uid: PrimaryKey) : JoinSearch
    data class Unspecified(val uid: PrimaryKey?) : JoinSearch
}
