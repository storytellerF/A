plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.serialization)
    application
    alias(libs.plugins.kotlinBuildConfig)
}

group = "com.storyteller_f.a.app"
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
    implementation(libs.napier)

    implementation(projects.shared)
    implementation(libs.bundles.ktor.server)

    implementation(projects.app.dev)
}

val buildType = project.findProperty("server.buildType") as String

buildConfig {
    buildConfigField<String>("BUILD_TYPE", buildType)
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