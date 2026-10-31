plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins.create("e2eJvmCoverage") {
        id = "e2e-jvm-coverage"
        implementationClass = "E2eJvmCoveragePlugin"
    }
    plugins.create("e2eAndroidCoverage") {
        id = "e2e-android-coverage"
        implementationClass = "E2eAndroidCoveragePlugin"
    }
    plugins.create("e2eWasmCoverage") {
        id = "e2e-wasm-coverage"
        implementationClass = "E2eWasmCoveragePlugin"
    }
}

dependencies {
    implementation(libs.gradle)
    implementation("org.jetbrains.kotlinx:kover-features-jvm:${libs.versions.kover.get()}")
    implementation("org.ow2.asm:asm:9.11.1")
    implementation("com.google.code.gson:gson:2.15.0")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.plugins.kotlinMultiplatform.get().version}")
    // https://github.com/gradle/gradle/issues/15383#issuecomment-779893192
    implementation(files(libs::class.java.superclass.protectionDomain.codeSource.location))
}


kotlin { jvmToolchain(21) }
sourceSets.test { java.srcDir("../dev/e2eCoverageRuntime/src/main/java") }
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kover-offline-runtime:${libs.versions.kover.get()}")
}
tasks.test {
    maxParallelForks = 1
    systemProperty("coverage.test.directory", layout.buildDirectory.dir("coverage-tests").get().asFile.absolutePath)
}
