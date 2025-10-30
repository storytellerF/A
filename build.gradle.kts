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
    alias(libs.plugins.aboutlibrary)
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
    id("com.github.ben-manes.versions") version "0.53.0"
    id("nl.littlerobots.version-catalog-update") version "1.0.1"
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
            "src/commonTest/kotlin",
            "src/jvmMain/kotlin",
            "src/jvmTest/kotlin",
            "src/iosMain/kotlin",
            "src/wasmJsMain/kotlin",
            "src/androidMain/kotlin",
            "src/androidUnitTest/kotlin",
            "src/androidDebug/kotlin",
            "src/headlessTest/kotlin",
            "build.gradle.kts",
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
val jvmLibModules = listOf(
    ":cloud:service",
    ":cloud:server",
    ":cloud:pdf",
    ":cloud:pdfbox",
    ":cloud:openpdf",
    ":api",
    ":backend:core",
    ":backend:exposed",
    ":backend:simple",
    ":backend:redis",
    ":backend:minio",
    ":backend:lucene",
    ":backend:filesystem",
    ":backend:elastic",
)
val composeModules = listOf(
    ":shared",
    ":app:composeApp",
    ":client:core",
    ":client:model-storage",
    ":client:room"
)
dependencies {
    (composeModules + jvmLibModules).forEach {
        kover(project(it))
    }
}

subprojects {
    val n = displayName.removePrefix("project ").removeSurrounding("'")
    if ((jvmLibModules + composeModules).contains(n)) {
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
