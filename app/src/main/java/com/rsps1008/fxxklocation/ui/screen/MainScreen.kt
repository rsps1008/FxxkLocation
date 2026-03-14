package com.rsps1008.fxxklocation.ui.screen

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.rsps1008.fxxklocation.util.SystemCheckUtil
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
    val hasPermission by viewModel.hasPermission.collectAsState()
    val isGpsEnabled by viewModel.isGpsEnabled.collectAsState()
    val isMockAppSet by viewModel.isMockAppSet.collectAsState()

    val selectedLoc = viewModel.selectedLocation

    LaunchedEffect(Unit) {
        viewModel.checkStatus()
        Configuration.getInstance().userAgentValue = context.packageName
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
            // Status Section
            StatusCard(hasPermission, isGpsEnabled, isMockAppSet, 
                onOpenPermissions = { SystemCheckUtil.openAppSettings(context) },
                onOpenGps = { SystemCheckUtil.openLocationSettings(context) },
                onOpenDev = { SystemCheckUtil.openDevelopmentSettings(context) }
            )

            // Map Section (OpenStreetMap)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
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

            // Input Section
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = selectedLoc.latitude.toString(),
                    onValueChange = { it.toDoubleOrNull()?.let { v -> viewModel.updateSelectedLocation(v, selectedLoc.longitude) } },
                    label = { Text("Lat") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = selectedLoc.longitude.toString(),
                    onValueChange = { it.toDoubleOrNull()?.let { v -> viewModel.updateSelectedLocation(selectedLoc.latitude, v) } },
                    label = { Text("Lng") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LargeFloatingActionButton(
                    onClick = { if (!isMocking && hasPermission && isMockAppSet) viewModel.startMock() },
                    containerColor = if (isMocking || !hasPermission || !isMockAppSet) Color.LightGray else MaterialTheme.colorScheme.primaryContainer
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

@Composable
fun StatusCard(
    hasPermission: Boolean,
    isGpsEnabled: Boolean,
    isMockAppSet: Boolean,
    onOpenPermissions: () -> Unit,
    onOpenGps: () -> Unit,
    onOpenDev: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            StatusItem("Location Permission", hasPermission, onOpenPermissions)
            StatusItem("GPS Enabled", isGpsEnabled, onOpenGps)
            StatusItem("Mock App Selected", isMockAppSet, onOpenDev)
        }
    }
}

@Composable
fun StatusItem(label: String, isOk: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isOk) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
            ),
            modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Text(if (isOk) "OK" else "Fix", color = Color.White)
        }
    }
}
