/*
 * This is a private project. All rights reserved.
 */

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class WriteAppiumRuntimeClasspathTask : DefaultTask() {
    @get:Classpath abstract val classpath: ConfigurableFileCollection
    @get:Optional @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val coverageClasspathFile: RegularFileProperty
    @get:OutputFile abstract val outputFile: RegularFileProperty

    @TaskAction
    fun writeClasspath() {
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(coverageClasspathFile.orNull?.asFile?.readText()
                ?: classpath.files.joinToString(File.pathSeparator) { it.absolutePath })
        }
    }
}
