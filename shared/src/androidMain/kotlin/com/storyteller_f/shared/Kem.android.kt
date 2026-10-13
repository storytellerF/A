/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.shared

import org.bouncycastle.jcajce.SecretKeyWithEncapsulation
import org.bouncycastle.jcajce.spec.KEMExtractSpec
import org.bouncycastle.jcajce.spec.KEMGenerateSpec
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import javax.crypto.KeyGenerator

internal actual fun encapsulateKem(publicKey: PublicKey): Pair<ByteArray, ByteArray> {
    // Android has no javax.crypto.KEM; disable BC's default KDF to preserve the raw shared secret.
    val generator = KeyGenerator.getInstance("ML-KEM", "BC")
    generator.init(KEMGenerateSpec.Builder(publicKey, "AES", 256).withNoKdf().build(), SecureRandom())
    val encapsulated = generator.generateKey() as SecretKeyWithEncapsulation
    return encapsulated.encapsulation to encapsulated.encoded
}

internal actual fun decapsulateKem(privateKey: PrivateKey, encapsulation: ByteArray): ByteArray {
    val generator = KeyGenerator.getInstance("ML-KEM", "BC")
    generator.init(KEMExtractSpec.Builder(privateKey, encapsulation, "AES", 256).withNoKdf().build())
    return generator.generateKey().encoded
}
