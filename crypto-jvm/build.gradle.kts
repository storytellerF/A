plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(libs.bcprov.jdk18on)
    implementation(libs.bcpkix.jdk18on)
}
