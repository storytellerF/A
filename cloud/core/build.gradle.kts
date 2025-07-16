plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.serialization)
    application
    alias(libs.plugins.kotlinBuildConfig)
    id("io.sentry.jvm.gradle") version ("5.8.0")
}

group = "com.storyteller_f.a.cloud"
version = "1.0.0"
application {
    mainClass.set("com.storyteller_f.a.cloud.server.ApplicationKt")
    applicationDefaultJvmArgs = listOf("--add-modules", "jdk.incubator.vector")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

dependencies {
    implementation(libs.napier)
    implementation(libs.cryptography.provider.jdk)
    implementation(libs.bcpkix.jdk18on)
    implementation(projects.shared)
    implementation(projects.api.core)
    implementation(projects.backend.core)
    implementation(projects.backend.exposed)
    implementation(projects.backend.service)
    implementation(libs.bundles.exposed)
    implementation(libs.pdfbox)
    implementation(libs.kotlinx.collections.immutable)

    implementation(libs.geoip2)
    implementation(libs.tika.core)
    implementation(libs.pdfbox.layout)
}



tasks.withType<JavaExec> {
    jvmArgs = listOf("--add-modules", "jdk.incubator.vector")
}

tasks.withType<Test> {
    jvmArgs = listOf("--add-modules", "jdk.incubator.vector")
}

val buildType = project.findProperty("server.buildType") as String
val flavor = project.findProperty("buildkonfig.flavor").toString()

buildConfig {
    className = "ServerConfig"
    packageName = "com.storyteller_f.a.cloud.server"
    buildConfigField<String>("BUILD_TYPE", buildType)
    buildConfigField<Boolean>("IS_PROD", buildType == "prod")
    buildConfigField<String>("FLAVOR", flavor)
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
tasks.withType<Tar> {
    filesMatching("core.jar") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE // 排除重复项
    }
}

tasks.withType<Zip> {
    filesMatching("core.jar") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE // 排除重复项
    }
}
sentry {
    org = "acommunity"
    projectName = "kotlin"
}