plugins {
    alias(libs.plugins.kotlinJvm)
}

val prepareWasmDistribution by tasks.registering(Sync::class) {
    dependsOn(":panel:composeApp:wasmJsBrowserDevelopmentWebpack")
    from(project(":panel:composeApp").layout.buildDirectory.dir("kotlin-webpack/wasmJs/developmentExecutable"))
    from(rootProject.layout.buildDirectory.file("wasm/packages/A-panel-composeApp/kotlin/index.html"))
    from(rootProject.layout.buildDirectory.file("wasm/packages/A-panel-composeApp/kotlin/styles.css"))
    from(rootProject.layout.buildDirectory.dir("wasm/packages/A-panel-composeApp/kotlin/composeResources")) {
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
