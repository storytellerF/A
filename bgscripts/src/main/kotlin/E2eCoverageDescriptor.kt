/*
 * This is a private project. All rights reserved.
 */

import java.io.File
import java.security.MessageDigest

/** Build identity includes original classes, production sources and the pinned engine, never timestamps. */
internal fun createCoverageDescriptor(originals: File, classSet: String, sources: Set<File>): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update("kover-offline-0.9.11-serialized-counters-v1".toByteArray())
    val sourceFingerprint = coverageSourceFingerprint(sources)
    digest.update(sourceFingerprint.toByteArray())
    val classes = originals.walkTopDown().filter { it.isFile && it.extension == "class" }
        .sortedBy { it.relativeTo(originals).invariantSeparatorsPath }.toList()
    check(classes.isNotEmpty()) { "No original classes exist for coverage reporting" }
    classes.forEach { file ->
        digest.update(file.relativeTo(originals).invariantSeparatorsPath.toByteArray())
        digest.update(0.toByte())
        digest.update(file.readBytes())
    }
    val id = digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    return ("classSet=$classSet\nbuildId=$id\nclassCount=${classes.size}\nsourceFingerprint=$sourceFingerprint\n" +
        "engine=kover-offline-serialized-counters\n").toByteArray()
}

internal fun coverageSourceFingerprint(sources: Set<File>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    sources.sortedBy(File::getAbsolutePath).forEach { source ->
        digest.update(source.name.toByteArray())
        digest.update(0.toByte())
        digest.update(source.readBytes())
    }
    return digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
