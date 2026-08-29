package com.rsps1008.fxxklocation.ui.screen

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.input.ImeAction
import com.rsps1008.fxxklocation.R
import com.rsps1008.fxxklocation.util.SystemCheckUtil
import com.rsps1008.fxxklocation.viewmodel.MainViewModel
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val isMocking by viewModel.isMocking.collectAsState()
    val isApplied by viewModel.isApplied.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val hasLoadedInitialSelectedLocation by viewModel.hasLoadedInitialSelectedLocation.collectAsState()
    
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    var showPermissionDialog by remember { mutableStateOf(false) }
    var pendingStartMock by remember { mutableStateOf(false) }
    var awaitingBatteryOptimizationResponse by remember { mutableStateOf(false) }
    var hasInitializedInitialCamera by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    val mapLibreMapRef = remember { mutableStateOf<MapLibreMap?>(null) }
    val markerRef = remember { mutableStateOf<Marker?>(null) }
    val currentMarkerRef = remember { mutableStateOf<Marker?>(null) }
    val currentLocationIcon = remember(context) { createCurrentLocationIcon(context) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.checkStatus()
        viewModel.refreshStatusAfterTransition()
        if (granted) {
            attemptStartMockFlow(
                context = context,
                viewModel = viewModel,
                pendingStartMock = pendingStartMock,
                onPendingStartMockChanged = { pendingStartMock = it },
                showPermissionDialog = { showPermissionDialog = it },
                locationPermissionLauncher = null,
                notificationPermissionLauncher = null,
                onBatteryOptimizationRequested = { awaitingBatteryOptimizationResponse = true }
            )
        } else {
            pendingStartMock = false
            showPermissionDialog = true
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.checkStatus()
        viewModel.refreshStatusAfterTransition()
        if (granted) {
            attemptStartMockFlow(
                context = context,
                viewModel = viewModel,
                pendingStartMock = pendingStartMock,
                onPendingStartMockChanged = { pendingStartMock = it },
                showPermissionDialog = { showPermissionDialog = it },
                locationPermissionLauncher = null,
                notificationPermissionLauncher = notificationPermissionLauncher,
                onBatteryOptimizationRequested = { awaitingBatteryOptimizationResponse = true }
            )
        } else {
            pendingStartMock = false
            showPermissionDialog = true
        }
    }

    val selectedLoc = viewModel.selectedLocation

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { messageRes ->
            Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.cameraLocations.collect { location ->
            val point = LatLng(location.latitude, location.longitude)
            mapLibreMapRef.value?.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(point)
                        .zoom(16.5)
                        .build()
                )
            )
        }
    }

    LaunchedEffect(mapLibreMapRef.value, isMocking, selectedLoc.latitude, selectedLoc.longitude) {
        if (hasInitializedInitialCamera || !hasLoadedInitialSelectedLocation) return@LaunchedEffect

        val map = mapLibreMapRef.value ?: return@LaunchedEffect
        val initialTarget = LatLng(selectedLoc.latitude, selectedLoc.longitude)

        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(initialTarget)
                    .zoom(15.0)
                    .build()
            )
        )
        hasInitializedInitialCamera = true
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapViewRef.value?.onStart()
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.checkStatus()
                    viewModel.refreshStatusAfterTransition()
                    viewModel.refreshBatteryOptimizationStatusAfterTransition()
                    mapViewRef.value?.onResume()
                    if (awaitingBatteryOptimizationResponse && pendingStartMock) {
                        coroutineScope.launch {
                            delay(1300)
                            awaitingBatteryOptimizationResponse = false
                            attemptStartMockFlow(
                                context = context,
                                viewModel = viewModel,
                                pendingStartMock = pendingStartMock,
                                onPendingStartMockChanged = { pendingStartMock = it },
                                showPermissionDialog = { showPermissionDialog = it },
                                locationPermissionLauncher = null,
                                notificationPermissionLauncher = null,
                                onBatteryOptimizationRequested = { awaitingBatteryOptimizationResponse = true },
                                allowBatteryOptimizationRequest = false
                            )
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> mapViewRef.value?.onPause()
                Lifecycle.Event.ON_STOP -> mapViewRef.value?.onStop()
                Lifecycle.Event.ON_DESTROY -> mapViewRef.value?.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            markerRef.value = null
            currentMarkerRef.value = null
            mapLibreMapRef.value = null
            mapViewRef.value?.onDestroy()
            mapViewRef.value = null
        }
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
        val triggerSearch = {
            keyboardController?.hide()
            focusManager.clearFocus()
            viewModel.searchPlace(searchQuery)
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 0.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Map Section (MapLibre)
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
                            MapLibre.getInstance(ctx)
                            MapView(ctx).apply {
                                mapViewRef.value = this
                                onCreate(null)
                                onStart()
                                onResume()
                                getMapAsync { map ->
                                    mapLibreMapRef.value = map
                                    map.uiSettings.isCompassEnabled = true
                                    map.uiSettings.isRotateGesturesEnabled = false
                                    map.setStyle(Style.Builder().fromJson(MAPLIBRE_RASTER_STYLE_JSON)) {
                                        val point = LatLng(selectedLoc.latitude, selectedLoc.longitude)
                                        markerRef.value = map.addMarker(
                                            MarkerOptions()
                                                .position(point)
                                                .title(context.getString(R.string.target_location))
                                        )
                                        currentLocation?.let { location ->
                                            val currentPoint = LatLng(location.latitude, location.longitude)
                                            currentMarkerRef.value = map.addMarker(
                                                MarkerOptions()
                                                    .position(currentPoint)
                                                    .title(context.getString(R.string.current_location))
                                                    .icon(currentLocationIcon)
                                            )
                                        }
                                    }
                                    map.addOnMapClickListener { point ->
                                        viewModel.updateSelectedLocation(point.latitude, point.longitude)
                                        markerRef.value?.position = point
                                        true
                                    }
                                }
                            }
                        },
                        update = {
                            val point = LatLng(selectedLoc.latitude, selectedLoc.longitude)
                            markerRef.value?.position = point

                            currentLocation?.let { location ->
                                val currentPoint = LatLng(location.latitude, location.longitude)
                                val currentMarker = currentMarkerRef.value
                                if (currentMarker == null) {
                                    currentMarkerRef.value = mapLibreMapRef.value?.addMarker(
                                        MarkerOptions()
                                            .position(currentPoint)
                                            .title(context.getString(R.string.current_location))
                                            .icon(currentLocationIcon)
                                    )
                                } else {
                                    currentMarker.position = currentPoint
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    SmallFloatingActionButton(
                        onClick = { viewModel.centerMapOnCurrentLocation() },
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

            // Search Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.search_place_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.search_place_hint)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { triggerSearch() })
                    )
                    FilledIconButton(
                        onClick = triggerSearch,
                        enabled = searchQuery.isNotBlank(),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.search_place_button)
                        )
                    }
                }
            }

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { 
                        pendingStartMock = true
                        attemptStartMockFlow(
                            context = context,
                            viewModel = viewModel,
                            pendingStartMock = pendingStartMock,
                            onPendingStartMockChanged = { pendingStartMock = it },
                            showPermissionDialog = { showPermissionDialog = it },
                            locationPermissionLauncher = locationPermissionLauncher,
                            notificationPermissionLauncher = notificationPermissionLauncher,
                            onBatteryOptimizationRequested = { awaitingBatteryOptimizationResponse = true }
                        )
                    },
                    enabled = !isApplied,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isApplied) Color.LightGray else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        disabledContainerColor = Color.LightGray,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(width = 126.dp, height = 56.dp)
                ) {
                    Icon(Icons.Default.Check, stringResource(R.string.start), modifier = Modifier.size(28.dp))
                }

                Button(
                    onClick = { if (isMocking) viewModel.stopMock() },
                    enabled = isMocking,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMocking) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (isMocking) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(width = 126.dp, height = 56.dp)
                ) {
                    Icon(Icons.Default.Close, stringResource(R.string.stop), modifier = Modifier.size(28.dp))
                }
            }

            Spacer(
                modifier = Modifier
                    .height(12.dp)
                    .navigationBarsPadding()
            )
        }
    }
}

