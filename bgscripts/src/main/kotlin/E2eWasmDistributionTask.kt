/*
 * This is a private project. All rights reserved.
 */

import com.google.gson.JsonParser
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.nio.file.Files
import java.security.MessageDigest
import javax.inject.Inject

/** The copied distribution is immutable during browser execution; ordinary webpack output is untouched. */
abstract class E2eWasmDistributionTask @Inject constructor(
    private val files: FileSystemOperations,
    private val processes: ExecOperations,
) : DefaultTask() {
    @get:Input abstract val program: Property<String>
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDirectory: DirectoryProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val originalWasm: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val originalMap: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val tool: RegularFileProperty
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection
    @get:Internal abstract val repositoryDirectory: DirectoryProperty
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun prepare() {
        val output = outputDirectory.get().asFile
        resetCoverageDirectory(output)
        files.sync { from(inputDirectory); into(output) }
        val original = originalWasm.get().asFile
        val digest = hash(original.readBytes())
        val candidates = output.walkTopDown().filter { it.isFile && it.extension == "wasm" && hash(it.readBytes()) == digest }.toList()
        check(candidates.size == 1) { "Webpack and original Wasm differ; build one immutable development distribution at a time" }
        val bundled = candidates.single()
        processes.exec {
            commandLine(tool.get().asFile, original, originalMap.get().asFile, bundled,
                output.resolve("e2e-coverage-manifest.json"), repositoryDirectory.get().asFile)
        }.assertNormalExitValue()
        val manifestFile = output.resolve("e2e-coverage-manifest.json")
        val manifest = JsonParser.parseString(manifestFile.readText()).asJsonObject
        manifest.addProperty("program", program.get())
        manifest.addProperty("sourceFingerprint", coverageSourceFingerprint(sourceFiles.files))
        manifestFile.writeText(manifest.toString())
        val newName = "${hash(bundled.readBytes()).take(20)}.wasm"
        val oldName = bundled.name
        Files.move(bundled.toPath(), bundled.resolveSibling(newName).toPath())
        output.walkTopDown().filter { it.isFile && it.extension in setOf("js", "mjs", "html") }.forEach { file ->
            val content = file.readText()
            if (oldName in content) file.writeText(content.replace(oldName, newName))
        }
    }

    private fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
