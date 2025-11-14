package com.storyteller_f.shared

import com.storyteller_f.shared.utils.mapResult
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
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
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider
import org.bouncycastle.pqc.jcajce.spec.DilithiumParameterSpec
import org.bouncycastle.util.encoders.Hex.decode
import org.bouncycastle.util.encoders.Hex.toHexString
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.StringReader
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Date
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalStdlibApi::class)
actual suspend fun getDerPublicKeyFromPrivateKeyP256(pemPrivateKeyStr: String): Result<String> {
    return CryptoJvm.readPrivateKeyFromPEMString(pemPrivateKeyStr).mapResult {
        CryptoJvm.getPublicKeyFromPrivateKey(it).map { pubKey ->
            pubKey.encoded.toHexString()
        }
    }
}

@OptIn(ExperimentalStdlibApi::class)
actual suspend fun calcAddressP256(derPublicKeyStr: String): Result<String> {
    return runCatching {
        val decode = decode(derPublicKeyStr)
        val digest256 = Keccak.Digest256()
        val digest = digest256.digest(decode).takeLast(20).toByteArray()
        toHexString(digest)
    }
}

object CryptoJvm {

    @Throws(IOException::class)
    fun readPrivateKeyFromPEMString(pemString: String): Result<PrivateKey> {
        return runCatching {
            val stringReader = StringReader(pemString)
            val pemParser = PEMParser(stringReader)
            val converter = JcaPEMKeyConverter().setProvider("BC")
            val any = pemParser.readObject()
            pemParser.close()

            when (any) {
                is org.bouncycastle.openssl.PEMKeyPair -> converter.getPrivateKey(any.privateKeyInfo)

                is PrivateKeyInfo -> converter.getPrivateKey(any)

                else -> throw IllegalArgumentException("Unsupported PEM object: " + any.javaClass)
            }
        }
    }

    // 从私钥中生成公钥
    @Throws(Exception::class)
    fun getPublicKeyFromPrivateKey(privateKey: PrivateKey): Result<PublicKey> {
        return runCatching {
            val keyFactory = KeyFactory.getInstance("ECDSA", "BC")
            val ecSpec = (keyFactory.getKeySpec(
                privateKey,
                ECPrivateKeySpec::class.java
            ) as ECPrivateKeySpec).params

            val ecPrivateKeySpec = ECPrivateKeySpec((privateKey as ECPrivateKey).d, ecSpec)
            val publicKeySpec = ECPublicKeySpec(ecSpec.g.multiply(ecPrivateKeySpec.d), ecSpec)

            keyFactory.generatePublic(publicKeySpec)
        }
    }

