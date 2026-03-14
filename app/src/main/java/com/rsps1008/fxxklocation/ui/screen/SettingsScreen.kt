package com.rsps1008.fxxklocation.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
    val hasPermission by viewModel.hasPermission.collectAsState()
    val isGpsEnabled by viewModel.isGpsEnabled.collectAsState()
    val isMockAppSet by viewModel.isMockAppSet.collectAsState()
    val isIgnoringBatteryOptimizations by viewModel.isIgnoringBatteryOptimizations.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkStatus()
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
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
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
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Permissions Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.system_permissions), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        StatusItem(stringResource(R.string.location_permission), hasPermission) { SystemCheckUtil.openAppSettings(context) }
                        StatusItem(stringResource(R.string.gps_enabled), isGpsEnabled) { SystemCheckUtil.openLocationSettings(context) }
                        StatusItem(stringResource(R.string.mock_app_selected), isMockAppSet) { SystemCheckUtil.openDevelopmentSettings(context) }
                        StatusItem(stringResource(R.string.ignore_battery_optimization), isIgnoringBatteryOptimizations) { SystemCheckUtil.openBatteryOptimizationSettings(context) }
                    }
                }
            }

            HorizontalDivider()

            // Random Drift Switch
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

            // Drift Radius Input
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.drift_radius_meters), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = driftRadius.toString(),
                    onValueChange = { 
                        it.toDoubleOrNull()?.let { radius -> viewModel.setDriftRadius(radius) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = enableDrift
                )
                Text(
                    stringResource(R.string.drift_radius_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            // Google Services Selection
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