private const val MAPLIBRE_RASTER_STYLE_JSON = """
{
  "version": 8,
  "name": "OSM Raster",
  "sources": {
    "osm": {
      "type": "raster",
      "tiles": [
        "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
      ],
      "tileSize": 256,
      "minzoom": 0,
      "maxzoom": 19,
      "attribution": "© OpenStreetMap contributors"
    }
  },
  "layers": [
    {
      "id": "osm-raster",
      "type": "raster",
      "source": "osm"
    }
  ]
}
"""

private fun createCurrentLocationIcon(context: android.content.Context): org.maplibre.android.annotations.Icon {
    val density = context.resources.displayMetrics.density
    val size = (24 * density).roundToInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = size / 2f

    val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(80, 66, 133, 244)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, size * 0.40f, haloPaint)

    val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, size * 0.28f, outerPaint)

    val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#4285F4")
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, size * 0.18f, innerPaint)

    return IconFactory.getInstance(context).fromBitmap(bitmap)
}

private fun attemptStartMockFlow(
    context: android.content.Context,
    viewModel: MainViewModel,
    pendingStartMock: Boolean,
    onPendingStartMockChanged: (Boolean) -> Unit,
    showPermissionDialog: (Boolean) -> Unit,
    locationPermissionLauncher: ActivityResultLauncher<String>?,
    notificationPermissionLauncher: ActivityResultLauncher<String>?,
    onBatteryOptimizationRequested: () -> Unit,
    allowBatteryOptimizationRequest: Boolean = true
) {
    if (!pendingStartMock) return

    when {
        !SystemCheckUtil.hasLocationPermission(context) -> {
            locationPermissionLauncher?.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        !SystemCheckUtil.hasNotificationPermission(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            notificationPermissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        !SystemCheckUtil.isIgnoringBatteryOptimizations(context) -> {
            if (allowBatteryOptimizationRequest) {
                onBatteryOptimizationRequested()
                SystemCheckUtil.requestBatteryOptimization(context)
            } else {
                onPendingStartMockChanged(false)
                showPermissionDialog(true)
            }
        }
        !SystemCheckUtil.isGpsEnabled(context) || !SystemCheckUtil.isMockLocationEnabled(context) -> {
            onPendingStartMockChanged(false)
            showPermissionDialog(true)
        }
        else -> {
            onPendingStartMockChanged(false)
            viewModel.startMock()
        }
    }
}
