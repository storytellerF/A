import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.report.ReportMergeTask


plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.jetbrainsCompose) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.screenshot) apply false
    alias(libs.plugins.kover)
    id("io.gitlab.arturbosch.detekt").version("1.23.7")
    id("com.mikepenz.aboutlibraries.plugin") version "11.2.3" apply false
    id("com.github.ben-manes.versions") version "0.52.0"
    id("nl.littlerobots.version-catalog-update") version "0.8.5"
    alias(libs.plugins.kotlin.android) apply false
}

val detektReportMergeSarif by tasks.registering(ReportMergeTask::class) {
    output = layout.buildDirectory.file("reports/detekt/merge.sarif")
}
subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    detekt {
        // The directories where detekt looks for source files.
        // Defaults to `files("src/main/java", "src/test/java", "src/main/kotlin", "src/test/kotlin")`.
        source.setFrom(
            "src/main/kotlin",
            "src/test/kotlin",
            "src/commonMain/kotlin",
            "src/desktopMain/kotlin",
            "src/iosMain/kotlin",
            "src/wasmJsMain/kotlin",
            "src/androidMain/kotlin"
        )
        // Builds the AST in parallel. Rules are always executed in parallel.
        // Can lead to speedups in larger projects. `false` by default.
        parallel = true

        autoCorrect = true

        // Android: Don't create tasks for the specified build types (e.g. "release")
        ignoredBuildTypes = listOf("release")

        // Specify the base path for file paths in the formatted reports.
        // If not set, all file paths reported will be absolute file path.
        basePath = projectDir.absolutePath

        buildUponDefaultConfig = true
    }

    dependencies {
        detektPlugins(rootProject.libs.detekt.formatting)
        detektPlugins(rootProject.libs.detekt.rules.libraries)
        detektPlugins(rootProject.libs.detekt.rules.ruleauthors)
    }

    tasks.withType<Detekt>().configureEach {
        reports {
            xml.required = true
            html.required = true
            txt.required = true
            sarif.required = true
            md.required = true
        }
        basePath = rootDir.absolutePath
        finalizedBy(detektReportMergeSarif)
    }

    detektReportMergeSarif {
        input.from(
            tasks.withType<Detekt>().map { it.sarifReportFile })
    }
}
val composeModules = listOf("composeApp", "shared")
val jvmLibModules = listOf("server", "backend", "client-lib")
dependencies {
    (composeModules + jvmLibModules.forEach {
        kover(project(":$it"))
    })
}

subprojects {
    if ((jvmLibModules + composeModules).contains(name)) {
        apply(plugin = "org.jetbrains.kotlinx.kover")
        kover {
            reports {
                // filters for all report types of all build variants
                filters {
                    excludes {
                        androidGeneratedClasses()
                    }
                }
            }
        }
    }

}
