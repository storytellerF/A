plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

group = "com.storyteller_f"
version = "unspecified"

dependencies {
    runtimeOnly(libs.sqlite.jdbc)
    runtimeOnly(libs.slf4j.simple)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)

    implementation(projects.shared)
    implementation(projects.backend)
    implementation(libs.kotlinx.cli)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.cryptography.provider.jdk)
    implementation(libs.napier)
    implementation(libs.tika.core)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "com.storyteller_f.cli.MainKt"
    applicationDefaultJvmArgs = listOf("--add-modules", "jdk.incubator.vector")
}

tasks.withType<JavaExec> {
    jvmArgs = listOf("--add-modules", "jdk.incubator.vector")
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


tasks.withType<Sync> {
    filesMatching("vavi-commons-1.1.10.jar") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE // 排除重复项
    }
}