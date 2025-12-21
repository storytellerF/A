import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.lang.Exception

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
            browser()
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
            implementation(libs.robolectric)
        }
        androidUnitTest {
            dependsOn(headlessTest)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmTest {
            dependsOn(headlessTest)
        }
    }
    compilerOptions {
        freeCompilerArgs.addAll("-Xcontext-parameters", "-Xexpect-actual-classes")
    }
}

dependencies {
    androidTestImplementation(libs.androidx.ui.test.junit4.android)
    debugImplementation(libs.androidx.ui.test.manifest)
}

android {
    namespace = "com.storyteller_f.a.client.kotbase"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions {
        val javaVersion = JavaVersion.forClassVersion(libs.versions.jdk.get().toInt())
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }
    testOptions {
        unitTests.all {
            val dir = project.layout.buildDirectory.dir("native-libs/couchbase").get().asFile
            it.jvmArgs("-Djava.library.path=$dir")
        }
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}
interface Injected {
    @get:Inject
    val operations: ArchiveOperations
}
val extractCouchbaseLibsTask = tasks.register("extractCouchbaseNativeLib") {
    group = "libs"
    val outputDir = layout.buildDirectory.dir("native-libs/couchbase")
    val runtimeClasspath = configurations.named("jvmRuntimeClasspath").get().files.toList()
    val injected = project.objects.newInstance<Injected>()

    inputs.files(runtimeClasspath)
    outputs.dir(outputDir)

    val osName = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch")
    doLast {
        val output = outputDir.get().asFile
        if (!output.exists() && !output.mkdirs()) {
            throw Exception("mkdirs failed $output")
        }
        runtimeClasspath.forEach { jarFile ->
            if (jarFile.path.contains("couchbase-lite-java")) {
                injected.operations.zipTree(jarFile).forEach { file ->
                    val match = if (osName.contains("mac")) {
                        file.path.contains("macos") && (file.path.contains("universal") || file.path.contains(arch))
                    } else if (osName.contains("win")) {
                        file.path.contains("windows")
                    } else if (osName.contains("nux")) {
                        file.path.contains("linux")
                    } else {
                        false
                    }
                    if (match) {
                        file.copyTo(File(output, file.name), true)
                    }
                }
            }
        }
    }
}

afterEvaluate {
    tasks.getByName("testDebugUnitTest").dependsOn(extractCouchbaseLibsTask)
}
