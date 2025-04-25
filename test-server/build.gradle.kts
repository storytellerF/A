plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.serialization)
    application
    alias(libs.plugins.kotlinBuildConfig)
}

group = "com.storyteller_f.a"
version = "1.0.0"
application {
    mainClass.set("com.storyteller_f.a.test_server.TestServerApplicationKt")
    applicationDefaultJvmArgs = listOf("--add-modules", "jdk.incubator.vector")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.logback)

    implementation(libs.cryptography.provider.jdk)
    implementation(libs.bcpkix.jdk18on)
    implementation(projects.shared)
    implementation(projects.backend)
    implementation(libs.bundles.ktor.server)
    implementation(libs.pdfbox)
    implementation(libs.napier)
    
    implementation(libs.geoip2)
    implementation(libs.simplemagic)
    implementation(libs.pdfbox.layout)
    implementation(libs.h2)

    implementation(projects.testServer.localServerLib)
}

val isProd = project.findProperty("server.prod") == true

buildConfig {
    buildConfigField<Boolean>("IS_PROD", isProd)
}

tasks.withType<JavaExec> {
    jvmArgs = listOf("--add-modules", "jdk.incubator.vector")
    standardInput = System.`in`
}

tasks.withType<Tar> {
    filesMatching("vavi-commons-1.1.10.jar") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE // 排除重复项
    }
}

tasks.withType<Zip> {
    filesMatching("vavi-commons-1.1.10.jar") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE // 排除重复项
    }
}