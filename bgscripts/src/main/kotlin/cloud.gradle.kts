
tasks.withType<JavaExec> {
    jvmArgs = listOf("--add-modules", "jdk.incubator.vector")
}

tasks.withType<Test> {
    jvmArgs = listOf("--add-modules", "jdk.incubator.vector")
}

fun AbstractCopyTask.handleDupJar() {
    filesMatching("vavi-commons-1.1.10.jar") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE // 排除重复项
    }
}

tasks.withType<Tar> {
    handleDupJar()
}

tasks.withType<Zip> {
    handleDupJar()
}

tasks.withType<Sync> {
    handleDupJar()
}
