/*
 * This is a private project. All rights reserved.
 */

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.net.URI
import java.security.MessageDigest

@CacheableTask
abstract class DownloadNotoWasmFontTask : DefaultTask() {
    @get:Input
    abstract val fontUrl: Property<String>

    @get:Input
    abstract val fontSha256: Property<String>

    @get:Input
    abstract val licenseUrl: Property<String>

    @get:Input
    abstract val licenseSha256: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun download() {
        val fontsDirectory = outputDirectory.get().dir("files/fonts").asFile
        fontsDirectory.mkdirs()
        downloadAndVerify(
            url = fontUrl.get(),
            expectedSha256 = fontSha256.get(),
            destination = fontsDirectory.resolve("noto_sans_sc_regular.otf"),
        )
        downloadAndVerify(
            url = licenseUrl.get(),
            expectedSha256 = licenseSha256.get(),
            destination = fontsDirectory.resolve("OFL.txt"),
        )
    }

    private fun downloadAndVerify(
        url: String,
        expectedSha256: String,
        destination: File,
    ) {
        if (!destination.isFile) {
            URI(url).toURL().openStream().buffered().use { input ->
                destination.outputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            }
        }
        val actualSha256 =
            destination.inputStream().buffered().use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
                digest.digest().toHexString()
            }
        if (actualSha256 != expectedSha256) {
            destination.delete()
            error("SHA-256 mismatch for $url: expected $expectedSha256, received $actualSha256")
        }
    }
}

tasks.register<DownloadNotoWasmFontTask>("downloadNotoWasmFont") {
    fontUrl.set(
        "https://raw.githubusercontent.com/notofonts/noto-cjk/Sans2.004/" +
            "Sans/SubsetOTF/SC/NotoSansSC-Regular.otf"
    )
    fontSha256.set("faa6c9df652116dde789d351359f3d7e5d2285a2b2a1f04a2d7244df706d5ea9")
    licenseUrl.set(
        "https://raw.githubusercontent.com/notofonts/noto-cjk/Sans2.004/LICENSE"
    )
    licenseSha256.set("6a73f9541c2de74158c0e7cf6b0a58ef774f5a780bf191f2d7ec9cc53efe2bf2")
    outputDirectory.set(layout.buildDirectory.dir("generated/notoWasmFont"))
}
