/*
 * This is a private project. All rights reserved.
 */

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Input
import org.gradle.api.provider.Property
import java.nio.file.FileSystems
import java.nio.file.Files
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Copies and instruments an E2E distribution without mutating installDist or release archives. */
abstract class E2eCoverageDistributionTask : DefaultTask() {
    @get:Input abstract val classSet: Property<String>
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDirectory: DirectoryProperty
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @get:OutputDirectory abstract val originalClasses: DirectoryProperty
    @get:LocalState abstract val scratchDirectory: DirectoryProperty

    @TaskAction
    fun prepare() {
        val source = inputDirectory.get().asFile
        val target = outputDirectory.get().asFile
        val originals = originalClasses.get().asFile
        resetCoverageDirectory(target)
        resetCoverageDirectory(originals)
        val instrumenter = E2eCoverageFiles(sourceFiles.files, originals, scratchDirectory.get().asFile)
        instrumenter.prepare(source.walkTopDown().filter { it.isFile && it.extension == "jar" }.toList())
        source.walkTopDown().filter { it.isFile }.forEach { input ->
            val output = target.resolve(input.relativeTo(source))
            instrumenter.copy(input, output)
            if (input.canExecute()) output.setExecutable(true, false)
        }
        check(originals.walkTopDown().any { it.extension == "class" }) { "Distribution contains no source-owned bytecode" }
        val descriptor = createCoverageDescriptor(originals, classSet.get(), sourceFiles.files)
        originals.resolve("classes.properties").writeBytes(descriptor)
        val jar = instrumenter.modifiedJars.firstOrNull() ?: error("No source-owned JAR was instrumented")
        FileSystems.newFileSystem(jar.toPath(), emptyMap<String, String>()).use { zip ->
            Files.write(zip.getPath("/e2e-coverage.properties"), descriptor)
        }
        prepareCoverageWindowsLaunchers(target)
    }
}
