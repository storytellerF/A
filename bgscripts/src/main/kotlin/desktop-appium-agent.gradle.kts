val accessibilityDumpAgentJar by tasks.registering(Jar::class) {
    archiveFileName.set(
        when (project.path) {
            ":panel:desktopAppium" -> "desktop-panel-accessibility-dump-agent.jar"
            else -> "desktop-accessibility-dump-agent.jar"
        }
    )
    destinationDirectory.set(layout.buildDirectory.dir("appium/agent"))
    dependsOn(":dev:appiumCore:compileJava")
    manifest {
        attributes(
            "Agent-Class" to "DesktopAccessibilityDumpAgent",
            "Can-Redefine-Classes" to "false",
            "Can-Retransform-Classes" to "false",
        )
    }
    from(project(":dev:appiumCore").layout.buildDirectory.dir("classes/java/main")) {
        include("DesktopAccessibilityDumpAgent*.class")
    }
}
