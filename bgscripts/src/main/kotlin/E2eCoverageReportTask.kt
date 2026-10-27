/*
 * This is a private project. All rights reserved.
 */

import kotlinx.kover.features.jvm.ClassFilters
import kotlinx.kover.features.jvm.KoverLegacyFeatures
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.Properties

/** Reports consume completed artifacts only; no live application or container is queried here. */
abstract class E2eCoverageReportTask : DefaultTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val rawArtifacts: ConfigurableFileCollection
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val originalClassFiles: ConfigurableFileCollection
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection
    @get:Internal abstract val repositoryDirectory: DirectoryProperty
    @get:OutputDirectory abstract val reportDirectory: DirectoryProperty
    @get:LocalState abstract val scratchDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val output = reportDirectory.get().asFile
        check(output.canonicalFile != repositoryDirectory.get().asFile.canonicalFile &&
            output.canonicalFile.toPath().startsWith(repositoryDirectory.get().asFile.canonicalFile.toPath()))
        output.deleteRecursively()
        output.mkdirs()
        val errors = mutableListOf<String>()
        val files = rawArtifacts.files.filter(File::isFile)
        files.filter { it.name == "status" && it.readText().trim() != "complete" }.forEach {
            errors.add("Incomplete coverage: ${relative(it)}")
        }
        files.filter { it.name == "collection-error.txt" }.forEach { errors.add("Collection failed: ${relative(it)}") }
        val fingerprint = coverageSourceFingerprint(sourceFiles.files)
        val catalog = originalClassFiles.files.filter { it.name == "classes.properties" }.associateBy {
            val properties = properties(it)
            properties.getProperty("classSet") to properties.getProperty("buildId")
        }
        val groups = collectJvm(files, catalog, fingerprint, errors)
        val links = mutableListOf<Pair<String, String>>()
        groups.forEach { group ->
            attempt(errors, group.classSet) {
                val name = group.classSet.trim(':').replace(':', '-')
                renderJvm(output.resolve(name), group.binaries, listOf(group.originals),
                    sourceRoots(group.classSet.endsWith(":androidApp")), group.classSet)
                links.add(group.classSet to "$name/html/index.html")
            }
        }
        val jvm = groups.filterNot { it.classSet.endsWith(":androidApp") }
        if (jvm.isNotEmpty()) attempt(errors, "JVM aggregate") {
            val classes = mergeOriginals(jvm.map { it.originals })
            renderJvm(output.resolve("jvm"), jvm.flatMap { it.binaries }, listOf(classes), sourceRoots(false), "JVM E2E aggregate")
            links.add("JVM E2E aggregate" to "jvm/html/index.html")
        }
        renderWasmCoverage(files, output, fingerprint, errors, links)
        if (links.isEmpty()) errors.add("No valid E2E coverage datasets were found")
        val content = links.joinToString("\n") { (label, link) -> "<li><a href='$link'>${escapeCoverageHtml(label)}</a></li>" }
        val failures = errors.joinToString("\n") { "<li>${escapeCoverageHtml(it)}</li>" }
        output.resolve("index.html").writeText("""
            <!doctype html><meta charset="utf-8"><title>E2E coverage</title>
            <style>body{font:16px system-ui;max-width:1000px;margin:40px auto;padding:0 24px}li{margin:12px 0}.error{color:#a12622}</style>
            <h1>E2E coverage</h1><p>JVM/Android: source-owned original classes, Kover engine filters. Wasm: linked, mapped project lines; source branch coverage is unavailable. Android and JVM percentages remain separate.</p>
            <p><a href='../coverage/index.html'>Unit + E2E source-line summary</a></p>
            <ul>$content</ul><ul class='error'>$failures</ul>
        """.trimIndent())
        if (errors.isNotEmpty()) {
            output.resolve("incomplete.txt").writeText(errors.joinToString("\n"))
            error("E2E coverage is incomplete; see ${output.resolve("index.html")}")
        }
    }

    private fun collectJvm(
        files: List<File>, catalog: Map<Pair<String?, String?>, File>, fingerprint: String, errors: MutableList<String>,
    ): List<JvmDataset> {
        val groups = mutableMapOf<Pair<String, String>, MutableList<File>>()
        files.filter { it.name == "report.ic" }.forEach { binary ->
            attempt(errors, relative(binary)) {
                check(binary.length() > 0) { "Empty coverage binary" }
                val metadata = properties(binary.parentFile.resolve("classes.properties"))
                check(metadata.getProperty("sourceFingerprint") == fingerprint) { "Snapshot belongs to different sources" }
                val classSet = checkNotNull(metadata.getProperty("classSet"))
                val id = checkNotNull(metadata.getProperty("buildId"))
                val descriptor = checkNotNull(catalog[classSet to id]) { "Matching original bytecode is missing" }
                val expected = properties(descriptor)
                check(expected.getProperty("sourceFingerprint") == fingerprint) { "Sources changed after instrumentation" }
                groups.getOrPut(classSet to id) { mutableListOf() }.add(binary)
            }
        }
        val datasets = mutableListOf<JvmDataset>()
        groups.forEach { (key, binaries) ->
            attempt(errors, key.first) {
                check(groups.keys.count { it.first == key.first } == 1) { "Multiple builds cannot share one coverage report" }
                val originals = checkNotNull(catalog[key]).parentFile
                val actual = java.io.ByteArrayInputStream(createCoverageDescriptor(originals, key.first, sourceFiles.files)).use {
                    Properties().apply { load(it) }
                }
                check(actual.getProperty("buildId") == key.second) { "Original bytecode changed after instrumentation" }
                datasets.add(JvmDataset(key.first, originals, binaries.toList()))
            }
        }
        return datasets
    }

    private fun renderJvm(output: File, binaries: List<File>, classes: List<File>, sources: List<File>, title: String) {
        output.mkdirs()
        val filters = ClassFilters(emptySet(), emptySet(), emptySet(), emptySet(), emptySet(), emptySet())
        KoverLegacyFeatures.generateXmlReport(output.resolve("coverage.xml"), binaries, classes, sources, title, filters)
        KoverLegacyFeatures.generateHtmlReport(output.resolve("html"), "UTF-8", binaries, classes, sources, title, filters)
        KoverLegacyFeatures.mergeIc(output.resolve("coverage.ic"), binaries)
    }

    private fun mergeOriginals(roots: List<File>): File {
        val output = scratchDirectory.get().asFile
        check(output.canonicalFile != repositoryDirectory.get().asFile.canonicalFile &&
            output.canonicalFile.toPath().startsWith(repositoryDirectory.get().asFile.canonicalFile.toPath()))
        output.deleteRecursively()
        output.mkdirs()
        roots.forEach { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "class" }.forEach { file ->
                val target = output.resolve(file.relativeTo(root))
                check(!target.exists() || target.readBytes().contentEquals(file.readBytes())) {
                    "Different JVM bytecode for ${file.relativeTo(root)}; keep variants separate"
                }
                target.parentFile.mkdirs()
                file.copyTo(target, overwrite = true)
            }
        }
        return output
    }

    private fun sourceRoots(android: Boolean): List<File> {
        val variants = if (android) setOf("androidMain", "noJvmMain") else setOf("jvmMain")
        val allowed = variants + setOf("main", "commonMain", "jvmAndroidMain")
        return sourceFiles.files.mapNotNull { file ->
            generateSequence(file.parentFile) { it.parentFile }.firstOrNull { it.name in setOf("kotlin", "java") }
                ?.takeIf { it.parentFile.name in allowed }
        }.distinct().sortedBy { if (it.parentFile.name in variants) 0 else 1 }
    }

    private fun relative(file: File): String = file.relativeTo(repositoryDirectory.get().asFile).invariantSeparatorsPath

    private fun properties(file: File): Properties = Properties().apply { file.inputStream().buffered().use(::load) }

    private fun attempt(errors: MutableList<String>, label: String, block: () -> Unit) {
        try { block() } catch (failure: Exception) { errors.add("$label: ${failure.message}") }
    }
}

private data class JvmDataset(val classSet: String, val originals: File, val binaries: List<File>)

internal fun escapeCoverageHtml(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;")
    .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
