import com.android.build.gradle.internal.tasks.factory.dependsOn

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinBuildConfig)
    id("me.champeau.jmh") version "0.7.3"
}

group = "com.storyteller_f.a.backend"
version = "unspecified"

dependencies {
    implementation(libs.napier)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(projects.backend.core)

    implementation(projects.shared)
    implementation(libs.elasticsearch.java)
    implementation(libs.jackson.module.kotlin)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

sourceSets {
    main {
        resources {
            srcDirs(
                layout.buildDirectory.dir("merged/services")
            )
        }
    }
}

interface Injected {
    @get:Inject
    val operations: ArchiveOperations
}

val mergeServiceFiles = tasks.register("mergeServiceFiles") {
    group = "build"
    description = "Merge SPI files from dependencies into a single output"

    val outputDir = layout.buildDirectory.dir("merged/services").get()
    val runtimeClasspath = configurations.runtimeClasspath.get().files.toList()
    val injected = project.objects.newInstance<Injected>()

    inputs.files(runtimeClasspath)
    outputs.dir(outputDir)
    doLast {
        fun extractServiceContent(
            file: File,
            servicePrefixName: String,
            classPrefixName: String
        ): List<Pair<String?, List<String>>> {
            return injected.operations.zipTree(file).filter {
                it.name.startsWith(servicePrefixName)
            }.map { serviceFile ->
                val serviceName = serviceFile.name
                val serviceContent =
                    serviceFile.readText().lines().filter { it.startsWith(classPrefixName) }
                serviceName to serviceContent
            }
        }

        val output = outputDir.asFile
        if (!output.exists() && !output.mkdirs()) {
            throw Exception("mkdirs falied: $output")
        }
        runtimeClasspath.flatMap { file ->
            when {
                !file.isFile || !file.name.endsWith(".jar") -> emptyList()

                file.name.startsWith("lucene") -> extractServiceContent(
                    file,
                    "org.apache.lucene.codecs",
                    "org.apache.lucene"
                )

                file.name.startsWith("r2dbc") -> extractServiceContent(
                    file,
                    "io.r2dbc.spi.ConnectionFactoryProvider",
                    "io.r2dbc"
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
}

tasks.processResources.dependsOn(mergeServiceFiles)
afterEvaluate {
    mergeServiceFiles.get().mustRunAfter(":shared:jvmJar")
    mergeServiceFiles.get().mustRunAfter(":backend:core:jar")
    mergeServiceFiles.get().mustRunAfter(":backend:exposed:jar")
}

val flavor = project.findProperty("server.flavor").toString()

buildConfig {
    className = "BackendConfig"
    buildConfigField<String>("FLAVOR", flavor)
}

tasks.withType<Test> {
    jvmArgs = listOf("--add-modules", "jdk.incubator.vector")
}

jmh {
    warmupIterations = 2
    iterations = 2
    fork = 2
}

