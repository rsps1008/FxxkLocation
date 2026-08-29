package com.rsps1008.fxxklocation.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.rsps1008.fxxklocation.data.model.LocationData
import kotlin.math.hypot
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MockLocationManager(private val context: Context) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
        "fused" // 許多 WebView 會直接使用融合定位來源
    )

    @SuppressLint("MissingPermission")
    @Synchronized
    fun startMock() {
        providers.forEach { provider ->
            try {
                // 確保先移除舊的，重新建立乾淨的 Test Provider
                try {
                    locationManager.removeTestProvider(provider)
                } catch (e: Exception) {}

                val requiresNetwork = provider == LocationManager.NETWORK_PROVIDER || provider == "fused"
                val requiresSatellite = provider == LocationManager.GPS_PROVIDER || provider == "fused"
                val requiresCell = provider == LocationManager.NETWORK_PROVIDER

                locationManager.addTestProvider(
                    provider,
                    requiresNetwork,
                    requiresSatellite,
                    requiresCell,
                    false, // hasMonetaryCost
                    true,  // supportsAltitude
                    true,  // supportsSpeed
                    true,  // supportsBearing
                    1,     // powerRequirement (POWER_LOW)
                    1      // accuracy (ACCURACY_FINE)
                )
                locationManager.setTestProviderEnabled(provider, true)
            } catch (e: Exception) {
                // 某些設備可能不支持 mock "fused"，忽略即可
            }
        }
    }

    @Synchronized
    fun stopMock() {
        providers.forEach { provider ->
            try {
                locationManager.removeTestProvider(provider)
            } catch (e: Exception) {
                // Provider not found
            }
        }
    }

    @Synchronized
    fun updateMockLocation(locationData: LocationData) {
        val currentTime = System.currentTimeMillis()
        val elapsedNanos = SystemClock.elapsedRealtimeNanos()

        providers.forEach { provider ->
            val mockLocation = Location(provider).apply {
                latitude = locationData.latitude
                longitude = locationData.longitude
                altitude = locationData.altitude
                time = currentTime
                elapsedRealtimeNanos = elapsedNanos
                
                // 設定極高的精度，讓 WebView 引擎強制採信這筆數據
                accuracy = 1.0f 
                try {
                    setVerticalAccuracyMeters(1.0f)
                } catch (e: NoSuchMethodError) {}
                
                // 即使靜止也給予微小的速度，模擬真實傳感器活耀狀態
                speed = 0.0f
                bearing = 0.0f
                
                isMock = true
                
                // 額外標記，部分 WebView 內核會檢查 bundle
                val bundle = android.os.Bundle()
                bundle.putBoolean("mockLocation", true)
                extras = bundle
            }
            try {
                locationManager.setTestProviderLocation(provider, mockLocation)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun generateRandomWalkLocation(
        anchor: LocationData,
        previous: LocationData,
        radiusInMeters: Double
    ): LocationData {
        val maxStepMeters = (radiusInMeters * 0.18).coerceAtLeast(2.0)
        val stepMeters = maxStepMeters * (0.35 + Math.random() * 0.65)
        val angle = 2 * Math.PI * Math.random()

        val metersPerLatDegree = 111320.0
        val metersPerLngDegree = (111320.0 * cos(Math.toRadians(anchor.latitude))).coerceAtLeast(1.0)

        val dxFromAnchor = (previous.longitude - anchor.longitude) * metersPerLngDegree
        val dyFromAnchor = (previous.latitude - anchor.latitude) * metersPerLatDegree

        var candidateDx = dxFromAnchor + (stepMeters * cos(angle))
        var candidateDy = dyFromAnchor + (stepMeters * sin(angle))
        val distanceFromAnchor = hypot(candidateDx, candidateDy)

        if (distanceFromAnchor > radiusInMeters) {
            val inwardScale = (radiusInMeters * 0.98) / distanceFromAnchor
            candidateDx *= inwardScale
            candidateDy *= inwardScale
        }

        val nextLatitude = anchor.latitude + (candidateDy / metersPerLatDegree)
        val nextLongitude = anchor.longitude + (candidateDx / metersPerLngDegree)

        // Add a small random altitude drift (±0.2 meters)
        val altDrift = (Math.random() - 0.5) * 0.4
        val nextAltitude = previous.altitude + altDrift

        return LocationData(nextLatitude, nextLongitude, nextAltitude)
    }
}
