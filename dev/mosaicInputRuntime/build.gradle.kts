/*
 * This is a private project. All rights reserved.
 */

import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.FilterReader
import java.io.Reader
import java.io.StringReader
import java.security.MessageDigest
import java.util.HexFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { alias(libs.plugins.kotlinMultiplatform) }

group = "com.jakewharton.mosaic"
version = "${libs.versions.mosaicRuntime.get()}-input1"

val upstreamVersion = libs.versions.mosaicRuntime.get()
val upstreamSources = configurations.create("upstreamSources") {
    isCanBeConsumed = false
    resolutionStrategy.useGlobalDependencySubstitutionRules = false
}
dependencies {
    upstreamSources("com.jakewharton.mosaic:mosaic-tty-terminal-jvm:$upstreamVersion:sources@jar")
}

abstract class VerifyMosaicArchive : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val archive: RegularFileProperty

    @TaskAction
    fun verify() {
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
            "bc09d05d7cf0f1ad11f74e5e47417e7ede39e925fe2d70b2343b753d00877b49") {
            "Unexpected Mosaic source archive; review the input patch before upgrading"
        }
    }
}

abstract class MosaicArchiveOperations {
    @get:javax.inject.Inject
    abstract val archives: org.gradle.api.file.ArchiveOperations
}

// Workaround for https://github.com/JakeWharton/mosaic/issues/1241; remove when an upstream release preserves burst keyboard input.
class LosslessMosaicInput(reader: Reader) : FilterReader(StringReader(
    reader.readText()
        .replace("import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST", "")
        .replace("Channel<Event>(64, onBufferOverflow = DROP_OLDEST)", "Channel<Event>(64)")
        .replace("events.trySend(event)", "events.send(event)")
))

val verifySources = tasks.register<VerifyMosaicArchive>("verifySources") {
    archive.set(layout.file(upstreamSources.elements.map { it.single().asFile }))
}
val archives = objects.newInstance<MosaicArchiveOperations>().archives
val prepareSources = tasks.register<Sync>("prepareSources") {
    val archiveOperations = archives
    dependsOn(verifySources)
    inputs.property("inputPolicy", "bounded-lossless-events-v1")
    from(upstreamSources.elements.map { files -> files.map { archiveOperations.zipTree(it.asFile) } }) {
        include("commonMain/**/*.kt", "jvmMain/**/*.kt")
        filesMatching("commonMain/**/TtyTerminal.kt") {
            filter(LosslessMosaicInput::class.java)
        }
    }
    into(layout.buildDirectory.dir("upstream"))
}

kotlin {
    jvmToolchain(21)
    compilerOptions.optIn.addAll("kotlin.contracts.ExperimentalContracts", "kotlin.ExperimentalStdlibApi")
    jvm { compilerOptions.jvmTarget.set(JvmTarget.JVM_21) }
    sourceSets {
        commonMain {
            kotlin.srcDir(prepareSources.map { it.destinationDir.resolve("commonMain") })
            languageSettings.optIn("kotlin.contracts.ExperimentalContracts")
            languageSettings.optIn("kotlin.ExperimentalStdlibApi")
            dependencies {
                api("com.jakewharton.mosaic:mosaic-terminal:$upstreamVersion")
                api("com.jakewharton.mosaic:mosaic-tty:$upstreamVersion")
                implementation("com.jakewharton.finalization:finalization-hook:0.1.0")
                api(libs.kotlinx.coroutines.core)
            }
        }
        jvmMain { kotlin.srcDir(prepareSources.map { it.destinationDir.resolve("jvmMain") }) }
    }
}

tasks.withType<Jar>().configureEach {
    from(listOf("LICENSE", "NOTICE")) { into("META-INF") }
}
