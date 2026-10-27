/*
 * This is a private project. All rights reserved.
 */

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Input
import org.gradle.api.provider.Property
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/** Immutable instrumented classpath plus the matching original business bytecode for reports. */
abstract class E2eJvmCoverageTask : DefaultTask() {
    @get:Input abstract val classSet: Property<String>
    @get:Classpath abstract val inputClasspath: ConfigurableFileCollection
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection
    @get:OutputDirectory abstract val runtimeDirectory: DirectoryProperty
    @get:OutputDirectory abstract val originalClasses: DirectoryProperty
    @get:OutputFile abstract val classpathFile: RegularFileProperty
    @get:LocalState abstract val scratchDirectory: DirectoryProperty

    @TaskAction
    fun prepare() {
        val output = runtimeDirectory.get().asFile
        val originals = originalClasses.get().asFile
        resetCoverageDirectory(output)
        resetCoverageDirectory(originals)
        val instrumenter = E2eCoverageFiles(sourceFiles.files, originals, scratchDirectory.get().asFile)
        // The JVM ignores absent entries, including Java output directories in Kotlin-only modules.
        val inputs = inputClasspath.files.filter(File::exists)
        instrumenter.prepare(inputs)
        val metadata = output.resolve("metadata").also { it.mkdirs() }
        val entries = inputs.mapIndexed { index, input ->
            val destination = output.resolve("$index/${input.name}")
            instrumenter.copy(input, destination)
            destination.absolutePath
        }
        check(originals.walkTopDown().any { it.extension == "class" }) { "No source-owned classes were instrumented" }
        val descriptor = createCoverageDescriptor(originals, classSet.get(), sourceFiles.files)
        metadata.resolve("e2e-coverage.properties").writeBytes(descriptor)
        originals.resolve("classes.properties").writeBytes(descriptor)
        classpathFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText((listOf(metadata.absolutePath) + entries).joinToString(File.pathSeparator))
        }
    }
}
