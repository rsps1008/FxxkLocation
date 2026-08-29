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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rsps1008.fxxklocation.MainActivity
import com.rsps1008.fxxklocation.R
import com.rsps1008.fxxklocation.data.model.LocationData
import com.rsps1008.fxxklocation.data.state.MockLocationRuntimeState
import com.rsps1008.fxxklocation.data.store.CurrentLocationSnapshotPolicy
import com.rsps1008.fxxklocation.data.store.SettingsStore
import com.rsps1008.fxxklocation.location.MockLocationManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Suppress("LogNotTimber")
class MockLocationService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val stopScopeJob = SupervisorJob()
    private val stopScope = CoroutineScope(Dispatchers.Main + stopScopeJob)
    private val providerDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val providerMutex = Mutex()
    private val cleanupScope = CoroutineScope(providerDispatcher + SupervisorJob())
    private lateinit var mockLocationManager: MockLocationManager
    private lateinit var settingsStore: SettingsStore
    private var mockJob: Job? = null
    private var stopJob: Job? = null
    private var latestMockLocation: LocationData? = null
    private var stopRequested = false
    private var latestStartId = 0
    private val snapshotPolicy = CurrentLocationSnapshotPolicy(LOCATION_SNAPSHOT_INTERVAL_MILLIS)

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
        private const val LOCATION_SNAPSHOT_INTERVAL_MILLIS = 30_000L
        private const val TAG = "MockLocationService"
    }

    override fun onCreate() {
        super.onCreate()
        mockLocationManager = MockLocationManager(this)
        settingsStore = SettingsStore(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        latestStartId = startId

        when (intent.action) {
            ACTION_STOP_MOCK -> {
                stopMocking(startId)
                return START_NOT_STICKY
            }
            ACTION_START_MOCK -> {
                try {
                    val notification = createNotification()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to promote mock location service to foreground", e)
                    stopMocking(startId)
                    return START_NOT_STICKY
                }

                val lat = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
                val lng = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
                val alt = intent.getDoubleExtra(EXTRA_ALTITUDE, 0.0)
                startMocking(LocationData(lat, lng, alt))
            }
            ACTION_PAUSE_MOCK -> {
                enqueueProviderOperation("pause") {
                    mockLocationManager.stopMock()
                }
            }
            ACTION_RESUME_MOCK -> {
                enqueueProviderOperation("resume") {
                    mockLocationManager.startMock()
                }
            }
        }
        return START_STICKY
    }

    private fun startMocking(initialCenter: LocationData) {
        var center = initialCenter
        var currentPosition = initialCenter
        val driftConfigFlow = combine(
            settingsStore.enableDrift,
            settingsStore.driftRadius
        ) { enabled, radius ->
            DriftConfig(enabled, radius)
        }.distinctUntilChanged()

        mockJob?.cancel()
        stopRequested = false
        MockLocationRuntimeState.clearStopRequest()
        snapshotPolicy.reset()
        latestMockLocation = initialCenter
        MockLocationRuntimeState.update(initialCenter)

        mockJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                runProviderOperation {
                    mockLocationManager.startMock()
                }
                var driftConfig = driftConfigFlow.first()

                settingsStore.setIsMocking(true)

                supervisorScope {
                    launch {
                        try {
                            driftConfigFlow.collect { config ->
                                driftConfig = config
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Drift settings observation stopped", e)
                        }
                    }

                    // Auto-stop logic
                    launch {
                        try {
                            val autoStop = settingsStore.enableAutoStop.first()
                            if (autoStop) {
                                val minutes = settingsStore.autoStopMinutes.first()
                                if (minutes > 0) {
                                    for (i in minutes downTo 1) {
                                        updateNotification(i)
                                        delay(60L * 1000L)
                                    }
                                    stopMocking()
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Auto-stop observation stopped", e)
                        }
                    }

                    // Dynamically update altitude if it's changed in Settings or via real-refresh while mocking
                    launch {
                        try {
                            settingsStore.lastAltitudeValue
                                .distinctUntilChanged()
                                .collect { alt ->
                                    center = center.copy(altitude = alt)
                                    currentPosition = currentPosition.copy(altitude = alt)
                                    latestMockLocation = latestMockLocation?.copy(altitude = alt)
                                }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Altitude observation stopped", e)
                        }
                    }

                    while (true) {
                        val anchor = center
                        val previous = currentPosition
                        val enabled = driftConfig.enabled
                        val radius = driftConfig.radius
                        val calculatedLocation = if (enabled) {
                            withContext(Dispatchers.Default) {
                                mockLocationManager.generateRandomWalkLocation(anchor, previous, radius)
                            }
                        } else {
                            anchor
                        }
                        // The altitude collector can run while the Default/IO work is
                        // suspended. Keep a newer altitude from being overwritten by
                        // a stale calculation result.
                        val locationToMock = calculatedLocation.copy(altitude = currentPosition.altitude)

                        currentPosition = locationToMock
                        latestMockLocation = locationToMock
                        MockLocationRuntimeState.update(locationToMock)
                        runProviderOperation {
                            mockLocationManager.updateMockLocation(locationToMock)
                        }
                        persistCurrentLocationIfDue(latestMockLocation ?: locationToMock)
                        delay(2000)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Mock location loop stopped unexpectedly", e)
                stopMocking()
            }
        }
    }

    private fun stopMocking(stopStartId: Int = latestStartId) {
        if (stopRequested) return
        stopRequested = true
        mockJob?.cancel()
        MockLocationRuntimeState.requestStop()
        MockLocationRuntimeState.clear()
        val finalLocation = latestMockLocation
        latestMockLocation = null
        stopJob = stopScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                providerMutex.withLock {
                    withContext(providerDispatcher) {
                        mockLocationManager.stopMock()
                    }
                    try {
                        settingsStore.setCurrentLocationAndStopMocking(finalLocation)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to persist the final mock location", e)
                        try {
                            settingsStore.setIsMocking(false)
                        } catch (fallbackError: CancellationException) {
                            throw fallbackError
                        } catch (fallbackError: Exception) {
                            Log.e(TAG, "Failed to clear the mocking state after persistence failure", fallbackError)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop the mock location provider", e)
                try {
                    settingsStore.setIsMocking(false)
                } catch (fallbackError: CancellationException) {
                    throw fallbackError
                } catch (fallbackError: Exception) {
                    Log.e(TAG, "Failed to clear the mocking state after persistence failure", fallbackError)
                }
            }
            // The stop transaction (or its fallback) has completed. A new
            // ViewModel may now evaluate auto-start again.
            MockLocationRuntimeState.clearStopRequest()
            stopSelfResult(stopStartId)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private suspend fun persistCurrentLocationIfDue(location: LocationData) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (!snapshotPolicy.shouldPersist(now)) return

        try {
            settingsStore.setCurrentLocation(location)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist a mock location snapshot", e)
        }
        // Throttle failed attempts too, so a persistent DataStore error does
        // not turn the two-second mock loop into repeated I/O and logging.
        snapshotPolicy.markAttempted(now)
    }

    private fun enqueueProviderOperation(name: String, operation: () -> Unit) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                runProviderOperation(operation)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Provider operation failed: $name", e)
            }
        }
    }

    private suspend fun runProviderOperation(operation: () -> Unit) {
        providerMutex.withLock {
            withContext(providerDispatcher) {
                operation()
            }
        }
    }

    private fun createNotification(remainingMinutes: Int? = null): Notification {
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

        val contentText = if (remainingMinutes != null) {
            getString(R.string.notification_text_auto_stop, remainingMinutes)
        } else {
            getString(R.string.notification_text)
        }

        // 取得 App 圖示作為大圖標
        //val appIcon = android.graphics.BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_stat_name)
            //.setLargeIcon(appIcon)
            .setContentIntent(mainPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.stop), stopPendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(remainingMinutes: Int? = null) {
        val notification = createNotification(remainingMinutes)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mockJob?.cancel()
        scope.cancel()
        MockLocationRuntimeState.clear()
        val activeStopJob = stopJob
        if (activeStopJob?.isActive == true) {
            activeStopJob.invokeOnCompletion {
                stopScopeJob.cancel()
            }
        } else {
            stopScopeJob.cancel()
        }
        cleanupScope.launch {
            try {
                providerMutex.withLock {
                    mockLocationManager.stopMock()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Provider cleanup failed while destroying service", e)
            } finally {
                cleanupScope.cancel()
            }
        }
        super.onDestroy()
    }

    private data class DriftConfig(
        val enabled: Boolean = false,
        val radius: Double = 10.0
    )
}
