package com.storyteller_f.media

import com.ashampoo.kim.common.convertToPhotoMetadata
import com.ashampoo.kim.jvm.KimJvm
import com.storyteller_f.Backend
import com.storyteller_f.shared.model.AMEDIA_BUCKET
import com.storyteller_f.shared.model.MediaInfo
import org.apache.tika.Tika
import java.io.File

data class UploadPack(
    val name: String,
    val path: File,
    val contentType: String? = null,
    val meta: Map<String, String> = emptyMap()
)

interface MediaService {
    fun upload(bucketName: String, list: List<UploadPack>): Result<List<MediaInfo?>>

    fun get(bucketName: String, objList: List<String?>): Result<List<MediaInfo?>>

    fun clean(bucketName: String): Result<Unit>

    fun list(bucketName: String, prefix: String): Result<List<MediaInfo>>
}

fun uploadFiles(
    tika: Tika,
    backend: Backend,
    files: List<Triple<File, String, String?>>
): Result<List<MediaInfo?>> {
    val packs = files.map { (file, saveFileName, contentType) ->
        val type = checkContentType(file, tika, contentType)
        val meta = if (type.second?.startsWith("image") == true) {
            KimJvm.readMetadata(file)?.convertToPhotoMetadata()?.let {
                val width = it.widthPx
                val height = it.heightPx
                if (width != null && height != null) {
                    mapOf("width" to width.toString(), "height" to height.toString())
                } else {
                    null
                }
            } ?: emptyMap()
        } else {
            emptyMap()
        }
        UploadPack(saveFileName, file, contentType, meta)
    }

    return backend.mediaService.upload(AMEDIA_BUCKET, packs)
}

private fun checkContentType(
    file: File,
    tika: Tika,
    contentType: String?
): Pair<String?, String?> {
    val s = "audio/mp4"
    val mimeType = tika.detect(file)
    return if (contentType == s) {
        if (mimeType == s) {
            s
        } else {
            null
        }
    } else {
        null
    } to mimeType
}
