/*
 * This is a private project. All rights reserved.
 */

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Tar
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

class E2eJvmCoveragePlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("java")
        dependencies.add("runtimeOnly", project(":dev:e2eCoverageRuntime"))
        val java = extensions.getByType<JavaPluginExtension>()
        val sourceFiles = rootProject.fileTree(rootProject.projectDir) {
            include("**/src/main/**/*.kt", "**/src/main/**/*.java", "**/src/*Main/**/*.kt", "**/src/*Main/**/*.java")
            exclude(".worktrees/**", "**/build/**", "dev/e2eCoverageRuntime/**")
        }
        val projectLayout = layout
        val classSetName = target.path
        val instrumentation = gradle.sharedServices.registerIfAbsent("e2e-instrumentation", E2eInstrumentationService::class.java) {
            maxParallelUsages.set(1)
        }
        val preparedClasspath = tasks.register<E2eJvmCoverageTask>("prepareE2eCoverageClasspath") {
            group = "verification"
            classSet.set(classSetName)
            usesService(instrumentation)
            dependsOn(target.tasks.named("classes"))
            inputClasspath.from(java.sourceSets.getByName("main").runtimeClasspath)
            this.sourceFiles.from(sourceFiles)
            runtimeDirectory.set(projectLayout.buildDirectory.dir("e2e-coverage/runtime"))
            originalClasses.set(projectLayout.buildDirectory.dir("e2e-coverage/originals/classpath"))
            classpathFile.set(projectLayout.buildDirectory.file("e2e-coverage/classpath.txt"))
            scratchDirectory.set(projectLayout.buildDirectory.dir("e2e-coverage/scratch/classpath"))
        }
        tasks.withType(WriteAppiumRuntimeClasspathTask::class.java).configureEach {
            coverageClasspathFile.set(preparedClasspath.flatMap { it.classpathFile })
        }
        pluginManager.withPlugin("application") {
            target.afterEvaluate {
            val installed = tasks.named<Sync>("installDist")
            val prepared = tasks.register<E2eCoverageDistributionTask>("prepareE2eCoverageDistribution") {
                group = "verification"
                classSet.set(classSetName)
                usesService(instrumentation)
                dependsOn(installed)
                inputDirectory.set(projectLayout.dir(installed.map { it.destinationDir }))
                this.sourceFiles.from(sourceFiles)
                outputDirectory.set(projectLayout.buildDirectory.dir("e2e-coverage/install"))
                originalClasses.set(projectLayout.buildDirectory.dir("e2e-coverage/originals/distribution"))
                scratchDirectory.set(projectLayout.buildDirectory.dir("e2e-coverage/scratch/distribution"))
            }
            val distributionName = target.name
            tasks.register<Tar>("e2eCoverageDistTar") {
                from(prepared.flatMap { it.outputDirectory }) { into(distributionName) }
                archiveFileName.set("$distributionName.tar")
                destinationDirectory.set(projectLayout.buildDirectory.dir("e2e-coverage/distributions"))
                filesMatching("*/bin/*") { permissions { unix("rwxr-xr-x") } }
            }
            }
        }
    }
}
