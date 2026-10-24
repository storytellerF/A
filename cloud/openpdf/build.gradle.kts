/*
 * This is a private project. All rights reserved.
 */

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.serialization)
}

group = "com.storyteller_f.a.cloud"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(projects.shared)
    implementation(libs.kotlinx.datetime)
    implementation(projects.cloud.pdf)
    implementation(libs.markdown)
    implementation(libs.openpdf)
    implementation(libs.highlights)
    implementation(libs.jna)
    testImplementation(kotlin("test"))
    testImplementation(libs.pdfbox)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Explicit maintenance command; ordinary builds consume the checked-in OFL assets.
tasks.register<JavaExec>("generateBundledPdfFonts") {
    group = "font assets"
    description = "Regenerate static PDF faces from pinned variable-font fixtures using public HarfBuzz APIs."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.storyteller_f.a.cloud.openpdf.PdfFontAssetsKt")
    args(layout.projectDirectory.asFile.absolutePath)
    inputs.files(fileTree("src/test/resources/fonts") { include("*.ttf") })
    outputs.files(
        listOf("serif-regular", "serif-bold", "mono-regular", "mono-bold").map {
            layout.projectDirectory.file("src/main/resources/fonts/$it.ttf")
        },
    )
    // Native version is selected at execution, so never reuse outputs from a different engine.
    outputs.upToDateWhen { false }
}
