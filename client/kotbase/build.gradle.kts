import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.serialization)
}

val buildIosTarget = project.findProperty("target.ios") == "true"
val buildWasmTarget = project.findProperty("target.wasm") == "true"
kotlin {
    if (buildWasmTarget) {
        @OptIn(ExperimentalWasmDsl::class)
        wasmJs {
            browser {
                commonWebpackConfig {
                    devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                        static = (static ?: mutableListOf()).apply {
                            // Serve sources to debug inside the browser
                            add(project.projectDir.path)
                        }
                    }
                }
                testTask {
                    useKarma {
                        useChrome()
                    }
                }
            }
        }
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    if (buildIosTarget) {
        iosX64()
        iosArm64()
        iosSimulatorArm64()
    }

    jvm()

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate()

    sourceSets {
        val headlessTest by creating {
            dependsOn(commonTest.get())
        }
        headlessTest.dependencies {
            implementation(projects.client.kotbase)
        }
        androidMain.dependencies {
        }
        commonMain.dependencies {
            implementation(projects.shared)
            implementation(projects.client.modelStorage)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.napier)
            implementation(libs.couchbase.lite)
            implementation(libs.couchbase.lite.ktx)
            implementation(libs.couchbase.lite.paging)
        }
        androidUnitTest.dependencies {
            implementation(libs.androidx.ui.test.junit4.android)
            implementation(libs.androidx.ui.test.manifest)
            implementation(libs.robolectric)
        }
        androidUnitTest {
            dependsOn(headlessTest)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
        }
        jvmMain {
        }
        jvmTest {
            dependsOn(headlessTest)
        }
        if (buildIosTarget) {
            iosMain.dependencies {
            }
            iosMain {
            }
        }
    }
    compilerOptions {
        freeCompilerArgs.addAll("-Xcontext-parameters", "-Xexpect-actual-classes")
    }
}

android {
    namespace = "com.storyteller_f.a.client.kotbase"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    testOptions {
        unitTests.all {
            val dir = project.layout.buildDirectory.dir("native-libs/couchbase").get().asFile
            println("test ${it.path} $dir")

            it.jvmArgs(
                "-Djava.library.path=$dir"
            )
        }
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

val extractCouchbaseLibsTask = tasks.register("extractCouchbaseNativeLib") {
    group = "libs"
    val outputDir = layout.buildDirectory.dir("native-libs/couchbase")
    val runtimeClasspath = configurations.getByName("jvmRuntimeClasspath")

    inputs.files(runtimeClasspath)
    outputs.dir(outputDir)

    doLast {
        val output = outputDir.get().asFile
        output.mkdirs()
        runtimeClasspath.forEach { jarFile ->
            if (jarFile.path.contains("couchbase-lite-java")) {
                zipTree(jarFile).forEach {
                    if (it.path.contains("libs") &&
                        !it.path.contains("macos") || it.path.contains("universal")
                    ) {
                        it.copyTo(File(output, it.name), true)
                    }
                }
            }
        }
    }
}

afterEvaluate {
    tasks.getByName("testDebugUnitTest").dependsOn(extractCouchbaseLibsTask)
}
