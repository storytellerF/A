/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.dev.appium

import com.storyteller_f.a.client.core.AuthKey
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class InjectedSession(
    val address: String,
    val pemPrivateKey: String,
    val derPrivateKey: String,
    val derPublicKey: String,
    val cookies: List<io.ktor.http.Cookie> = emptyList(),
) {
    fun toAuthKey(): AuthKey =
        AuthKey.P256(
        pemPrivateKey = pemPrivateKey,
        derPrivateKey = derPrivateKey,
        derPublicKey = derPublicKey,
    )
}

fun buildInjectedSessionJson(session: InjectedSession): String =
    buildJsonObject {
    put("algo", "P256")
    put("address", session.address)
    put("pemPrivateKey", session.pemPrivateKey)
    put("derPrivateKey", session.derPrivateKey)
    put("derPublicKey", session.derPublicKey)
    put("cookies", Json.encodeToJsonElement(ListSerializer(io.ktor.http.Cookie.serializer()), session.cookies))
}.toString()
