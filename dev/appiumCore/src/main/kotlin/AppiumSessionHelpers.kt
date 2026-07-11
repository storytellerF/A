import com.storyteller_f.a.client.core.AuthKey
import com.storyteller_f.a.client.core.SimplePassHolder
import com.storyteller_f.a.client.core.getAuthKey
import com.storyteller_f.shared.getAlgo
import com.storyteller_f.shared.model.AlgoType
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun buildInjectedSessionJson(session: InjectedSession): String =
    buildJsonObject {
        put("algo", "P256")
        put("address", session.address)
        put("pemPrivateKey", session.pemPrivateKey)
        put("derPrivateKey", session.derPrivateKey)
        put("derPublicKey", session.derPublicKey)
    }.toString()

suspend fun createPreRegisteredInjectedSession(
    signUp: suspend (AuthKey, SimplePassHolder) -> Unit,
): InjectedSession {
    val session = createUnsignedInjectedSession()
    val passHolder = SimplePassHolder()
    signUp(getAuthKey(AlgoType.P256, session.pemPrivateKey), passHolder)
    return session
}

suspend fun generateAppiumPrivateKey(): String =
    getAlgo(AlgoType.P256).generatePemKeyPair().getOrThrow().first

private suspend fun createUnsignedInjectedSession(): InjectedSession {
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
