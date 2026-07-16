package com.storyteller_f.a.dev.appium

import com.storyteller_f.a.client.core.AuthKey
import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.model.AlgoType
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

const val PRIVATE_STORAGE_INJECTED_SESSION_DIR = "appium-session"
const val PRIVATE_STORAGE_INJECTED_SESSION_FILE = "session.json"

data class InjectedSession(
    val address: String,
    val pemPrivateKey: String,
    val derPrivateKey: String,
    val derPublicKey: String,
)

fun buildInjectedSessionJson(session: InjectedSession): String =
    buildJsonObject {
        put("algo", "P256")
        put("address", session.address)
        put("pemPrivateKey", session.pemPrivateKey)
        put("derPrivateKey", session.derPrivateKey)
        put("derPublicKey", session.derPublicKey)
    }.toString()

fun InjectedSession.toAuthKey(): AuthKey =
    AuthKey.P256(
        pemPrivateKey = pemPrivateKey,
        derPrivateKey = derPrivateKey,
        derPublicKey = derPublicKey,
    )

suspend fun createUnsignedInjectedSession(): InjectedSession {
    val algo = getAlgo(AlgoType.P256)
    val (pemPrivateKey, _) = algo.generatePemKeyPair().getOrThrow()
    val derPrivateKey = algo.getDerPrivateKey(pemPrivateKey).getOrThrow()
    val derPublicKey = algo.getDerPublicKeyFromPrivateKey(pemPrivateKey).getOrThrow()
    val address = algo.calcAddress(derPublicKey).getOrThrow()
    return InjectedSession(
        address = address,
        pemPrivateKey = pemPrivateKey,
        derPrivateKey = derPrivateKey,
        derPublicKey = derPublicKey,
    )
}

fun writeInjectedSessionFile(filesDir: File, json: String) {
    val sessionFile = filesDir
        .resolve(PRIVATE_STORAGE_INJECTED_SESSION_DIR)
        .resolve(PRIVATE_STORAGE_INJECTED_SESSION_FILE)
    sessionFile.parentFile?.mkdirs()
    sessionFile.writeText(json)
}
