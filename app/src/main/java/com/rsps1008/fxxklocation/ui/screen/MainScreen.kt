package com.rsps1008.fxxklocation.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.rsps1008.fxxklocation.viewmodel.MainViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val isMocking by viewModel.isMocking.collectAsState()
    val isApplied by viewModel.isApplied.collectAsState()
    val hasPermission by viewModel.hasPermission.collectAsState()
    val isGpsEnabled by viewModel.isGpsEnabled.collectAsState()
    val isMockAppSet by viewModel.isMockAppSet.collectAsState()
    val isIgnoringBatteryOptimizations by viewModel.isIgnoringBatteryOptimizations.collectAsState()
    
    val lifecycleOwner = LocalLifecycleOwner.current
    var showPermissionDialog by remember { mutableStateOf(false) }
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }

    val selectedLoc = viewModel.selectedLocation

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.checkStatus()
                    mapViewRef.value?.onResume()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    mapViewRef.value?.onPause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewRef.value?.onDetach()
        }
    }

    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Permissions Required") },
            text = { Text("To start mocking location, please ensure all system permissions and settings are correctly configured in the Settings page.") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    onNavigateToSettings()
                }) {
                    Text("Go to Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fxxk Location") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Map Section (OpenStreetMap)
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            setTilesScaledToDpi(true)
                            setBackgroundColor(android.graphics.Color.WHITE)
                            controller.setZoom(15.0)
                            controller.setCenter(GeoPoint(selectedLoc.latitude, selectedLoc.longitude))
                            
                            val marker = Marker(this)
                            marker.position = GeoPoint(selectedLoc.latitude, selectedLoc.longitude)
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            marker.title = "Target Location"
                            overlays.add(marker)

                            val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                    viewModel.updateSelectedLocation(p.latitude, p.longitude)
                                    marker.position = p
                                    invalidate()
                                    return true
                                }
                                override fun longPressHelper(p: GeoPoint): Boolean = false
                            })
                            overlays.add(eventsOverlay)
                            mapViewRef.value = this
                        }
                    },
                    update = { mapView ->
                        val point = GeoPoint(selectedLoc.latitude, selectedLoc.longitude)
                        val marker = mapView.overlays.filterIsInstance<Marker>().firstOrNull()
                        marker?.position = point
                        mapView.invalidate()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LargeFloatingActionButton(
                    onClick = { 
                        if (!isApplied) {
                            if (hasPermission && isMockAppSet && isGpsEnabled && isIgnoringBatteryOptimizations) {
                                viewModel.startMock()
                            } else {
                                showPermissionDialog = true
                            }
                        }
                    },
                    containerColor = if (isApplied) Color.LightGray else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.Default.Check, "Start", modifier = Modifier.size(36.dp))
                }

                LargeFloatingActionButton(
                    onClick = { if (isMocking) viewModel.stopMock() },
                    containerColor = if (isMocking) MaterialTheme.colorScheme.errorContainer else Color.LightGray
                ) {
                    Icon(Icons.Default.Close, "Stop", modifier = Modifier.size(36.dp))
                }
            }
        }
    }
}
