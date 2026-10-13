/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.shared

import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import javax.crypto.KEM

internal actual fun encapsulateKem(publicKey: PublicKey): Pair<ByteArray, ByteArray> {
    val encapsulated = KEM.getInstance("ML-KEM", "BC").newEncapsulator(publicKey, SecureRandom()).encapsulate()
    return encapsulated.encapsulation() to encapsulated.key().encoded
}

internal actual fun decapsulateKem(privateKey: PrivateKey, encapsulation: ByteArray): ByteArray =
    KEM.getInstance("ML-KEM", "BC").newDecapsulator(privateKey).decapsulate(encapsulation).encoded
