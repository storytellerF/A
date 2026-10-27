/*
 * This is a private project. All rights reserved.
 */

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

class E2eAndroidCoveragePlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val android = extensions.getByType<ApplicationExtension>()
        val components = extensions.getByType<ApplicationAndroidComponentsExtension>()
        dependencies.add("debugImplementation", project(":dev:e2eCoverageRuntime"))
        val source = rootProject.layout.projectDirectory.dir("dev/e2eCoverageRuntime/android")
        android.sourceSets.getByName("debug").apply {
            java.srcDir(source.dir("src"))
            manifest.srcFile(source.file("AndroidManifest.xml"))
        }
        val sourceFiles = rootProject.fileTree(rootProject.projectDir) {
            include("**/src/main/**/*.kt", "**/src/main/**/*.java", "**/src/*Main/**/*.kt", "**/src/*Main/**/*.java")
            exclude(".worktrees/**", "**/build/**", "dev/e2eCoverageRuntime/**")
        }
        val projectLayout = layout
        val classSetName = target.path
        val instrumentation = gradle.sharedServices.registerIfAbsent("e2e-instrumentation", E2eInstrumentationService::class.java) {
            maxParallelUsages.set(1)
        }
        components.onVariants(components.selector().withBuildType("debug")) { variant ->
            val prepared = tasks.register<E2eAndroidCoverageTask>("instrument${variant.name.replaceFirstChar(Char::uppercaseChar)}E2eCoverage") {
                group = "verification"
                classSet.set(classSetName)
                usesService(instrumentation)
                this.sourceFiles.from(sourceFiles)
                outputJar.set(projectLayout.buildDirectory.file("e2e-coverage/${variant.name}/classes.jar"))
                originalClasses.set(projectLayout.buildDirectory.dir("e2e-coverage/originals/${variant.name}"))
                metadataDirectory.set(projectLayout.buildDirectory.dir("e2e-coverage/${variant.name}/assets"))
                scratchDirectory.set(projectLayout.buildDirectory.dir("e2e-coverage/${variant.name}/scratch"))
            }
            variant.artifacts.forScope(ScopedArtifacts.Scope.ALL).use(prepared).toTransform(
                ScopedArtifact.CLASSES, E2eAndroidCoverageTask::allJars,
                E2eAndroidCoverageTask::allDirectories, E2eAndroidCoverageTask::outputJar,
            )
            variant.sources.assets?.addGeneratedSourceDirectory(prepared, E2eAndroidCoverageTask::metadataDirectory)
        }
    }
}
