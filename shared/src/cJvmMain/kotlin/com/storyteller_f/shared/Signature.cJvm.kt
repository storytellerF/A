package com.storyteller_f.shared

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jcajce.provider.digest.Keccak
import org.bouncycastle.jce.interfaces.ECPrivateKey
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECPrivateKeySpec
import org.bouncycastle.jce.spec.ECPublicKeySpec
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.util.encoders.Hex.decode
import org.bouncycastle.util.encoders.Hex.toHexString
import java.io.IOException
import java.io.StringReader
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Security.addProvider
import java.security.Security.removeProvider
import java.security.cert.X509Certificate
import java.util.*

@OptIn(ExperimentalStdlibApi::class)
actual suspend fun getDerPublicKeyFromPrivateKey(pemPrivateKeyStr: String): String {
    val privateKey = CryptoJvm.readPrivateKeyFromPEMString(pemPrivateKeyStr)
    return CryptoJvm.getPublicKeyFromPrivateKey(privateKey).encoded.toHexString()
}

@OptIn(ExperimentalStdlibApi::class)
actual suspend fun calcAddress(derPublicKeyStr: String): String {
    val decode = decode(derPublicKeyStr)
    val digest256 = Keccak.Digest256()
    val digest = digest256.digest(decode).takeLast(20).toByteArray()
    val toHexString = toHexString(digest)
    return toHexString
}

object CryptoJvm {

    @Throws(IOException::class)
    fun readPrivateKeyFromPEMString(pemString: String): PrivateKey {
        val stringReader = StringReader(pemString)
        val pemParser = PEMParser(stringReader)
        val converter: JcaPEMKeyConverter = JcaPEMKeyConverter().setProvider("BC")
        val any: Any = pemParser.readObject()
        pemParser.close()

        return when (any) {
            is org.bouncycastle.openssl.PEMKeyPair -> converter.getPrivateKey(any.privateKeyInfo)

            is org.bouncycastle.asn1.pkcs.PrivateKeyInfo -> converter.getPrivateKey(any)

            else -> throw IllegalArgumentException("Unsupported PEM object: " + any.javaClass)
        }
    }

    // 从私钥中生成公钥
    @Throws(Exception::class)
    fun getPublicKeyFromPrivateKey(privateKey: PrivateKey): PublicKey {
        val keyFactory = KeyFactory.getInstance("ECDSA", "BC")
        val ecSpec = (keyFactory.getKeySpec(
            privateKey,
            ECPrivateKeySpec::class.java
        ) as ECPrivateKeySpec).params

        val ecPrivateKeySpec = ECPrivateKeySpec((privateKey as ECPrivateKey).d, ecSpec)
        val publicKeySpec = ECPublicKeySpec(ecSpec.g.multiply(ecPrivateKeySpec.d), ecSpec)

        return keyFactory.generatePublic(publicKeySpec)
    }

    fun generateCert(pemPrivateKeyStr: String): X509Certificate {
        val privateKey = readPrivateKeyFromPEMString(pemPrivateKeyStr)
        val publicKey = getPublicKeyFromPrivateKey(privateKey)
        // 2️⃣ 证书信息
        val issuer = X500Name("CN=MyCertificate, O=MyCompany, C=US") // 证书颁发者
        val subject = X500Name("CN=MyCertificate, O=MyCompany, C=US") // 证书主体（自签名）
        val serialNumber = BigInteger.valueOf(System.currentTimeMillis()) // 唯一序列号
        val notBefore = Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24) // 生效时间
        val notAfter = Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365 * 10) // 过期时间 (10 年)

        // 3️⃣ 创建 X509v3 证书
        val certBuilder: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
            issuer,
            serialNumber,
            notBefore,
            notAfter,
            subject,
            publicKey
        )

        // 4️⃣ 使用 ECDSA-SHA256 签名
        val signer: ContentSigner = JcaContentSignerBuilder("SHA256withECDSA")
            .setProvider("BC")
            .build(privateKey)

        // 5️⃣ 生成 X.509 证书
        val certHolder = certBuilder.build(signer)
        val certificate: X509Certificate = JcaX509CertificateConverter()
            .setProvider("BC")
            .getCertificate(certHolder)

        return certificate
    }
}

actual fun loadIfNeed() {
    if (getPlatform().name.startsWith("android", true)) {
        removeProvider("BC")
        addProvider(BouncyCastleProvider())
    } else {
        addProvider(BouncyCastleProvider())
    }
}