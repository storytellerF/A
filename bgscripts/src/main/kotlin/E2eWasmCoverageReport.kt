/*
 * This is a private project. All rights reserved.
 */

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/** Browser datasets merge by the original module identity, never by percentages or line positions alone. */
internal fun renderWasmCoverage(
    files: List<File>, output: File, fingerprint: String, errors: MutableList<String>, links: MutableList<Pair<String, String>>,
) {
    val groups = mutableMapOf<String, Pair<JsonObject, MutableSet<Int>>>()
    files.filter { it.name == "snapshots.json" }.forEach { snapshot ->
        try {
            val manifest = JsonParser.parseString(snapshot.parentFile.resolve("manifest.json").readText()).asJsonObject
            val data = JsonParser.parseString(snapshot.readText()).asJsonObject
            check(manifest["sourceFingerprint"].asString == fingerprint) { "Sources changed after Wasm instrumentation" }
            val id = manifest["buildId"].asString
            val program = manifest["program"].asString
            check(program.matches(Regex("[A-Za-z0-9_-]+"))) { "Invalid coverage program name" }
            check(groups.values.none { it.first["program"].asString == program && it.first["buildId"].asString != id }) {
                "Multiple builds of $program cannot share one report"
            }
            check(data["buildId"].asString == id) { "Wasm data and manifest belong to different builds" }
            val documents = data.getAsJsonObject("documents").entrySet()
            check(documents.isNotEmpty() && documents.none { it.value.isJsonNull }) { "Browser document coverage is incomplete" }
            val group = groups.getOrPut(id) { manifest to mutableSetOf() }
            check(group.first == manifest) { "Wasm manifests differ for the same build identity" }
            documents.forEach { document ->
                val instances = document.value.asJsonObject.getAsJsonArray("instances")
                check(!instances.isEmpty) { "Wasm instance coverage is missing" }
                instances.forEach { instance ->
                    instance.asJsonObject.getAsJsonArray("covered").forEach { point ->
                        check(point.asInt in 0 until manifest.getAsJsonArray("lines").size()) { "Unknown Wasm coverage location" }
                        group.second.add(point.asInt)
                    }
                }
            }
        } catch (failure: Exception) { errors.add("Wasm coverage: ${failure.message}") }
    }
    groups.values.forEach { (manifest, covered) ->
        val program = manifest["program"]?.asString ?: "wasm"
        check(program.matches(Regex("[A-Za-z0-9_-]+"))) { "Invalid coverage program name" }
        val target = output.resolve("wasm-$program").also { it.mkdirs() }
        val lines = manifest.getAsJsonArray("lines").map { it.asJsonObject }
        val lcov = buildString {
            lines.groupBy { it["file"].asString }.forEach { (file, locations) ->
                appendLine("TN:$program")
                appendLine("SF:$file")
                locations.forEach { point -> appendLine("DA:${point["line"].asInt},${if (point["id"].asInt in covered) 1 else 0}") }
                appendLine("LF:${locations.size}")
                appendLine("LH:${locations.count { it["id"].asInt in covered }}")
                appendLine("end_of_record")
            }
        }
        target.resolve("lcov.info").writeText(lcov)
        val rows = lines.groupBy { it["file"].asString }.map { (file, locations) ->
            val count = locations.count { it["id"].asInt in covered }
            "<tr><td>${escapeCoverageHtml(file)}</td><td>$count / ${locations.size}</td></tr>"
        }.joinToString("\n")
        target.resolve("index.html").writeText("""
            <!doctype html><meta charset="utf-8"><title>$program mapped line coverage</title>
            <style>body{font:16px system-ui;margin:40px}td,th{padding:10px;border-bottom:1px solid #ccc;text-align:left}</style>
            <h1>$program mapped line coverage</h1><p>${covered.size} / ${lines.size} linked, mapped project lines. Source branch coverage is unavailable. Unmapped and eliminated code is outside this denominator.</p>
            <p><a href='lcov.info'>LCOV</a></p><table><tr><th>Source</th><th>Covered / mapped lines</th></tr>$rows</table>
        """.trimIndent())
        links.add("$program Wasm mapped lines" to "wasm-$program/index.html")
    }
}
