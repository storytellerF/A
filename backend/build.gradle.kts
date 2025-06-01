import com.android.build.gradle.internal.tasks.factory.dependsOn

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinBuildConfig)
}

group = "com.storyteller_f"
version = "unspecified"

dependencies {
    api(libs.exposed.core)
    api(libs.exposed.dao)
    api(libs.exposed.crypt)
    api(libs.exposed.jdbc)
    api(libs.exposed.kotlin.datetime)
    api(libs.exposed.json)
    api(libs.exposed.money)

    implementation(libs.pgjdbc.ng)
    implementation(libs.postgresql)
    implementation(projects.shared)
    implementation(libs.minio)
    implementation(libs.elasticsearch.java)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.lucene.core)
    implementation(libs.lucene.queryparser)
    implementation(libs.lucene.analysis.common)
    implementation(libs.tika.core)
    implementation(libs.kim)
    runtimeOnly(libs.vavi.image.avif)
    implementation(libs.memoryfilesystem)
    implementation(libs.lucene.backward.codecs)
    implementation(libs.kotlinx.coroutines.slf4j)

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
    doLast {

        // Iterate through each jar/zip in runtimeClasspath
        val output = outputDir.get().asFile
        output.mkdirs()
        configurations.runtimeClasspath.get().flatMap { file ->
            if (file.isFile && file.name.endsWith(".jar")) {
                zipTree(file).filter {
                    it.name.startsWith("org.apache.lucene.codecs")
                }.map { serviceFile ->
                    val serviceName = serviceFile.name
                    val serviceContent =
                        serviceFile.readText().lines().filter { it.startsWith("org.apache.lucene") }
                    serviceName to serviceContent
                }
            } else {
                emptyList()
            }
        }.groupBy {
            it.first
        }.forEach {
            val outputFile = output.resolve("META-INF/services/${it.key}")
            outputFile.parentFile.mkdirs()

            outputFile.writeText(it.value.flatMap {
                it.second
            }.joinToString("\n"))
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