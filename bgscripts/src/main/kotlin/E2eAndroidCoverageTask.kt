/*
 * This is a private project. All rights reserved.
 */

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** AGP owns the transform boundary; D8 receives the instrumented class copies and normal resources. */
abstract class E2eAndroidCoverageTask : DefaultTask() {
    @get:Classpath abstract val allJars: ListProperty<RegularFile>
    @get:Classpath abstract val allDirectories: ListProperty<Directory>
    @get:Input abstract val classSet: Property<String>
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection
    @get:OutputFile abstract val outputJar: RegularFileProperty
    @get:OutputDirectory abstract val originalClasses: DirectoryProperty
    @get:OutputDirectory abstract val metadataDirectory: DirectoryProperty
    @get:LocalState abstract val scratchDirectory: DirectoryProperty

    @TaskAction
    fun instrument() {
        val originals = originalClasses.get().asFile
        val scratch = scratchDirectory.get().asFile
        resetCoverageDirectory(originals)
        resetCoverageDirectory(scratch)
        val inputs = allJars.get().map { it.asFile } + allDirectories.get().map { it.asFile }
        val instrumenter = E2eCoverageFiles(sourceFiles.files, originals, scratch.resolve("instrumented"))
        instrumenter.prepare(inputs)
        val copied = inputs.mapIndexed { index, input ->
            scratch.resolve("$index/${input.name}").also { instrumenter.copy(input, it) }
        }
        val destination = outputJar.get().asFile.also { it.parentFile.mkdirs() }
        val names = mutableSetOf<String>()
        ZipOutputStream(destination.outputStream().buffered()).use { output ->
            copied.forEach { entry ->
                if (entry.isDirectory) {
                    entry.walkTopDown().filter { it.isFile && isAndroidClass(it.relativeTo(entry).invariantSeparatorsPath) }.forEach { file ->
                        val name = file.relativeTo(entry).invariantSeparatorsPath
                        check(names.add(name)) { "Duplicate Android class $name" }
                        output.putNextEntry(ZipEntry(name))
                        output.write(file.readBytes())
                        output.closeEntry()
                    }
                } else {
                    ZipFile(entry).use { jar ->
                        jar.entries().asSequence().filter { !it.isDirectory && isAndroidClass(it.name) }.forEach { file ->
                            check(names.add(file.name)) { "Duplicate Android class ${file.name}" }
                            output.putNextEntry(ZipEntry(file.name))
                            jar.getInputStream(file).buffered().use { it.copyTo(output) }
                            output.closeEntry()
                        }
                    }
                }
            }
        }
        val descriptor = createCoverageDescriptor(originals, classSet.get(), sourceFiles.files)
        originals.resolve("classes.properties").writeBytes(descriptor)
        metadataDirectory.get().asFile.apply { mkdirs() }.resolve("e2e-coverage.properties").writeBytes(descriptor)
    }

    private fun isAndroidClass(name: String): Boolean =
        name.endsWith(".class") && !name.startsWith("META-INF/") && name != "module-info.class"
}
