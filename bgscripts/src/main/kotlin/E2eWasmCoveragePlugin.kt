/*
 * This is a private project. All rights reserved.
 */

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.register

class E2eWasmCoveragePlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val program = if (path.startsWith(":panel:")) "panelWebApp" else "appWebApp"
        val windows = providers.systemProperty("os.name").map { it.startsWith("Windows") }.get()
        val executable = rootProject.layout.projectDirectory.file(
            "dev/wasmCoverage/build/target/debug/a-wasm-coverage${if (windows) ".exe" else ""}",
        )
        val projectLayout = layout
        val prepared = tasks.register<E2eWasmDistributionTask>("prepareE2eCoverageWasmDistribution") {
            group = "verification"
            this.program.set(program)
            dependsOn("prepareWasmDistribution", ":buildWasmCoverageTool")
            inputDirectory.set(projectLayout.buildDirectory.dir("wasmDistribution"))
            originalWasm.set(rootProject.layout.buildDirectory.file("wasm/packages/$program/kotlin/$program.wasm"))
            originalMap.set(rootProject.layout.buildDirectory.file("wasm/packages/$program/kotlin/$program.wasm.map"))
            tool.set(executable)
            repositoryDirectory.set(rootProject.layout.projectDirectory)
            sourceFiles.from(rootProject.fileTree(rootProject.projectDir) {
                include("**/src/main/**/*.kt", "**/src/main/**/*.java", "**/src/*Main/**/*.kt", "**/src/*Main/**/*.java")
                exclude(".worktrees/**", "**/build/**", "dev/e2eCoverageRuntime/**")
            })
            outputDirectory.set(projectLayout.buildDirectory.dir("e2e-coverage/wasmDistribution"))
        }
        tasks.withType(Test::class.java).matching { it.name == "appiumTest" }.configureEach {
            dependsOn(prepared)
            systemProperty("e2e.coverage.wasmDistribution",
                projectLayout.buildDirectory.dir("e2e-coverage/wasmDistribution").get().asFile.absolutePath)
        }
    }
}
