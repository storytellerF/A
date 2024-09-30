import com.android.build.gradle.internal.tasks.factory.dependsOn
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinJvm)
    id("com.github.gmazzo.buildconfig") version "5.4.0"
}

group = "com.storyteller_f"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    api(libs.exposed.core)
    api(libs.exposed.dao)
    implementation(libs.pgjdbc.ng)
    implementation(libs.napier)
    api(libs.exposed.crypt)
    api(libs.exposed.jdbc)
    api(libs.exposed.kotlin.datetime)
    api(libs.exposed.json)
    api(libs.exposed.money)
    implementation(projects.shared)
    implementation(libs.bcprov.jdk18on)
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
    jvmToolchain(17)
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
val isProd = project.findProperty("server.prod") == true


val copyTask = tasks.register("CopyNewEnv", Copy::class) {
    group = "copy"
    from("../${flavor}.env")
    into(layout.buildDirectory.dir("copied/resources"))
    rename {
        ".env"
    }
}

tasks.processResources.dependsOn(copyTask)

buildConfig {
    buildConfigField<Boolean>("IS_PROD", isProd)
}
