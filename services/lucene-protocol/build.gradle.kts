/*
 * This is a private project. All rights reserved.
 */

plugins {
    `java-library`
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinxRpc)
    alias(libs.plugins.serialization)
}

group = "com.storyteller_f.services"
version = "unspecified"

kotlin { jvmToolchain(21) }

dependencies {
    api(libs.kotlinx.rpc.core)
    api(libs.kotlinx.serialization.json)
}
