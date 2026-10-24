/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.cloud.openpdf

import com.storyteller_f.result.CustomResult
import com.storyteller_f.result.getOrThrow
import java.io.File

/** Static faces take precedence over variable sources, independently for each requested weight. */
internal class PdfFontFamily private constructor(
    private val regular: ByteArray?,
    private val bold: ByteArray?,
    loadVariable: () -> ByteArray?,
) {
    constructor(regular: ByteArray?, bold: ByteArray?, variable: ByteArray?) : this(regular, bold, { variable })

    private val variable: ByteArray? by lazy(loadVariable)

    fun getInstance(repository: FontInstanceRepository, weight: Int): CustomResult<FontInstance, FontError> =
        captureFontFailure {
            require(weight == 400 || weight == 700) { "PDF styles require Regular (400) or Bold (700)" }
            val static = if (weight == 400) regular else bold
            if (static != null) {
                require(!SfntFont(static, 0).hasTable("fvar")) { "Static PDF font slot contains a variable font" }
                repository.getInstance(static).getOrThrow()
            } else {
                val source = requireNotNull(variable) { "Missing PDF font face for weight $weight" }
                repository.getInstance(source, axes = mapOf("wght" to weight.toFloat())).getOrThrow()
            }
        }

    companion object {
        fun load(
            name: String,
            directory: File? = System.getProperty("pdf.font.directory")?.let(::File),
        ): PdfFontFamily =
            if (directory == null) {
            PdfFontFamily(bundled("$name-regular.ttf"), bundled("$name-bold.ttf"), null)
        } else {
            require(directory.isDirectory) { "PDF font directory does not exist" }
            PdfFontFamily(
                optionalFile(directory, "$name-regular.ttf"),
                optionalFile(directory, "$name-bold.ttf"),
            ) { optionalFile(directory, "$name-variable.ttf") }
        }

        private fun optionalFile(directory: File, name: String): ByteArray? =
            directory.resolve(name).let {
            if (it.exists()) it.inputStream().buffered().use { input -> input.readBytes() } else null
        }

        private fun bundled(name: String): ByteArray =
            checkNotNull(javaClass.getResourceAsStream("/fonts/$name")) {
            "Missing bundled PDF font $name"
        }.buffered().use { it.readBytes() }
    }
}
