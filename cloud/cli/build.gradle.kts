plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

group = "com.storyteller_f.a.cloud"
version = "unspecified"

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)

    implementation(projects.shared)
    implementation(projects.backend.core)
    implementation(projects.backend.exposed)
    implementation(projects.cloud.service)
    implementation(libs.napier)
    implementation(libs.kotlinx.cli)
    implementation(libs.cryptography.provider.jdk)
    implementation(libs.tika.core)
    implementation(libs.bundles.ktor.server.client)
    implementation(libs.kotlinx.datetime)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "com.storyteller_f.a.cloud.cli.MainKt"
    applicationDefaultJvmArgs = listOf("--add-modules", "jdk.incubator.vector")
}

tasks.withType<JavaExec> {
    jvmArgs = listOf("--add-modules", "jdk.incubator.vector")
}

fun AbstractCopyTask.handleDupJar() {
    filesMatching("vavi-commons-1.1.10.jar") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE // 排除重复项
    }
}

tasks.withType<Tar> {
    handleDupJar()
}

tasks.withType<Zip> {
    handleDupJar()
}

tasks.withType<Sync> {
    handleDupJar()
}
