import com.android.build.gradle.internal.tasks.factory.dependsOn

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.serialization)
    alias(libs.plugins.kotlinBuildConfig)
    id("io.sentry.jvm.gradle") version ("5.8.0")
    id("me.champeau.jmh") version "0.7.3"
}

group = "com.storyteller_f.a.cloud"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

dependencies {
    implementation(libs.napier)
    implementation(libs.cryptography.provider.jdk)
    implementation(projects.shared)
    implementation(projects.api)
    implementation(projects.backend.core)
    implementation(projects.cloud.pdf)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.tika.core)
    implementation(projects.backend.elastic)
    implementation(projects.backend.filesystem)
    implementation(projects.backend.lucene)
    implementation(projects.backend.minio)
    implementation(projects.backend.redis)
    implementation(projects.backend.simple)
    runtimeOnly(libs.h2)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.commons.logging)
    testImplementation(kotlin("test"))
    testImplementation(libs.testcontainers.elasticsearch)
}

tasks.withType<Test> {
    jvmArgs = listOf("--add-modules", "jdk.incubator.vector")
}

val buildType = project.findProperty("server.buildType") as String
val flavor = project.findProperty("server.flavor").toString()

buildConfig {
    className = "ServerConfig"
    packageName = "com.storyteller_f.a.cloud.server"
    buildConfigField<String>("BUILD_TYPE", buildType)
    buildConfigField<Boolean>("IS_PROD", buildType == "prod")
    buildConfigField<String>("FLAVOR", flavor)
}

sentry {
    org = "acommunity"
    projectName = "kotlin"
}

jmh {
    warmupIterations = 2
    iterations = 2
    fork = 2
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
            jarFile: File,
            servicePrefixName: String
        ): List<Pair<String?, List<String>>> {
            return injected.operations.zipTree(jarFile).filter {
                it.name.startsWith(servicePrefixName)
            }.map { serviceFile ->
                val serviceName = serviceFile.name
                val serviceContent = serviceFile.readText().lines().filter {
                    it.isNotEmpty() && !it.startsWith("#")
                }
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
}

tasks.processResources.dependsOn(mergeServiceFiles)
afterEvaluate {
    mergeServiceFiles.get().mustRunAfter(":api:jar")
    mergeServiceFiles.get().mustRunAfter(":shared:jvmJar")
    mergeServiceFiles.get().mustRunAfter(":backend:core:jar")
    mergeServiceFiles.get().mustRunAfter(":backend:elastic:jar")
    mergeServiceFiles.get().mustRunAfter(":backend:exposed:jar")
    mergeServiceFiles.get().mustRunAfter(":backend:filesystem:jar")
    mergeServiceFiles.get().mustRunAfter(":backend:lucene:jar")
    mergeServiceFiles.get().mustRunAfter(":backend:minio:jar")
    mergeServiceFiles.get().mustRunAfter(":backend:redis:jar")
    mergeServiceFiles.get().mustRunAfter(":backend:simple:jar")
    mergeServiceFiles.get().mustRunAfter(":cloud:pdf:jar")
}
