import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class WriteAppiumRuntimeClasspathTask : DefaultTask() {
    @get:Classpath
    abstract val classpath: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun writeClasspath() {
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(classpath.files.joinToString(File.pathSeparator) { it.absolutePath })
        }
    }
}

tasks.register<WriteAppiumRuntimeClasspathTask>("writeAppiumRuntimeClasspath") {
    dependsOn(tasks.named("classes"))
    classpath.from(
        layout.buildDirectory.dir("classes/kotlin/main"),
        layout.buildDirectory.dir("resources/main"),
        configurations.named("runtimeClasspath"),
    )
    outputFile.set(layout.buildDirectory.file("appium/runtimeClasspath.txt"))
}
