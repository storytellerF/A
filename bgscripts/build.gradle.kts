plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.gradle)
    // https://github.com/gradle/gradle/issues/15383#issuecomment-779893192
    implementation(files(libs::class.java.superclass.protectionDomain.codeSource.location))
}