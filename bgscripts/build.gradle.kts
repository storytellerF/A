plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.gradle)
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.plugins.kotlinMultiplatform.get().version}")
    // https://github.com/gradle/gradle/issues/15383#issuecomment-779893192
    implementation(files(libs::class.java.superclass.protectionDomain.codeSource.location))
}
