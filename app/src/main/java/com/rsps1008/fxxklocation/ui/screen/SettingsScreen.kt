package com.rsps1008.fxxklocation.ui.screen

import android.Manifest
import android.content.Intent
import android.os.Build
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.rsps1008.fxxklocation.R
import com.rsps1008.fxxklocation.util.SystemCheckUtil
import com.rsps1008.fxxklocation.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val enableDrift by viewModel.enableDrift.collectAsState()
    val driftRadius by viewModel.driftRadius.collectAsState()
    val useGooglePlayServices by viewModel.useGooglePlayServices.collectAsState()
    val useRealAltitude by viewModel.useRealAltitude.collectAsState()
    val lastAltitude by viewModel.lastAltitude.collectAsState()
    val enableAutoStop by viewModel.enableAutoStop.collectAsState()
    val autoStopMinutes by viewModel.autoStopMinutes.collectAsState()
    val autoStartOnLaunch by viewModel.autoStartOnLaunch.collectAsState()
    val hasPermission by viewModel.hasPermission.collectAsState()
    val hasNotificationPermission by viewModel.hasNotificationPermission.collectAsState()
    val isGpsEnabled by viewModel.isGpsEnabled.collectAsState()
    val isMockAppSet by viewModel.isMockAppSet.collectAsState()
    val isIgnoringBatteryOptimizations by viewModel.isIgnoringBatteryOptimizations.collectAsState()
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.checkStatus()
        viewModel.refreshStatusAfterTransition()
        viewModel.refreshBatteryOptimizationStatusAfterTransition()
        if (!granted) {
            SystemCheckUtil.openAppSettings(context)
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.checkStatus()
        viewModel.refreshStatusAfterTransition()
        viewModel.refreshBatteryOptimizationStatusAfterTransition()
        if (!granted) {
            SystemCheckUtil.openAppSettings(context)
        }
    }

    var driftRadiusInput by remember { mutableStateOf("") }
    var manualAltitudeInput by remember { mutableStateOf("") }
    var autoStopMinutesInput by remember { mutableStateOf("") }
    
    // Sync string state with Double state if it changes from outside
    LaunchedEffect(driftRadius) {
        if (driftRadius.toString() != driftRadiusInput && driftRadius != driftRadiusInput.toDoubleOrNull()) {
            driftRadiusInput = if (driftRadius % 1.0 == 0.0) {
                driftRadius.toInt().toString()
            } else {
                driftRadius.toString()
            }
        }
    }

    // Sync manual altitude input
    LaunchedEffect(lastAltitude) {
        if (lastAltitude.toString() != manualAltitudeInput && lastAltitude != manualAltitudeInput.toDoubleOrNull()) {
            manualAltitudeInput = if (lastAltitude % 1.0 == 0.0) {
                lastAltitude.toInt().toString()
            } else {
                lastAltitude.toString()
            }
        }
    }

    // Sync auto stop minutes input
    LaunchedEffect(autoStopMinutes) {
        if (autoStopMinutes.toString() != autoStopMinutesInput) {
            autoStopMinutesInput = autoStopMinutes.toString()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkStatus()
                viewModel.refreshStatusAfterTransition()
                viewModel.refreshBatteryOptimizationStatusAfterTransition()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 0. Permissions Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.system_permissions), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        StatusItem(stringResource(R.string.location_permission), hasPermission) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            } else {
                                SystemCheckUtil.openAppSettings(context)
                            }
                        }
                        StatusItem(stringResource(R.string.notification_permission), hasNotificationPermission) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                SystemCheckUtil.openAppSettings(context)
                            }
                        }
                        StatusItem(stringResource(R.string.gps_enabled), isGpsEnabled) { SystemCheckUtil.openLocationSettings(context) }
                        StatusItem(stringResource(R.string.mock_app_selected), isMockAppSet) { SystemCheckUtil.openDevelopmentSettings(context) }
                        StatusItem(stringResource(R.string.ignore_battery_optimization), isIgnoringBatteryOptimizations) { SystemCheckUtil.requestBatteryOptimization(context) }
                    }
                }
            }

            HorizontalDivider()

            // 1. Auto-start Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.auto_start_launch), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.auto_start_launch_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoStartOnLaunch,
                    onCheckedChange = { viewModel.setAutoStartOnLaunch(it) }
                )
            }

            HorizontalDivider()

            // 2. Auto-stop Section
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.auto_stop), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.auto_stop_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enableAutoStop,
                        onCheckedChange = { viewModel.setEnableAutoStop(it) }
                    )
                }

                if (enableAutoStop) {
                    OutlinedTextField(
                        value = autoStopMinutesInput,
                        onValueChange = {
                            autoStopMinutesInput = it
                            it.toIntOrNull()?.let { minutes -> viewModel.setAutoStopMinutes(minutes) }
                        },
                        label = { Text(stringResource(R.string.stop_after_minutes)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }

            HorizontalDivider()

            // 3. Random Drift Switch & Input
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.random_drift), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.random_drift_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enableDrift,
                        onCheckedChange = { viewModel.setEnableDrift(it) }
                    )
                }

                if (enableDrift) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.drift_radius_meters), style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            value = driftRadiusInput,
                            onValueChange = { 
                                driftRadiusInput = it
                                it.toDoubleOrNull()?.let { radius -> viewModel.setDriftRadius(radius) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        Text(
                            stringResource(R.string.drift_radius_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider()

            // 4. Real Altitude Selection
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.use_real_altitude), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.use_real_altitude_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (useRealAltitude) {
                            Text(
                                stringResource(R.string.current_altitude, lastAltitude),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Switch(
                        checked = useRealAltitude,
                        onCheckedChange = { viewModel.setUseRealAltitude(it) }
                    )
                }

                if (!useRealAltitude) {
                    OutlinedTextField(
                        value = manualAltitudeInput,
                        onValueChange = {
                            manualAltitudeInput = it
                            it.toDoubleOrNull()?.let { alt -> viewModel.setManualAltitude(alt) }
                        },
                        label = { Text(stringResource(R.string.manual_altitude)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            }

            HorizontalDivider()

            // 5. Google Services Selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.use_google_services), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.use_google_services_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = useGooglePlayServices,
                    onCheckedChange = { viewModel.setUseGooglePlayServices(it) }
                )
            }

            HorizontalDivider()

            // 6. Privacy Policy
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.privacy_policy),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.privacy_policy_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://rsps1008.github.io/FxxkLocation/privacy-policy/")
                        )
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.open_privacy_policy))
                }
            }
        }
    }
}

@Composable
private fun StatusItem(label: String, isOk: Boolean, onClick: () -> Unit) {
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
            Text(if (isOk) stringResource(R.string.ok) else stringResource(R.string.fix), color = Color.White)
        }
    }
}
