/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.cloud.openpdf

import com.storyteller_f.result.getOrThrow
import java.io.File

/** Developer-only asset maintenance entry point. Never part of a production request. */
fun main(args: Array<String>) {
    require(args.size == 1) { "Pass the cloud/openpdf module directory" }
    val root = File(args.single())
    val instancer = HarfBuzzFontInstancer()
    check(instancer.version == "14.6.0") { "Asset generation requires pinned HarfBuzz 14.6.0" }
    val sources = mapOf("serif" to "NotoSerifSC[wght].ttf", "mono" to "NotoSansMono[wdth,wght].ttf")
    for ((family, name) in sources) {
        val source = root.resolve("src/test/resources/fonts/$name").inputStream().buffered().use { it.readBytes() }
        for ((style, weight) in mapOf("regular" to 400f, "bold" to 700f)) {
            val instance = instancer.instantiate(source, 0, mapOf("wght" to weight)).getOrThrow()
            val target = root.resolve("src/main/resources/fonts/$family-$style.ttf")
            target.outputStream().buffered().use { it.write(instance) }
        }
    }
}
