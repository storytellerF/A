
import org.gradle.api.file.ArchiveOperations
import org.gradle.kotlin.dsl.register
import javax.inject.Inject

abstract class MergeServicesTask : DefaultTask() {

    @get:Inject
    abstract val operations: ArchiveOperations

    @OutputDirectory
    lateinit var output: File

    @InputFiles
    lateinit var runtimeClasspath: List<File>

    init {
        group = "build"
        description = "Merge SPI files from dependencies into a single output"
    }

    @TaskAction
    fun taskAction() {
        if (!output.exists() && !output.mkdirs()) {
            throw Exception("mkdirs failed: $output")
        }

        runtimeClasspath.flatMap { file ->
            when {
                !file.isFile || !file.name.endsWith(".jar") -> emptyList()
                file.canonicalPath.contains("backend") -> extractServiceContent(
                    file,
                    "com.storyteller_f.a.backend.core.service"
                )

                file.name.startsWith("lucene-") -> extractServiceContent(
                    file,
                    "org.apache.lucene.codecs"
                )

                file.name.startsWith("r2dbc") -> extractServiceContent(
                    file,
                    "io.r2dbc.spi.ConnectionFactoryProvider"
                )

                else -> emptyList()
            }
        }.groupBy {
            it.first
        }.forEach { (serviceName, fragments) ->
            val outputFile = output.resolve("META-INF/services/${serviceName}")
            outputFile.parentFile.mkdirs()

            val newContent = fragments.flatMap { it.second }.joinToString("\n")

            if (!outputFile.exists() || outputFile.readText() != newContent) {
                outputFile.writeText(newContent)
            }
        }
    }

    private fun extractServiceContent(
        jarFile: File,
        servicePrefixName: String
    ): List<Pair<String?, List<String>>> {
        return operations.zipTree(jarFile).filter {
            it.name.startsWith(servicePrefixName)
        }.map { serviceFile ->
            val serviceName = serviceFile.name
            val serviceContent = serviceFile.readText().lines().filter {
                it.isNotEmpty() && !it.startsWith("#")
            }
            serviceName to serviceContent
        }
    }
}

tasks.register<MergeServicesTask>("mergeServiceFiles") {
    output = project.layout.buildDirectory.dir("merged/services").get().asFile
    runtimeClasspath = project.configurations.getByName("runtimeClasspath").files.toList()
}
