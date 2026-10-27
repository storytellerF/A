/*
 * This is a private project. All rights reserved.
 */

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Source-line identity permits cross-platform unions without mixing bytecode or branch counters. */
internal data class CoverageLine(val file: String, val number: Int)
internal data class CoverageHits(val unit: Boolean, val e2e: Boolean) {
    fun merge(other: CoverageHits): CoverageHits = CoverageHits(unit || other.unit, e2e || other.e2e)
}

internal class CombinedCoverageData(private val root: File, sources: Set<File>) {
    private val byPath = sources.associateBy { it.relativeTo(root).invariantSeparatorsPath }
    private val byName = sources.groupBy { source ->
        val content = source.readText()
        val packageName = Regex("(?m)^\\s*package\\s+([\\w.]+)").find(content)?.groupValues?.get(1).orEmpty()
        packageName.replace('.', '/') to source.name
    }
    val lines = sortedMapOf<CoverageLine, CoverageHits>(compareBy({ it.file }, { it.number }))
    val excluded = sortedSetOf<String>()

    fun readKover(report: File, unit: Boolean) {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val document = report.inputStream().buffered().use { factory.newDocumentBuilder().parse(it) }
        val packages = document.getElementsByTagName("package")
        repeat(packages.length) { packageIndex ->
            val pkg = packages.item(packageIndex) as Element
            val files = pkg.getElementsByTagName("sourcefile")
            repeat(files.length) { fileIndex ->
                val source = files.item(fileIndex) as Element
                val candidates = byName[pkg.getAttribute("name") to source.getAttribute("name")].orEmpty()
                check(candidates.size <= 1) { "Ambiguous source: ${pkg.getAttribute("name")}/${source.getAttribute("name")}" }
                val file = candidates.singleOrNull()
                if (file == null) excluded.add("${pkg.getAttribute("name")}/${source.getAttribute("name")}")
                else readKoverLines(source, file, unit)
            }
        }
    }

    private fun readKoverLines(source: Element, file: File, unit: Boolean) {
        val entries = source.getElementsByTagName("line")
        repeat(entries.length) { index ->
            val line = entries.item(index) as Element
            val covered = line.getAttribute("ci").toInt() > 0
            record(file.relativeTo(root).invariantSeparatorsPath, line.getAttribute("nr").toInt(), covered, unit)
        }
    }

    fun readLcov(report: File) {
        var path: String? = null
        report.forEachLine { line ->
            when {
                line.startsWith("SF:") -> path = line.substring(3).replace('\\', '/')
                line.startsWith("DA:") -> {
                    val fields = line.substring(3).split(',')
                    record(checkNotNull(path) { "LCOV line has no source" }, fields[0].toInt(), fields[1].toInt() > 0, false)
                }
                line == "end_of_record" -> path = null
            }
        }
    }

    private fun record(path: String, number: Int, covered: Boolean, unit: Boolean) {
        check(path in byPath) { "Coverage source is outside the production source catalog: $path" }
        check(number > 0) { "Invalid source line: $path:$number" }
        val key = CoverageLine(path, number)
        val hit = CoverageHits(unit && covered, !unit && covered)
        lines[key] = lines[key]?.merge(hit) ?: hit
    }

    fun writeLcov(output: File) {
        output.writeText(buildString {
            lines.entries.groupBy { it.key.file }.forEach { (file, entries) ->
                appendLine("TN:unit-and-e2e")
                appendLine("SF:$file")
                entries.forEach { (line, hit) -> appendLine("DA:${line.number},${if (hit.unit || hit.e2e) 1 else 0}") }
                appendLine("LF:${entries.size}")
                appendLine("LH:${entries.count { it.value.unit || it.value.e2e }}")
                appendLine("end_of_record")
            }
        })
    }
}