    suspend fun generateCert(pemPrivateKeyStr: String): Result<X509Certificate> {
        return readPrivateKeyFromPEMString(pemPrivateKeyStr).mapResult { privateKey ->
            getPublicKeyFromPrivateKey(privateKey).map { publicKey ->
                // 2️⃣ 证书信息
                val issuer = X500Name("CN=MyCertificate, O=MyCompany, C=US") // 证书颁发者
                val subject = X500Name("CN=MyCertificate, O=MyCompany, C=US") // 证书主体（自签名）
                val serialNumber = BigInteger.valueOf(System.currentTimeMillis()) // 唯一序列号
                val notBefore = Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24) // 生效时间
                val notAfter =
                    Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365 * 10) // 过期时间 (10 年)

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

                JcaX509CertificateConverter().setProvider("BC").getCertificate(certHolder)
            }

        }

    }


    @Suppress("DeprecatedProvider")
    fun encrypt(data: String): Result<Pair<ByteArray, ByteArray>> {
        return runCatching {
            // 生成AES密钥
            val keyGen = KeyGenerator.getInstance("AES", "BC")
            keyGen.init(256) // 使用256位密钥
            val aesKey = keyGen.generateKey()

            // 初始化Cipher进行AES加密
            val aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "BC")
            val iv = ByteArray(aesCipher.getBlockSize())
            val ivSpec = IvParameterSpec(iv)

            // 待加密的数据
            val plaintextBytes = data.toByteArray()

            // 使用AES密钥加密数据
            aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, ivSpec)
            aesCipher.doFinal(plaintextBytes) to aesKey.encoded
        }
    }

    @Suppress("DeprecatedProvider")
    @OptIn(ExperimentalStdlibApi::class)
    fun decrypt(
        derPrivateKeyStr: String,
        encrypted: ByteArray,
        encryptedAesKey: ByteArray
    ): Result<String> {
        return runCatching {
            val privateKeyBytes = derPrivateKeyStr.hexToByteArray()

            // 将字节数组转换为私钥对象
            val privateKeySpec = PKCS8EncodedKeySpec(privateKeyBytes)
            val keyFactory = KeyFactory.getInstance("EC", "BC")
            val privateKey = keyFactory.generatePrivate(privateKeySpec)

            val rsaCipher = Cipher.getInstance("ECIES", "BC")
            rsaCipher.init(Cipher.DECRYPT_MODE, privateKey)
            val aesKeyBytes = rsaCipher.doFinal(encryptedAesKey)

            // 将解密后的AES密钥转换为SecretKey对象
            val aesKey = SecretKeySpec(aesKeyBytes, "AES")

            // 初始化Cipher进行AES解密
            val aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "BC")
            val iv = ByteArray(aesCipher.blockSize)
            val ivSpec = IvParameterSpec(iv)
            aesCipher.init(Cipher.DECRYPT_MODE, aesKey, ivSpec)

            // 使用AES密钥解密数据
            String(aesCipher.doFinal(encrypted))
        }
    }

    @Suppress("DeprecatedProvider")
    @OptIn(ExperimentalStdlibApi::class)
    fun encryptAesKey(derPublicKeyStr: String, aesKey: ByteArray): Result<ByteArray> {
        return runCatching {
            val publicKeyBytes = derPublicKeyStr.hexToByteArray()

            val keySpec = X509EncodedKeySpec(publicKeyBytes)
            val keyFactory = KeyFactory.getInstance("EC", "BC")
            val publicKey = keyFactory.generatePublic(keySpec)

            val rsaCipher = Cipher.getInstance("ECIES", "BC")
            rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey)
            rsaCipher.doFinal(aesKey)
        }
    }


    fun createKeystore(keystorePassword: CharArray, path: String) {
        val file = File(path)
        file.parentFile!!.let {
            if (!it.exists() && !it.mkdirs()) {
                throw Exception("can not create parent file $path")
            }
        }
        val alias = "snapshot"
        val validityDays = 365L

        // 注册 BouncyCastle 提供者（如未自动加载）
        Security.addProvider(BouncyCastleProvider())

        // 1. 生成密钥对
        val keyPairGen = KeyPairGenerator.getInstance("RSA", "BC")
        keyPairGen.initialize(2048, SecureRandom())
        val keyPair: KeyPair = keyPairGen.generateKeyPair()

        // 2. 证书信息
        val issuer = X500Name("CN=Example, OU=Org, O=Company, L=City, ST=State, C=US")
        val subject = issuer // 自签名
        val serial = BigInteger(160, SecureRandom())
        val notBefore = Date(System.currentTimeMillis())
        val notAfter = Date(System.currentTimeMillis() + validityDays * 24 * 60 * 60 * 1000)

        // 3. 构建 X509 证书
        val certBuilder: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
            issuer,
            serial,
            notBefore,
            notAfter,
            subject,
            keyPair.public
        )

        // 4. 生成签名
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val certHolder = certBuilder.build(signer)
        val cert: X509Certificate = JcaX509CertificateConverter()
            .setProvider("BC")
            .getCertificate(certHolder)

        // 5. 创建 PKCS12 keystore 并存储证书和私钥
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry(alias, keyPair.private, keystorePassword, arrayOf(cert))

        // 6. 保存 keystore 到文件
        FileOutputStream(file).use { fos ->
            ks.store(fos, keystorePassword)
        }
    }
}

actual fun loadCryptoLibIfNeed() {
    if (getPlatform().name.startsWith("android", true)) {
        Security.removeProvider("BC")
    }
    Security.addProvider(BouncyCastlePQCProvider())
    Security.addProvider(BouncyCastleProvider())
}

