import com.android.build.gradle.internal.tasks.factory.dependsOn
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinBuildConfig)
}

group = "com.storyteller_f"
version = "unspecified"

dependencies {
    testImplementation(kotlin("test"))

    api(libs.exposed.core)
    api(libs.exposed.dao)
    api(libs.exposed.crypt)
    api(libs.exposed.jdbc)
    api(libs.exposed.kotlin.datetime)
    api(libs.exposed.json)
    api(libs.exposed.money)

    implementation(libs.pgjdbc.ng)
    implementation(libs.postgresql)
    implementation(libs.napier)
    implementation(projects.shared)
    implementation(libs.minio)
    implementation(libs.elasticsearch.java)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.lucene.core)
    implementation(libs.lucene.queryparser)
    implementation(libs.lucene.analysis.common)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

sourceSets {
    main {
        resources {
            srcDirs(
                layout.buildDirectory.dir("copied/resources"),
                layout.buildDirectory.dir("copied-ca/resources")
            )
        }
    }
}

val flavor = project.findProperty("buildkonfig.flavor")

val copyTask = tasks.register("CopyEnv", Copy::class) {
    group = "copy"
    from("../${flavor}.env")
    enabled = File(rootDir, "${flavor}.env").exists()
    into(layout.buildDirectory.dir("copied/resources"))
    rename {
        ".env"
    }
}

tasks.processResources.dependsOn(copyTask)

