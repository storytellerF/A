plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.serialization)
    application
    alias(libs.plugins.kotlinBuildConfig)
    id("io.sentry.jvm.gradle")
    id("cloud")
    id("merge-services")
}

group = "com.storyteller_f.a.cloud"
version = "unspecified"

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

// configurations.all {
//    resolutionStrategy {
//        force(libs.bcprov.jdk18on)
//        force(libs.bcpkix.jdk18on)
//        dependencySubstitution {
//             substitute(module("org.bouncycastle:bcprov-jdk15on")).using(module("org.bouncycastle:bcprov-jdk18on:1.83"))
//             substitute(module("org.bouncycastle:bcpkix-jdk15on")).using(module("org.bouncycastle:bcpkix-jdk18on:1.83"))
//             substitute(module("org.bouncycastle:bcprov-jdk15to18")).using(module("org.bouncycastle:bcprov-jdk18on:1.83"))
//             substitute(module("org.bouncycastle:bcpkix-jdk15to18")).using(module("org.bouncycastle:bcpkix-jdk18on:1.83"))
//        }
//    }
// }

dependencies {
    implementation(libs.napier)
    implementation(libs.cryptography.provider.jdk)
    implementation(projects.shared)
    implementation(projects.api)
    implementation(projects.cloud.pdf)
    implementation(projects.cloud.openpdf)
    implementation(libs.endpoint4k.common)
    implementation(libs.endpoint4k.ktor.server)
    implementation(projects.backend.core)
    implementation(projects.backend.exposed)
    implementation(projects.cloud.service)
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.server.client)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.datetime)
    implementation(libs.bundles.exposed)

    implementation(libs.geoip2)
    implementation(libs.tika.core)
    implementation(libs.micrometer.registry.prometheus)

    @Suppress("VulnerableLibrariesLocal", "RedundantSuppression")
    testImplementation(libs.mysql.connector.java)
    testImplementation(projects.client.core)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(kotlin("test"))
    @Suppress("VulnerableLibrariesLocal", "RedundantSuppression")
    testImplementation(libs.testcontainers.elasticsearch)
    testImplementation(libs.testcontainers.minio)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.mysql)
    testImplementation(libs.sql.formatter)
    testImplementation(libs.javacv.platform)
    testImplementation(projects.cloud.pdfbox)
    testImplementation(libs.pdfbox) {
//        exclude(group = "org.bouncycastle")
    }
    testImplementation(libs.pdfcompare) {
//        exclude(group = "org.bouncycastle")
    }
    testImplementation(libs.commons.imaging)
    testImplementation(projects.cloud.worker)
    testImplementation(projects.cloud.cli)
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "3096m"
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

sentry {
    org = "acommunity"
    projectName = "kotlin"
}
