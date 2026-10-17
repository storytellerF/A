/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.test

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestReportDirectoryTest {
    @Test
    fun keepReadableNamesAndReplaceOnlySelectedCase() {
        val root = File("build/test/report-directories/readable")
        val directory = prepareTestReportDirectory(root, "ExampleTest", "register and browse 测试")
        assertEquals(root.resolve("ExampleTest/register and browse 测试"), directory)
        directory.resolve("stale.log").writeText("old report")
        val other = prepareTestReportDirectory(root, "ExampleTest", "other test")
        other.resolve("keep.log").writeText("other report")
        assertEquals(directory, prepareTestReportDirectory(root, "ExampleTest", "register and browse 测试"))
        assertFalse(directory.resolve("stale.log").exists())
        assertEquals("other report", other.resolve("keep.log").readText())
    }

    @Test
    fun encodeUnsafeNamesWithoutCollisions() {
        val root = File("build/test/report-directories/encoded")
        val names = listOf("../test", "..\\test", "CON", "%43ON", "", ".", "..", "a:", "a%3A", "trailing.")
        val directories = names.map { prepareTestReportDirectory(root, it) }
        assertEquals(names.size, directories.distinct().size)
        directories.forEach { assertTrue(it.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())) }
        assertEquals("%43ON", directories[2].name)
        assertEquals("a%3A", directories[7].name)
    }
}
