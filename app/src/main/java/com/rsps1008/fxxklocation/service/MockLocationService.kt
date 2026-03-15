package com.rsps1008.fxxklocation.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.rsps1008.fxxklocation.data.model.LocationData
import com.rsps1008.fxxklocation.data.store.SettingsStore
import com.rsps1008.fxxklocation.location.MockLocationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.rsps1008.fxxklocation.R
import com.rsps1008.fxxklocation.MainActivity

class MockLocationService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var mockLocationManager: MockLocationManager
    private lateinit var settingsStore: SettingsStore
    private var mockJob: Job? = null

    companion object {
        const val ACTION_START_MOCK = "ACTION_START_MOCK"
        const val ACTION_STOP_MOCK = "ACTION_STOP_MOCK"
        const val ACTION_PAUSE_MOCK = "ACTION_PAUSE_MOCK"
        const val ACTION_RESUME_MOCK = "ACTION_RESUME_MOCK"
        const val EXTRA_LATITUDE = "EXTRA_LATITUDE"
        const val EXTRA_LONGITUDE = "EXTRA_LONGITUDE"
        const val EXTRA_ALTITUDE = "EXTRA_ALTITUDE"
        private const val CHANNEL_ID = "MockLocationChannel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        mockLocationManager = MockLocationManager(this)
        settingsStore = SettingsStore(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        when (intent.action) {
            ACTION_STOP_MOCK -> {
                stopMocking()
                return START_NOT_STICKY
            }
            ACTION_START_MOCK -> {
                val notification = createNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }

                val lat = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
                val lng = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
                val alt = intent.getDoubleExtra(EXTRA_ALTITUDE, 0.0)
                startMocking(LocationData(lat, lng, alt))
            }
            ACTION_PAUSE_MOCK -> {
                mockLocationManager.stopMock()
            }
            ACTION_RESUME_MOCK -> {
                mockLocationManager.startMock()
            }
        }
        return START_STICKY
    }

    private fun startMocking(center: LocationData) {
        mockJob?.cancel()
        mockLocationManager.startMock()

        mockJob = scope.launch {
            // Update immediately on new center
            val enableDriftOnStart = settingsStore.enableDrift.first()
            val radiusOnStart = settingsStore.driftRadius.first()
            val initialLocation = if (enableDriftOnStart) {
                mockLocationManager.generateDriftLocation(center, radiusOnStart)
            } else {
                center
            }
            mockLocationManager.updateMockLocation(initialLocation)

            while (true) {
                delay(2000)
                val enableDrift = settingsStore.enableDrift.first()
                val radius = settingsStore.driftRadius.first()
                
                val locationToMock = if (enableDrift) {
                    mockLocationManager.generateDriftLocation(center, radius)
                } else {
                    center
                }
                
                mockLocationManager.updateMockLocation(locationToMock)
            }
        }
    }

    private fun stopMocking() {
        mockJob?.cancel()
        mockLocationManager.stopMock()
        scope.launch {
            settingsStore.setIsMocking(false)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, MockLocationService::class.java).apply {
            action = ACTION_STOP_MOCK
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(mainPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.stop), stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        mockJob?.cancel()
    }
}
