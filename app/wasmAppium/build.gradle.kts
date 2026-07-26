plugins {
    alias(libs.plugins.kotlinJvm)
}

val prepareWasmDistribution by tasks.registering(Sync::class) {
    dependsOn(":app:composeApp:wasmJsBrowserDevelopmentWebpack")
    from(project(":app:composeApp").layout.buildDirectory.dir("kotlin-webpack/wasmJs/developmentExecutable"))
    from(rootProject.layout.buildDirectory.file("wasm/packages/composeApp/kotlin/index.html"))
    from(rootProject.layout.buildDirectory.file("wasm/packages/composeApp/kotlin/styles.css"))
    from(rootProject.layout.buildDirectory.dir("wasm/packages/composeApp/kotlin/composeResources")) {
        into("composeResources")
    }
    into(layout.buildDirectory.dir("wasmDistribution"))
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(project(":dev:appiumCore"))
    testImplementation(libs.selenium.firefox.driver)
}

tasks.test {
    dependsOn(prepareWasmDistribution)
    jvmArgs("--add-modules", "jdk.httpserver")
    maxParallelForks = 1
}
