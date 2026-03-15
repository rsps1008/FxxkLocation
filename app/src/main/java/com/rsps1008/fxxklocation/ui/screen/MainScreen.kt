package com.rsps1008.fxxklocation.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.rsps1008.fxxklocation.R
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
    val hasNotificationPermission by viewModel.hasNotificationPermission.collectAsState()
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
            title = { Text(stringResource(R.string.permissions_required)) },
            text = { Text(stringResource(R.string.permissions_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    onNavigateToSettings()
                }) {
                    Text(stringResource(R.string.go_to_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
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
                Box(modifier = Modifier.fillMaxSize()) {
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
                                marker.title = context.getString(R.string.target_location)
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
                            // Check if current center is far from target, if so, center it.
                            // We use a small threshold to avoid constant snapping while dragging.
                            val currentCenter = mapView.mapCenter
                            if (Math.abs(currentCenter.latitude - point.latitude) > 0.00001 || 
                                Math.abs(currentCenter.longitude - point.longitude) > 0.00001) {
                                mapView.controller.animateTo(point)
                            }
                            mapView.invalidate()
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Locate Me Button
                    SmallFloatingActionButton(
                        onClick = { viewModel.locateMe() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Default.Place, contentDescription = stringResource(R.string.locate_me))
                    }
                }
            }

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LargeFloatingActionButton(
                    onClick = { 
                        if (!isApplied) {
                            if (hasPermission && isMockAppSet && isGpsEnabled && isIgnoringBatteryOptimizations && hasNotificationPermission) {
                                viewModel.startMock()
                            } else {
                                showPermissionDialog = true
                            }
                        }
                    },
                    containerColor = if (isApplied) Color.LightGray else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.Default.Check, stringResource(R.string.start), modifier = Modifier.size(36.dp))
                }

                LargeFloatingActionButton(
                    onClick = { if (isMocking) viewModel.stopMock() },
                    containerColor = if (isMocking) MaterialTheme.colorScheme.errorContainer else Color.LightGray
                ) {
                    Icon(Icons.Default.Close, stringResource(R.string.stop), modifier = Modifier.size(36.dp))
                }
            }
        }
    }
}
