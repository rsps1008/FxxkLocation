package com.rsps1008.fxxklocation.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.rsps1008.fxxklocation.data.model.LocationData
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MockLocationManager(private val context: Context) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private val providerName = LocationManager.GPS_PROVIDER

    @SuppressLint("MissingPermission")
    fun startMock() {
        try {
            locationManager.addTestProvider(
                providerName,
                false, false, false, false, true, true, true,
                1, // Power usage low
                1  // Accuracy fine
            )
            locationManager.setTestProviderEnabled(providerName, true)
        } catch (e: Exception) {
            // Already added or permission denied
        }
    }

    fun stopMock() {
        try {
            locationManager.removeTestProvider(providerName)
        } catch (e: Exception) {
            // Provider not found
        }
    }

    fun updateMockLocation(locationData: LocationData) {
        val mockLocation = Location(providerName).apply {
            latitude = locationData.latitude
            longitude = locationData.longitude
            altitude = locationData.altitude
            time = System.currentTimeMillis()
            accuracy = 1.0f
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        try {
            locationManager.setTestProviderLocation(providerName, mockLocation)
        } catch (e: Exception) {
            // Handle error
        }
    }

    fun generateDriftLocation(center: LocationData, radiusInMeters: Double): LocationData {
        val radiusInDegrees = radiusInMeters / 111320.0
        val u = Math.random()
        val v = Math.random()
        val w = radiusInDegrees * sqrt(u)
        val t = 2 * Math.PI * v
        val x = w * cos(t)
        val y = w * sin(t)

        val newLongitude = x / cos(Math.toRadians(center.latitude))
        return LocationData(center.latitude + y, center.longitude + newLongitude, center.altitude)
    }
}
