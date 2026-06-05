package com.phantom.rat.core

import android.util.Base64
import java.security.*
import java.security.spec.X509EncodedKeySpec
import javax.crypto.*
import javax.crypto.spec.*
import java.security.SecureRandom

object Crypto {

    private const val AES_ALGORITHM = "AES/GCM/NoPadding"
    private const val AES_KEY_SIZE = 256
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private const val RSA_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
    private const val RSA_KEY_SIZE = 2048
    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    
    private val secureRandom = SecureRandom()
    
    // Master key derived from device-specific info
    private fun getMasterKey(): ByteArray {
        val salt = "PhantomRAT_SALT_2025".toByteArray()
        val password = "PhantomRAT_MASTER_KEY_2025!!SECURE".toCharArray()
        
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val spec = PBEKeySpec(password, salt, 10000, 256)
        return factory.generateSecret(spec).encoded
    }

    // === AES-256-GCM Encryption/Decryption ===
    
    fun encryptAES(plainText: String, key: ByteArray = getMasterKey()): String {
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        val secretKey = SecretKeySpec(key, "AES")
        val iv = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val encrypted = cipher.doFinal(plainText.toByteArray())
        // Prepend IV to ciphertext
        val combined = iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decryptAES(encryptedText: String, key: ByteArray = getMasterKey()): String {
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        val secretKey = SecretKeySpec(key, "AES")
        val decoded = Base64.decode(encryptedText, Base64.NO_WRAP)
        val iv = decoded.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = decoded.copyOfRange(GCM_IV_LENGTH, decoded.size)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return String(cipher.doFinal(encrypted))
    }

    // === AES-CBC (legacy/fallback) ===
    
    fun encryptAESCBC(plainText: String, key: ByteArray = getMasterKey()): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKey = SecretKeySpec(key.copyOf(32), "AES")
        val iv = ByteArray(16).also { secureRandom.nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
        val encrypted = cipher.doFinal(plainText.toByteArray())
        val combined = iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decryptAESCBC(encryptedText: String, key: ByteArray = getMasterKey()): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKey = SecretKeySpec(key.copyOf(32), "AES")
        val decoded = Base64.decode(encryptedText, Base64.NO_WRAP)
        val iv = decoded.copyOfRange(0, 16)
        val encrypted = decoded.copyOfRange(16, decoded.size)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
        return String(cipher.doFinal(encrypted))
    }

    // === RSA Encryption ===
    
    fun generateRSAKeyPair(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(RSA_KEY_SIZE, SecureRandom())
        return keyGen.generateKeyPair()
    }

    fun encryptRSA(plainText: String, publicKeyBytes: ByteArray): String {
        val cipher = Cipher.getInstance(RSA_ALGORITHM)
        val publicKey = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(publicKeyBytes))
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return Base64.encodeToString(cipher.doFinal(plainText.toByteArray()), Base64.NO_WRAP)
    }

    fun decryptRSA(encryptedText: String, privateKey: PrivateKey): String {
        val cipher = Cipher.getInstance(RSA_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        return String(cipher.doFinal(Base64.decode(encryptedText, Base64.NO_WRAP)))
    }

    // === HMAC ===
    
    fun hmacSHA256(data: ByteArray, key: ByteArray): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        val secretKey = SecretKeySpec(key, HMAC_ALGORITHM)
        mac.init(secretKey)
        return Base64.encodeToString(mac.doFinal(data), Base64.NO_WRAP)
    }

    fun verifyHMAC(data: ByteArray, key: ByteArray, expectedHMAC: String): Boolean {
        val computed = hmacSHA256(data, key)
        return MessageDigest.isEqual(computed.toByteArray(), expectedHMAC.toByteArray())
    }

    // === Hashing ===
    
    fun sha256(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data.toByteArray()).joinToString("") { 
            String.format("%02x", it) 
        }
    }

    fun sha512(data: String): String {
        val digest = MessageDigest.getInstance("SHA-512")
        return digest.digest(data.toByteArray()).joinToString("") { 
            String.format("%02x", it) 
        }
    }

    fun md5(data: String): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(data.toByteArray()).joinToString("") { 
            String.format("%02x", it) 
        }
    }

    // === Key Derivation ===
    
    fun deriveKey(password: String, salt: String = "PhantomRAT", iterations: Int = 10000): ByteArray {
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val spec = PBEKeySpec(password.toCharArray(), salt.toByteArray(), iterations, 256)
        return factory.generateSecret(spec).encoded
    }

    // === Utility ===
    
    fun generateRandomKey(length: Int = 32): ByteArray {
        val key = ByteArray(length)
        secureRandom.nextBytes(key)
        return key
    }

    fun generateRandomString(length: Int = 16): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length).map { chars[secureRandom.nextInt(chars.length)] }.joinToString("")
    }

    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { String.format("%02x", it) }
    }

    fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    fun generateSessionToken(): String {
        val random = ByteArray(32)
        secureRandom.nextBytes(random)
        return Base64.encodeToString(random, Base64.NO_WRAP or Base64.URL_SAFE)
    }

    fun generateDeviceFingerprint(androidId: String, serial: String, buildFingerprint: String): String {
        val raw = "$androidId|$serial|$buildFingerprint"
        return sha256(raw)
    }

    // === Secure Preferences ===
    
    fun encryptPrefValue(value: String): String {
        return encryptAES(value)
    }

    fun decryptPrefValue(encrypted: String): String {
        return try {
            decryptAES(encrypted)
        } catch (_: Exception) {
            encrypted // Return as-is if not encrypted (legacy)
        }
    }

    // === File Encryption ===
    
    fun encryptFile(data: ByteArray, key: ByteArray = getMasterKey()): ByteArray {
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        val secretKey = SecretKeySpec(key.copyOf(32), "AES")
        val iv = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val encrypted = cipher.doFinal(data)
        return iv + encrypted
    }

    fun decryptFile(encryptedData: ByteArray, key: ByteArray = getMasterKey()): ByteArray {
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        val secretKey = SecretKeySpec(key.copyOf(32), "AES")
        val iv = encryptedData.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = encryptedData.copyOfRange(GCM_IV_LENGTH, encryptedData.size)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(encrypted)
    }
}
