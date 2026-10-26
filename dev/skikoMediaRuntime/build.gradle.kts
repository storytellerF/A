/*
 * This is a private project. All rights reserved.
 */

import java.io.BufferedInputStream
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.HexFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile

plugins {
    kotlin("multiplatform") version "2.4.21"
    kotlin("jvm") version "2.4.21" apply false
}
group = "org.jetbrains.skiko"
version = "0.150.2-media1"

val upstreamSources by configurations.creating {
    resolutionStrategy.useGlobalDependencySubstitutionRules = false
}
val upstreamVersion = "0.150.2"
dependencies {
    upstreamSources("org.jetbrains.skiko:skiko-wasm-js:$upstreamVersion:sources@jar")
}
abstract class VerifySourceArchive : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE)
    abstract val archive: RegularFileProperty
    @TaskAction fun verify() {
        val digest = MessageDigest.getInstance("SHA-256")
        BufferedInputStream(FileInputStream(archive.get().asFile)).use { stream ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        check(HexFormat.of().formatHex(digest.digest()) ==
            "a68a303b1638005b4a9094bd171c69c695816646bc0b9a50616b3d4bac54a174") {
            "Unexpected Skiko source archive"
        }
    }
}
val verifySourceArchive by tasks.registering(VerifySourceArchive::class) {
    archive.set(layout.file(upstreamSources.elements.map { it.single().asFile }))
}
val sourceArchives = upstreamSources.elements
val archiveOperations = objects.newInstance<ArchiveOperationsHolder>()

abstract class ArchiveOperationsHolder {
    @get:javax.inject.Inject abstract val archives: org.gradle.api.file.ArchiveOperations
}
val prepareSources by tasks.registering(Sync::class) {
    dependsOn(verifySourceArchive)
    val unpack = archiveOperations.archives
    from(sourceArchives.map { files -> files.map { unpack.zipTree(it.asFile) } }) {
        exclude("webMain/org/jetbrains/skiko/CanvasRenderer.kt", "webMain/org/jetbrains/skiko/SkiaLayer.web.kt")
    }
    from("patches") { into("webMain/org/jetbrains/skiko") }
    into(layout.buildDirectory.dir("upstream"))
}
kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }
    sourceSets {
        commonMain {
            kotlin.srcDir(prepareSources.map { it.destinationDir.resolve("commonMain") })
            dependencies { api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0") }
        }
        val nativeJsMain by creating {
            dependsOn(commonMain.get())
            kotlin.srcDir(prepareSources.map { it.destinationDir.resolve("nativeJsMain") })
        }
        val webMain by creating {
            dependsOn(nativeJsMain)
            kotlin.srcDir(prepareSources.map { it.destinationDir.resolve("webMain") })
            dependencies { api("org.jetbrains.kotlinx:kotlinx-browser:0.5.0") }
        }
        wasmJsMain {
            dependsOn(webMain)
            kotlin.srcDir(prepareSources.map { it.destinationDir.resolve("wasmJsMain") })
        }
    }
}
val generatedImports = layout.buildDirectory.file("imports/skiko-imports.mjs").get().asFile
val importPlugin = project(":import-generator").layout.buildDirectory.file("libs/import-generator.jar").get().asFile
tasks.named<Kotlin2JsCompile>("compileKotlinWasmJs") {
    dependsOn(":import-generator:jar")
    inputs.file(importPlugin)
    compilerOptions.optIn.addAll("org.jetbrains.skiko.InternalSkikoApi", "org.jetbrains.skiko.ExperimentalSkikoApi", "kotlin.js.ExperimentalWasmJsInterop")
    compilerOptions.freeCompilerArgs.addAll(
        "-Xplugin=${importPlugin.absolutePath}",
        "-P", "plugin:org.jetbrains.skiko.imports.generator:import-generator-path=${generatedImports.absolutePath}",
        "-P", "plugin:org.jetbrains.skiko.imports.generator:import-generator-module-name=skiko",
    )
    outputs.file(generatedImports)
}
