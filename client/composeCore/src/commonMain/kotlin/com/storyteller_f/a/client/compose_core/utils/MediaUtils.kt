package com.storyteller_f.a.client.compose_core.utils

import com.storyteller_f.a.client.core.UploadData
import io.ktor.http.ContentType
import io.ktor.http.defaultForFileExtension
import kotlinx.io.buffered
import kotlinx.io.files.FileMetadata
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

fun getUploadDataFromPath(
    meta: FileMetadata,
    path: Path,
    sha256: String,
) = UploadData(meta.size, path.name, ContentType.defaultForFileExtension(path.toString()), sha256) {
    SystemFileSystem.source(path).buffered()
}
