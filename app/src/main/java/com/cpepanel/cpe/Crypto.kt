package com.cpepanel.cpe

import android.util.Base64
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** 十六进制编解码（不依赖外部库） */
object Hex {
    private val LOWER = "0123456789abcdef".toCharArray()
    private val UPPER = "0123456789ABCDEF".toCharArray()

    fun encode(bytes: ByteArray, upper: Boolean = false): String {
        val table = if (upper) UPPER else LOWER
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(table[v ushr 4]).append(table[v and 0x0F])
        }
        return sb.toString()
    }

    fun decode(s: String): ByteArray {
        val h = s.trim()
        val out = ByteArray(h.length / 2)
        for (i in out.indices) {
            out[i] = ((Character.digit(h[i * 2], 16) shl 4) or Character.digit(h[i * 2 + 1], 16)).toByte()
        }
        return out
    }
}

/**
 * 与中兴 Web 前端完全对齐的密码学实现。
 *
 * ── 登录（对应前端 paswordAlgorithmsCookie / web_login）──
 *   前端 SHA256 的内部标志位 p=1，输出【大写】十六进制。
 *   口令 = SHA256( SHA256(明文) + sault )，两层均为大写十六进制。
 *   若改成小写，设备侧比对必然失败。
 *
 * ── 会话加密（对应前端 p() / f()）──
 *   AES-256-CBC/PKCS7，密钥 = 32 字节随机数（以小写十六进制字符串经 RSA 交给设备）。
 *   密文格式 = base64( iv(16B) || ciphertext )。
 */
object Crypto {

    fun sha256HexUpper(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return Hex.encode(md.digest(input.toByteArray(Charsets.UTF_8)), upper = true)
    }

    /** 中兴网页登录口令 */
    fun loginHash(plainPassword: String, sault: String): String =
        sha256HexUpper(sha256HexUpper(plainPassword) + sault)

    /** 会话 AES 密钥原文：32 字节随机数的【小写】十六进制（64 字符），与 CryptoJS 一致 */
    fun randomHexLower(byteCount: Int): String {
        val b = ByteArray(byteCount)
        SecureRandom().nextBytes(b)
        return Hex.encode(b, upper = false)
    }

    /** 用设备下发的 PEM 公钥做 RSA/PKCS#1 v1.5 加密（对应 JSEncrypt.encrypt），输出 base64 */
    fun rsaEncryptPem(pem: String, plain: String): String {
        val body = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace(Regex("\\s"), "")
        val der = Base64.decode(body, Base64.DEFAULT)
        val pub = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, pub)
        return Base64.encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    /** 会话内敏感字段加密，等价于前端 p() */
    fun sessionEncrypt(aesKeyHex: String, plain: String): String {
        val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(Hex.decode(aesKeyHex), "AES"),
            IvParameterSpec(iv)
        )
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    /** 解密设备返回的加密字段，等价于前端 f() */
    fun sessionDecrypt(aesKeyHex: String, encoded: String): String? = try {
        val raw = Base64.decode(encoded, Base64.DEFAULT)
        if (raw.size <= 16) null else {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(Hex.decode(aesKeyHex), "AES"),
                IvParameterSpec(raw.copyOfRange(0, 16))
            )
            String(cipher.doFinal(raw.copyOfRange(16, raw.size)), Charsets.UTF_8)
        }
    } catch (t: Throwable) {
        null
    }
}
