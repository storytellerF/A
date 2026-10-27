/*
 * This is a private project. All rights reserved.
 */

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CombinedCoverageDataTest {
    @Test
    fun unionsOverlappingHitsAndUncoveredDenominators() {
        val root = root("union")
        val source = root.resolve("module/src/commonMain/kotlin/Example.kt").apply {
            parentFile.mkdirs()
            writeText("package example\nclass Example\n")
        }
        val data = CombinedCoverageData(root, setOf(source))
        data.readKover(xml(root, "unit", listOf(1 to true, 2 to true, 3 to false)), true)
        data.readKover(xml(root, "e2e", listOf(1 to true, 3 to true, 4 to false)), false)
        val wasm = root.resolve("wasm.info").apply {
            writeText("SF:module/src/commonMain/kotlin/Example.kt\nDA:3,1\nDA:5,0\nend_of_record\n")
        }
        repeat(2) { data.readLcov(wasm) }
        assertEquals(5, data.lines.size)
        val hits = data.lines.values
        assertEquals(2, hits.count { it.unit })
        assertEquals(2, hits.count { it.e2e })
        assertEquals(1, hits.count { it.unit && it.e2e })
        assertEquals(3, hits.count { it.unit || it.e2e })
        val output = root.resolve("combined.info")
        data.writeLcov(output)
        assertTrue(output.readText().contains("LF:5\nLH:3"))
        assertFalse(output.readText().contains("BRDA"))
    }

    @Test
    fun rejectsAmbiguousSourceOwnership() {
        val root = root("ambiguous")
        val sources = listOf("first", "second").map { name ->
            root.resolve("$name/Example.kt").apply { parentFile.mkdirs(); writeText("package example\n") }
        }.toSet()
        val result = runCatching { CombinedCoverageData(root, sources).readKover(xml(root, "unit", listOf(1 to true)), true) }
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Ambiguous source"))
    }

    @Test
    fun rejectsForeignLcovSource() {
        val root = root("foreign")
        val report = root.resolve("foreign.info").apply { writeText("SF:../private.kt\nDA:1,1\n") }
        val result = runCatching { CombinedCoverageData(root, emptySet()).readLcov(report) }
        assertTrue(result.isFailure)
    }

    @Test
    fun listsSourcesOutsideProductionCatalog() {
        val root = root("generated")
        val data = CombinedCoverageData(root, emptySet())
        data.readKover(xml(root, "unit", listOf(1 to true)), true)
        assertTrue(data.lines.isEmpty())
        assertEquals(setOf("example/Example.kt"), data.excluded)
    }

    private fun root(name: String): File = File(System.getProperty("coverage.test.directory"), "combined-$name")
        .apply { resetCoverageDirectory(this) }

    private fun xml(root: File, name: String, lines: List<Pair<Int, Boolean>>): File = root.resolve("$name.xml").apply {
        val content = lines.joinToString("") { (number, hit) -> "<line nr='$number' ci='${if (hit) 1 else 0}'/>" }
        writeText("<report><package name='example'><sourcefile name='Example.kt'>$content</sourcefile></package></report>")
    }
}
