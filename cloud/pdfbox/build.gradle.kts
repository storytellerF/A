plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.serialization)
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
    implementation(libs.bcpkix.jdk18on)
    implementation(libs.napier)
    implementation(projects.shared)
    implementation(libs.kotlinx.datetime)
    implementation(projects.cloud.pdf)
    implementation(libs.markdown)
    implementation(libs.pdfbox.layout)
    implementation(libs.pdfbox)
    implementation(libs.highlights)
}
