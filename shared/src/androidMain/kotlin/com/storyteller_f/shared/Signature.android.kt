package com.storyteller_f.shared

import com.storyteller_f.crypto_jvm.CryptoJvm
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

fun addProvider() {
    Security.removeProvider("BC")
    Security.addProvider(BouncyCastleProvider())
//    Security.addProvider(BouncyCastleJsseProvider())
//    Security.addProvider(BouncyCastlePQCProvider())
//    Security.getProviders().forEach {
//        println(it.name)
//        it.services.forEach {
//            println("service ${it.type} ${it.algorithm} ${it.className}")
//        }
//    }
}

actual suspend fun getDerPublicKeyFromPrivateKey(pemPrivateKeyStr: String): String {
    return CryptoJvm.getDerPublicKeyFromPrivateKey(pemPrivateKeyStr)
}

actual suspend fun calcAddress(derPublicKeyStr: String): String {
    return CryptoJvm.calcAddress(derPublicKeyStr)
}

actual suspend fun encrypt(data: String): Pair<ByteArray, ByteArray> {
    return CryptoJvm.encrypt(data)
}

actual suspend fun decrypt(derPrivateKeyStr: String, encrypted: ByteArray, encryptedAesKey: ByteArray): String {
    return CryptoJvm.decrypt(derPrivateKeyStr, encrypted, encryptedAesKey)
}

actual suspend fun encryptAesKey(derPublicKeyStr: String, aesKey: ByteArray): ByteArray {
    return CryptoJvm.encryptAesKey(derPublicKeyStr, aesKey)
}
