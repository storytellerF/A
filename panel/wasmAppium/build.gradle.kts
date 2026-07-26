plugins {
    alias(libs.plugins.kotlinJvm)
}

val prepareWasmDistribution = tasks.register<Sync>("prepareWasmDistribution") {
    dependsOn(":panel:webApp:wasmJsBrowserDevelopmentWebpack")
    from(project(":panel:webApp").layout.buildDirectory.dir("kotlin-webpack/wasmJs/developmentExecutable"))
    from(rootProject.layout.buildDirectory.file("wasm/packages/panelWebApp/kotlin/index.html"))
    from(rootProject.layout.buildDirectory.file("wasm/packages/panelWebApp/kotlin/styles.css"))
    from(rootProject.layout.buildDirectory.dir("wasm/packages/panelWebApp/kotlin/composeResources")) {
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
