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
    mainClass.set("com.storyteller_f.a.server.ApplicationKt")
    applicationDefaultJvmArgs = listOf("--add-modules", "jdk.incubator.vector")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

dependencies {
    implementation(libs.cryptography.provider.jdk)
    implementation(libs.bcpkix.jdk18on)
    implementation(projects.shared)
    implementation(projects.backend)
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.server.client)
    implementation(libs.pdfbox)

    implementation(libs.geoip2)
    implementation(libs.tika.core)
    implementation(libs.pdfbox.layout)
    implementation(libs.h2)

    @Suppress("VulnerableLibrariesLocal", "RedundantSuppression")
    testImplementation(libs.mysql.connector.java)
    testImplementation(projects.clientLib)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test)
    @Suppress("VulnerableLibrariesLocal", "RedundantSuppression")
    testImplementation(libs.testcontainers.elasticsearch)
    testImplementation(libs.testcontainers.minio)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.mysql)
    testImplementation(libs.sql.formatter)
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