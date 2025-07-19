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
    implementation(libs.bundles.exposed)
    implementation(libs.kotlinx.serialization.json)
    implementation(projects.backend.core)
    implementation(projects.backend.exposed)

    implementation(projects.shared)
    implementation(libs.minio)
    implementation(libs.elasticsearch.java)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.bundles.lucene)
    implementation(libs.tika.core)
    runtimeOnly(libs.vavi.image.avif)
    implementation(libs.memoryfilesystem)
    implementation(libs.kotlinx.coroutines.slf4j)
    implementation(libs.kreds)

    testImplementation(libs.commons.logging)
    testImplementation(kotlin("test"))
    testImplementation(libs.testcontainers.elasticsearch)
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

val mergeServiceFiles = tasks.register("mergeServiceFiles") {
    group = "build"
    description = "Merge SPI files from dependencies into a single output"

    val outputDir = layout.buildDirectory.dir("merged/services")

    inputs.files(configurations.runtimeClasspath)
    outputs.dir(outputDir)
    doLast {

        // Iterate through each jar/zip in runtimeClasspath
        val output = outputDir.get().asFile
        output.mkdirs()
        configurations.runtimeClasspath.get().flatMap { file ->
            when {
                !file.isFile || !file.name.endsWith(".jar") -> {
                    emptyList()
                }

                file.name.startsWith("lucene") -> {
                    extractServiceContent(file, "org.apache.lucene.codecs", "org.apache.lucene")
                }

                file.name.startsWith("r2dbc") -> {
                    extractServiceContent(
                        file,
                        "io.r2dbc.spi.ConnectionFactoryProvider",
                        "io.r2dbc"
                    )
                }

                else -> emptyList()
            }
        }.groupBy {
            it.first
        }.forEach { (serviceName, fragments) ->
            val outputFile = output.resolve("META-INF/services/${serviceName}")
            outputFile.parentFile.mkdirs()

            val newContent = fragments.flatMap { it.second }.joinToString("\n")

            // ✨ Only write if content differs
            if (!outputFile.exists() || outputFile.readText() != newContent) {
                outputFile.writeText(newContent)
            }
        }
    }
}

tasks.processResources.dependsOn(mergeServiceFiles)

val flavor = project.findProperty("buildkonfig.flavor").toString()

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

fun extractServiceContent(
    file: File,
    serviceFullName: String,
    keyword: String
): List<Pair<String?, List<String>>> {
    return zipTree(file).filter {
        it.name.startsWith(serviceFullName)
    }.map { serviceFile ->
        val serviceName = serviceFile.name
        val serviceContent =
            serviceFile.readText().lines().filter { it.startsWith(keyword) }
        serviceName to serviceContent
    }
}