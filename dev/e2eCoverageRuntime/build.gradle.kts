/*
 * This is a private project. All rights reserved.
 */

plugins { `java-library` }

java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }

dependencies {
    implementation("org.jetbrains.kotlinx:kover-offline-runtime:${libs.versions.kover.get()}")
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    systemProperty("coverage.test.directory", layout.buildDirectory.dir("test-runtime").get().asFile.absolutePath)
}
