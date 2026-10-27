tasks.register<WriteAppiumRuntimeClasspathTask>("writeAppiumRuntimeClasspath") {
    dependsOn(tasks.named("classes"))
    classpath.from(
        layout.buildDirectory.dir("classes/kotlin/main"),
        layout.buildDirectory.dir("resources/main"),
        configurations.named("runtimeClasspath"),
    )
    outputFile.set(layout.buildDirectory.file("appium/runtimeClasspath.txt"))
}
