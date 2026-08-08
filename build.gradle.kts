import dev.detekt.gradle.Detekt
import dev.detekt.gradle.DetektCreateBaselineTask
import dev.detekt.gradle.report.ReportMergeTask
import org.gradle.api.tasks.testing.Test


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
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
    alias(libs.plugins.benManesVersions)
    alias(libs.plugins.versionCatalogUpdate)
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlinxRpc) apply false
    alias(libs.plugins.sentryAndroidGradle) apply false
}

fun isNoReleaseCompileTask(taskName: String): Boolean {
    val isKsp = taskName.startsWith("ksp")
    val isKotlinOrJavaCompile = taskName.startsWith("compile") && listOf(
        "Kotlin",
        "Java",
        "JavaWithJavac",
        "KotlinJvm",
        "KotlinMetadata",
        "Main",
    ).any { suffix ->
        taskName.endsWith(suffix)
    }
    val isExcludedVariant = listOf("Release", "Benchmark", "Test", "Jmh").any { variant ->
        taskName.contains(variant)
    }
    return (isKsp || isKotlinOrJavaCompile) && !isExcludedVariant
}

fun detektBaselineFileName(taskName: String): String {
    val suffix = taskName
        .removePrefix("detektBaseline")
        .removePrefix("detekt")
    if (suffix.isEmpty() || suffix.endsWith("SourceSet")) {
        return "detekt-baseline.xml"
    }
    val kebabSuffix = suffix
        .replace(Regex("([a-z0-9])([A-Z])"), "$1-$2")
        .lowercase()
    return "detekt-baseline-$kebabSuffix.xml"
}

val compileAllNoRelease = tasks.register("compileAllNoRelease") {
    group = "verification"
    description = "Compile all included modules without Android release or benchmark variants."
}

val buildAndTestFilters =
    providers.gradleProperty("buildAndTest.testFilters").orNull
        ?.lineSequence()
        ?.filter(String::isNotEmpty)
        ?.toList()

if (!buildAndTestFilters.isNullOrEmpty()) {
    subprojects {
        tasks.withType<Test>().configureEach {
            filter {
                buildAndTestFilters.forEach(::includeTestsMatching)
                isFailOnNoMatchingTests = false
            }
        }
    }
}

subprojects {
    compileAllNoRelease.configure {
        dependsOn(tasks.matching { task ->
            isNoReleaseCompileTask(task.name)
        })
    }
}

val detektReportMergeSarif = tasks.register<ReportMergeTask>("detektReportMergeSarif") {
    output = layout.buildDirectory.file("reports/detekt/merge.sarif")
}
subprojects {
    apply(plugin = "dev.detekt")
    detekt {
        // Include every Kotlin source set so module baselines also cover KMP-only targets.
        source.setFrom("src", "build.gradle.kts")
        // Builds the AST in parallel. Rules are always executed in parallel.
        // Can lead to speedups in larger projects. `false` by default.
        parallel = true

        autoCorrect = false

        // Keep existing violations separate from violations introduced by new code.
        baseline = layout.projectDirectory.file("detekt-baseline.xml")

        // Android: Don't create tasks for the specified build types (e.g. "release")
        ignoredBuildTypes = listOf("release")

        // Specify the base path for file paths in the formatted reports.
        // If not set, all file paths reported will be absolute file path.
        basePath = layout.projectDirectory

        buildUponDefaultConfig = true
    }

    dependencies {
        detektPlugins(rootProject.libs.detekt.formatting)
        detektPlugins(rootProject.libs.detekt.rules.libraries)
        detektPlugins(rootProject.libs.detekt.rules.ruleauthors)
    }

    tasks.withType<Detekt>().configureEach {
        baseline.set(layout.projectDirectory.file(detektBaselineFileName(name)))
        exclude { source -> source.file.absolutePath.replace('\\', '/').contains("/build/") }
        reports {
            checkstyle.required = true
            html.required = true
            sarif.required = true
            markdown.required = true
        }
        basePath = rootDir.absolutePath
        finalizedBy(detektReportMergeSarif)
    }

    tasks.withType<DetektCreateBaselineTask>().configureEach {
        baseline.set(layout.projectDirectory.file(detektBaselineFileName(name)))
        exclude { source -> source.file.absolutePath.replace('\\', '/').contains("/build/") }
    }

    detektReportMergeSarif {
        input.from(
            tasks.withType<Detekt>().map { it.reports.sarif.outputLocation })
    }
}
val koverIncludedProjects = listOf(
    ":shared",
    ":app:composeApp",
    ":app:cliApp",
    ":client:composeCore",
    ":app:androidApp",
    ":app:desktopApp",
    ":cloud:service",
    ":cloud:server",
    ":cloud:cli",
    ":cloud:worker",
    ":cloud:pdf",
    ":cloud:pdfbox",
    ":cloud:openpdf",
    ":api",
    ":panel:composeApp",
    ":panel:androidApp",
    ":panel:cliApp",
    ":panel:desktopApp",
    ":backend:core",
    ":backend:exposed",
    ":backend:simple",
    ":backend:redis",
    ":backend:minio",
    ":backend:lucene",
    ":backend:filesystem",
    ":backend:elastic",
    ":client:core",
    ":client:bot-lib",
    ":client:model-storage",
    ":client:room",
    ":client:asciidoc-parser",
    ":bot:builtin-bot",
) + if (providers.gradleProperty("appium").orNull == "true") {
    listOf(
        ":dev:appiumCore",
        ":app:androidAppium",
        ":app:desktopAppium",
        ":app:wasmAppium",
        ":panel:androidAppium",
        ":panel:desktopAppium",
        ":panel:wasmAppium",
    )
} else {
    emptyList()
} + if (providers.gradleProperty("e2e").orNull == "true") {
    listOf(
        ":dev:e2eCore",
        ":app:cliE2e",
        ":panel:cliE2e",
    )
} else {
    emptyList()
}.distinct()
dependencies {
    koverIncludedProjects.forEach { projectPath ->
        kover(project(projectPath))
    }
}

subprojects {
    if (path in koverIncludedProjects) {
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

versionCatalogUpdate {
    keep {
        // keep versions without any library or plugin reference
        keepUnusedVersions.set(true)
    }
}
