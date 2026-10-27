/*
 * This is a private project. All rights reserved.
 */

import com.google.gson.GsonBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Independent reports remain immutable; only executable source-line sets are unioned here. */
abstract class CombinedCoverageReportTask : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val unitXml: RegularFileProperty
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val e2eReports: DirectoryProperty
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection
    @get:Internal abstract val repositoryDirectory: DirectoryProperty
    @get:OutputDirectory abstract val reportDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val output = reportDirectory.get().asFile
        resetCoverageDirectory(output)
        val e2e = e2eReports.get().asFile
        check(!e2e.resolve("incomplete.txt").exists()) { "E2E coverage is incomplete" }
        val data = CombinedCoverageData(repositoryDirectory.get().asFile, sourceFiles.files)
        data.readKover(unitXml.get().asFile, true)
        check(data.lines.isNotEmpty()) { "Unit coverage has no production source lines" }
        val inputs = e2e.walkTopDown().filter { it.isFile && it.name in setOf("coverage.xml", "lcov.info") }.toList()
        check(inputs.isNotEmpty()) { "No E2E coverage reports were found" }
        inputs.forEach { if (it.extension == "xml") data.readKover(it, false) else data.readLcov(it) }
        data.writeLcov(output.resolve("lcov.info"))
        val summary = summarize(data.lines.values)
        output.resolve("summary.json").writeText(GsonBuilder().setPrettyPrinting().create().toJson(summary))
        output.resolve("excluded-sources.txt").writeText(data.excluded.joinToString("\n"))
        writeHtml(data, summary)
    }

    private fun writeHtml(data: CombinedCoverageData, summary: Map<String, Int>) {
        val output = reportDirectory.get().asFile
        val rows = data.lines.entries.groupBy { it.key.file }.entries.mapIndexed { index, (file, entries) ->
            val name = "source-$index.html"
            val hits = entries.associate { it.key.number to it.value }
            val source = repositoryDirectory.get().asFile.resolve(file).readLines().mapIndexed { number, text ->
                val hit = hits[number + 1]
                val state = when {
                    hit == null -> "none"
                    hit.unit && hit.e2e -> "both"
                    hit.unit -> "unit"
                    hit.e2e -> "e2e"
                    else -> "miss"
                }
                "<tr class='$state' id='L${number + 1}'><td>${number + 1}</td><td><pre>${escapeCoverageHtml(text)}</pre></td></tr>"
            }.joinToString("\n")
            output.resolve(name).writeText(page(file, "<p><a href='index.html'>Summary</a></p>" +
                "<p>Blue: unit only; yellow: E2E only; green: both; red: uncovered executable line.</p><table>$source</table>"))
            val counts = summarize(entries.map { it.value })
            "<tr><td><a href='$name'>${escapeCoverageHtml(file)}</a></td>${cells(counts)}</tr>"
        }.joinToString("\n")
        val percent = "%.2f".format(java.util.Locale.ROOT, 100.0 * summary.getValue("covered") / summary.getValue("lines"))
        val content = """
            <p>Combined line coverage: <strong>$percent%</strong> (${summary.getValue("covered")} / ${summary.getValue("lines")}).</p>
            <p>Source-line union across the existing Kover unit-test scope and completed JVM, Android and Wasm E2E reports.
            A shared source line counts once when any included platform hits it; this does not establish coverage on every platform.</p>
            <p>Denominator: union of executable lines exposed by these reports. Wasm includes linked mapped lines only.
            Branch, method and instruction metrics remain in the independent platform reports. Uninstrumented Wasm unit tests contribute no data.</p>
            <p><a href='../kover/html/index.html'>Unit tests (Kover)</a> |
            <a href='../e2eCoverage/index.html'>E2E platform reports</a> |
            <a href='lcov.info'>Combined LCOV</a> | <a href='summary.json'>Counts</a> |
            <a href='excluded-sources.txt'>Sources outside the repository production catalog (${data.excluded.size})</a></p>
            <table><tr><th>Source</th><th>Unit hits</th><th>E2E hits</th><th>Both</th><th>Unit only</th><th>E2E only</th><th>Combined hits</th><th>Executable lines</th></tr>
            <tr><th>Total</th>${cells(summary)}</tr>$rows</table>
        """.trimIndent()
        output.resolve("index.html").writeText(page("Unit + E2E source-line coverage", content))
    }

    private fun cells(counts: Map<String, Int>): String =
        listOf("unit", "e2e", "both", "unitOnly", "e2eOnly", "covered", "lines").joinToString("") { "<td>${counts.getValue(it)}</td>" }

    private fun summarize(hits: Collection<CoverageHits>): Map<String, Int> = mapOf(
        "unit" to hits.count { it.unit }, "e2e" to hits.count { it.e2e }, "both" to hits.count { it.unit && it.e2e },
        "unitOnly" to hits.count { it.unit && !it.e2e }, "e2eOnly" to hits.count { it.e2e && !it.unit },
        "covered" to hits.count { it.unit || it.e2e }, "lines" to hits.size,
    )

    private fun page(title: String, content: String): String = """
        <!doctype html><meta charset="utf-8"><title>${escapeCoverageHtml(title)}</title>
        <style>body{font:16px system-ui;margin:40px}td,th{padding:6px;border-bottom:1px solid #ddd;text-align:left}
        pre{margin:0;white-space:pre-wrap}.unit{background:#dcecff}.e2e{background:#fff1bd}.both{background:#dbf5df}.miss{background:#ffdddd}</style>
        <h1>${escapeCoverageHtml(title)}</h1>$content
    """.trimIndent()
}
