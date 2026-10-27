/*
 * This is a private project. All rights reserved.
 */

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class E2eWasmCoverageReportTest {
    @Test
    fun mergesBrowserHitsAgainstTheSameLinkedLineDenominator() {
        val root = File(System.getProperty("coverage.test.directory"), "wasm-report").apply { resetCoverageDirectory(this) }
        val snapshot = dataset(root, "current", "source", """{"first":{"instances":[{"covered":[0]}]},"second":{"instances":[{"covered":[1]}]}}""")
        val errors = mutableListOf<String>()
        val links = mutableListOf<Pair<String, String>>()
        val output = root.resolve("report")
        renderWasmCoverage(listOf(snapshot), output, "source", errors, links)
        assertTrue(errors.isEmpty())
        assertEquals(listOf("app Wasm mapped lines" to "wasm-app/index.html"), links)
        val lcov = output.resolve("wasm-app/lcov.info").readText()
        assertTrue(lcov.contains("DA:12,1\nDA:15,1\nDA:19,0"))
        assertTrue(lcov.contains("LF:3\nLH:2"))
        assertFalse(lcov.contains("BRDA"))
    }

    @Test
    fun rejectsChangedSourcesAndUnacknowledgedDocuments() {
        val root = File(System.getProperty("coverage.test.directory"), "wasm-incomplete").apply { resetCoverageDirectory(this) }
        val stale = dataset(root, "stale", "old-source", """{"first":{"instances":[{"covered":[0]}]}}""")
        val missing = dataset(root, "missing", "source", """{"first":null}""")
        val errors = mutableListOf<String>()
        val links = mutableListOf<Pair<String, String>>()
        renderWasmCoverage(listOf(stale, missing), root.resolve("report"), "source", errors, links)
        assertEquals(2, errors.size)
        assertTrue(errors.any { "Sources changed" in it })
        assertTrue(errors.any { "incomplete" in it })
        assertTrue(links.isEmpty())
    }

    private fun dataset(root: File, name: String, fingerprint: String, documents: String): File {
        val directory = root.resolve(name).apply { mkdirs() }
        directory.resolve("manifest.json").writeText("""
            {"buildId":"build","program":"app","sourceFingerprint":"$fingerprint","lines":[
              {"id":0,"file":"Source.kt","line":12},
              {"id":1,"file":"Source.kt","line":15},
              {"id":2,"file":"Source.kt","line":19}]}
        """.trimIndent())
        return directory.resolve("snapshots.json").also {
            it.writeText("""{"buildId":"build","documents":$documents}""")
        }
    }
}