actual val AlgoDilithium: Algo = object : Algo {
    override suspend fun verify(
        derPublicKey: String,
        derSignature: String,
        data: String
    ): Result<Boolean> {
        return runCatching {
            val publicKeyBytes = derPublicKey.hexToByteArray()
            val keyFactory = KeyFactory.getInstance("Dilithium", "BCPQC")
            val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))

            val signature = java.security.Signature.getInstance("Dilithium", "BCPQC")
            signature.initVerify(publicKey)
            signature.update(data.encodeToByteArray())
            signature.verify(derSignature.hexToByteArray())
        }
    }

    override suspend fun signature(
        pemPrivateKey: String,
        data: String
    ): Result<String> {
        return runCatching {
            val pemParser = PEMParser(StringReader(pemPrivateKey))
            val converter = JcaPEMKeyConverter().setProvider("BCPQC")
            val obj = pemParser.readObject()
            pemParser.close()

            val privateKey: PrivateKey = when (obj) {
                is org.bouncycastle.openssl.PEMKeyPair -> converter.getPrivateKey(obj.privateKeyInfo)
                is PrivateKeyInfo -> converter.getPrivateKey(obj)
                else -> throw IllegalArgumentException("Unsupported PEM object: ${obj?.javaClass}")
            }

            val signer = java.security.Signature.getInstance("Dilithium", "BCPQC")
            signer.initSign(privateKey, SecureRandom())
            signer.update(data.encodeToByteArray())
            signer.sign().toHexString()
        }
    }

    override suspend fun getDerPrivateKey(pemPrivateKey: String): Result<String> {
        return runCatching {
            val pemParser = PEMParser(StringReader(pemPrivateKey))
            val converter = JcaPEMKeyConverter().setProvider("BCPQC")
            val obj = pemParser.readObject()
            pemParser.close()

            val privateKey: PrivateKey = when (obj) {
                is org.bouncycastle.openssl.PEMKeyPair -> converter.getPrivateKey(obj.privateKeyInfo)
                is PrivateKeyInfo -> converter.getPrivateKey(obj)
                else -> throw IllegalArgumentException("Unsupported PEM object: ${obj?.javaClass}")
            }

            privateKey.encoded.toHexString()
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun getPemPrivateKeyFromDer(derPrivateKey: String): Result<String> {
        return runCatching {
            val der = derPrivateKey.hexToByteArray()
            val base64 = Base64.encode(der).chunked(64).joinToString("\n")
            buildString {
                appendLine("-----BEGIN PRIVATE KEY-----")
                appendLine(base64)
                appendLine("-----END PRIVATE KEY-----")
            }
        }
    }

    override suspend fun decryptMessage(
        derPrivateKey: String,
        encrypted: ByteArray,
        encryptedAesKey: ByteArray
    ): Result<String> {
        return runCatching {
            throw UnsupportedOperationException("decryptMessage is not supported for Dilithium")
        }
    }

    override suspend fun kemEncrypt(
        derPublicKeyStr: String,
        aesKeyBytes: ByteArray
    ): Result<ByteArray> {
        return runCatching {
            val publicKeyBytes = derPublicKeyStr.hexToByteArray()

            val keyFactory = KeyFactory.getInstance("Kyber", "BCPQC")
            val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
            val wrapCipher = Cipher.getInstance("Kyber", "BCPQC")
            wrapCipher.init(Cipher.WRAP_MODE, publicKey)
            wrapCipher.wrap(SecretKeySpec(aesKeyBytes, "AES"))
        }
    }

    override suspend fun kemDecrypt(
        derPrivateKeyStr: String,
        encrypted: ByteArray
    ): Result<ByteArray> {
        return runCatching {
            val privateKeyBytes = derPrivateKeyStr.hexToByteArray()
            val keyFactory = KeyFactory.getInstance("Kyber", "BCPQC")
            val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))

            val unwrapCipher = Cipher.getInstance("Kyber", "BCPQC")
            unwrapCipher.init(Cipher.UNWRAP_MODE, privateKey)
            val aesKey =
                unwrapCipher.unwrap(encrypted, "AES", Cipher.SECRET_KEY) as javax.crypto.SecretKey
            aesKey.encoded
        }
    }

    override suspend fun calcAddress(derPublicKeyStr: String): Result<String> {
        return runCatching {
            val decode = decode(derPublicKeyStr)
            val digest256 = Keccak.Digest256()
            val digest = digest256.digest(decode).takeLast(20).toByteArray()
            toHexString(digest)
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun generatePemKeyPair(): Result<Pair<String, String>> {
        return runCatching {
            val kpg = KeyPairGenerator.getInstance("Dilithium", "BCPQC")
            kpg.initialize(DilithiumParameterSpec.dilithium3, SecureRandom())
            val kp = kpg.generateKeyPair()
            val privDer = kp.private.encoded
            val privB64 = Base64.encode(privDer).chunked(64).joinToString("\n")
            val privatePem = buildString {
                appendLine("-----BEGIN PRIVATE KEY-----")
                appendLine(privB64)
                appendLine("-----END PRIVATE KEY-----")
            }
            val pubDer = kp.public.encoded
            val pubB64 = Base64.encode(pubDer).chunked(64).joinToString("\n")
            val publicPem = buildString {
                appendLine("-----BEGIN PUBLIC KEY-----")
                appendLine(pubB64)
                appendLine("-----END PUBLIC KEY-----")
            }
            privatePem to publicPem
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun getDerPublicKeyFromPem(pemPublicKeyStr: String): Result<String> {
        return runCatching {
            val base64 = pemPublicKeyStr
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\r", "")
                .replace("\n", "")
                .trim()
            val der = Base64.decode(base64)
            der.toHexString()
        }
    }

    override suspend fun getDerPublicKeyFromPrivateKey(pemPrivateKeyStr: String): Result<String> {
        TODO("Not yet implemented")
    }

}
