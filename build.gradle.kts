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

val compileAllNoRelease by tasks.registering {
    group = "verification"
    description = "Compile all included modules without Android release or benchmark variants."
}

subprojects {
    compileAllNoRelease.configure {
        dependsOn(tasks.matching { task ->
            isNoReleaseCompileTask(task.name)
        })
    }
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
val koverIncludedProjects = listOf(
    ":shared",
    ":app:composeApp",
    ":app:cliApp",
    ":app:core",
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
    ":client:ascii-parser",
    ":bot:builtin-bot",
) + if (providers.gradleProperty("appium").orNull == "true") {
    listOf(
        ":dev:appiumCore",
        ":app:androidAppium",
        ":app:desktopAppium",
        ":panel:androidAppium",
        ":panel:desktopAppium",
    )
} else {
    emptyList()
}
dependencies {
    koverIncludedProjects.mapNotNull { findProject(it) }.forEach {
        kover(it)
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
