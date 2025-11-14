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
    implementation(libs.commons.imaging)
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
