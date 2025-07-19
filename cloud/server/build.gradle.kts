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
    implementation(projects.api.server)
    implementation(projects.backend.core)
    implementation(projects.backend.exposed)
    implementation(projects.backend.service)
    implementation(projects.cloud.core)
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.server.client)
    implementation(libs.bundles.exposed)
    implementation(libs.pdfbox)
    implementation(libs.kotlinx.collections.immutable)

    implementation(libs.geoip2)
    implementation(libs.tika.core)
    implementation(libs.pdfbox.layout)
    implementation(libs.micrometer.registry.prometheus)

    @Suppress("VulnerableLibrariesLocal", "RedundantSuppression")
    testImplementation(libs.mysql.connector.java)
    testImplementation(projects.client.core)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test)
    @Suppress("VulnerableLibrariesLocal", "RedundantSuppression")
    testImplementation(libs.testcontainers.elasticsearch)
    testImplementation(libs.testcontainers.minio)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.mysql)
    testImplementation(libs.sql.formatter)
    testImplementation(libs.javacv.platform)
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
    eachFile {
        if (sourceName == "core.jar") {
            val filePath = file.path
            when {
                filePath.contains("api") -> name = "api-core.jar"
                filePath.contains("backend") -> name = "backend-core.jar"
                filePath.contains("cloud") -> name = "cloud-core.jar"
            }
        }
    }
}

tasks.withType<Zip> {
    eachFile {
        if (sourceName == "core.jar") {
            val filePath = file.path
            when {
                filePath.contains("api") -> name = "api-core.jar"
                filePath.contains("backend") -> name = "backend-core.jar"
                filePath.contains("cloud") -> name = "cloud-core.jar"
            }
        }
    }
}
sentry {
    org = "acommunity"
    projectName = "kotlin"
}