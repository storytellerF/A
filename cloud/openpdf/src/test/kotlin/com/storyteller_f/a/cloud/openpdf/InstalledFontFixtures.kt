/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.cloud.openpdf

import com.storyteller_f.result.getOrThrow
import java.io.File

/** Integration fixtures come from the host installation; no font binaries are copied into the repository. */
internal object InstalledFontFixtures {
    private val repository = FontInstanceRepository()
    private val variable by lazy {
        requireNotNull(
            InstalledPdfFonts.installedFaces().firstOrNull {
                it.variable && !it.italic && it.source.file.extension.equals("ttf", ignoreCase = true) && it.family in
                    listOf(
                        "Noto Serif SC",
                        "Noto Serif CJK SC",
                        "Source Han Serif SC",
                    )
            },
        ) {
            "Font tests require an installed standalone variable Noto Serif SC/CJK SC or Source Han Serif SC font"
        }.source.readBytes()
    }
    private val cff2 by lazy {
        val configured = System.getProperty("pdf.test.cff2.font")?.let(::File)
        val source =
            configured ?: InstalledPdfFonts.installedFaces().firstOrNull {
                it.variable && it.source.faceIndex == 0 && it.source.file.extension.equals("otf", ignoreCase = true) &&
                    it.family in listOf("Noto Serif SC", "Noto Serif CJK SC", "Source Han Serif SC")
            }?.source?.file
        val file =
            requireNotNull(source) {
                "Provide an installed CFF2 Chinese variable font through pdf.test.cff2.font"
            }
        val bytes = FontFileSource(file).readBytes()
        require(SfntFont(bytes, 0).hasTable("CFF2")) { "CFF2 test input must contain CFF2 outlines" }
        bytes
    }
    private val multiAxis by lazy { externalCff2("pdf.test.cff2.multiaxis.font") }
    private val collectionFace by lazy { externalCff2("pdf.test.cff2.collection.font") }
    private val mono by lazy { PdfFontFamily.load("mono") }

    private fun externalCff2(property: String): ByteArray {
        val file = requireNotNull(System.getProperty(property)) { "Provide an external CFF2 font through $property" }
        val bytes = FontFileSource(File(file)).readBytes()
        require(SfntFont(bytes, 0).hasTable("CFF2")) { "$property must contain CFF2 outlines" }
        return bytes
    }

    fun read(name: String): ByteArray =
        when (name) {
        "variable.ttf" -> variable.copyOf()
        "cff2-variable.otf" -> cff2.copyOf()
        "cff2-multiaxis.otf" -> multiAxis.copyOf()
        "cff2-collection.otf" -> collectionFace.copyOf()
        "mono-regular.ttf" -> mono.getInstance(repository, 400).getOrThrow().copyBytes()
        "mono-bold.ttf" -> mono.getInstance(repository, 700).getOrThrow().copyBytes()
        "serif-regular.ttf" -> repository.getInstance(variable, axes = mapOf("wght" to 400f)).getOrThrow().copyBytes()
        "serif-bold.ttf" -> repository.getInstance(variable, axes = mapOf("wght" to 700f)).getOrThrow().copyBytes()
        else -> error("Unknown installed font fixture $name")
    }
}

internal fun fontBytes(name: String): ByteArray = InstalledFontFixtures.read(name)
