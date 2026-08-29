package com.rsps1008.fxxklocation.ui.screen

import android.Manifest
import android.content.Intent
import android.os.Build
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.rsps1008.fxxklocation.R
import com.rsps1008.fxxklocation.util.SystemCheckUtil
import com.rsps1008.fxxklocation.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay

private const val SETTING_INPUT_COMMIT_DEBOUNCE_MILLIS = 450L

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

    val driftRadiusInput = rememberDeferredSettingInput(
        externalValue = driftRadius,
        format = ::formatSettingDouble,
        parse = String::toDoubleOrNull,
        save = viewModel::setDriftRadius
    )
    val manualAltitudeInput = rememberDeferredSettingInput(
        externalValue = lastAltitude,
        format = ::formatSettingDouble,
        parse = String::toDoubleOrNull,
        save = viewModel::setManualAltitude
    )
    val autoStopMinutesInput = rememberDeferredSettingInput(
        externalValue = autoStopMinutes,
        format = Int::toString,
        parse = String::toIntOrNull,
        save = viewModel::setAutoStopMinutes
    )

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
                        value = autoStopMinutesInput.text,
                        onValueChange = autoStopMinutesInput::onTextChanged,
                        label = { Text(stringResource(R.string.stop_after_minutes)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                autoStopMinutesInput.onFocusChanged(
                                    isFocusedNow = focusState.isFocused,
                                    externalValue = autoStopMinutes,
                                    format = Int::toString,
                                    parse = String::toIntOrNull,
                                    save = viewModel::setAutoStopMinutes
                                )
                            },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                autoStopMinutesInput.commit(
                                    externalValue = autoStopMinutes,
                                    parse = String::toIntOrNull,
                                    save = viewModel::setAutoStopMinutes
                                )
                            }
                        )
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
                            value = driftRadiusInput.text,
                            onValueChange = driftRadiusInput::onTextChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focusState ->
                                    driftRadiusInput.onFocusChanged(
                                        isFocusedNow = focusState.isFocused,
                                        externalValue = driftRadius,
                                        format = ::formatSettingDouble,
                                        parse = String::toDoubleOrNull,
                                        save = viewModel::setDriftRadius
                                    )
                                },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    driftRadiusInput.commit(
                                        externalValue = driftRadius,
                                        parse = String::toDoubleOrNull,
                                        save = viewModel::setDriftRadius
                                    )
                                }
                            )
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
                        value = manualAltitudeInput.text,
                        onValueChange = manualAltitudeInput::onTextChanged,
                        label = { Text(stringResource(R.string.manual_altitude)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                manualAltitudeInput.onFocusChanged(
                                    isFocusedNow = focusState.isFocused,
                                    externalValue = lastAltitude,
                                    format = ::formatSettingDouble,
                                    parse = String::toDoubleOrNull,
                                    save = viewModel::setManualAltitude
                                )
                            },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                manualAltitudeInput.commit(
                                    externalValue = lastAltitude,
                                    parse = String::toDoubleOrNull,
                                    save = viewModel::setManualAltitude
                                )
                            }
                        )
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

internal class DeferredSettingInput<T> {
    var text by mutableStateOf("")
        private set
    private var isDirty by mutableStateOf(false)
    private var isFocused by mutableStateOf(false)
    private var pendingValue by mutableStateOf<T?>(null)

    val hasPendingEdit: Boolean
        get() = isDirty

    fun onTextChanged(value: String) {
        text = value
        isDirty = true
        pendingValue = null
    }

    fun restoreText(value: String, hasPendingEdit: Boolean) {
        text = value
        isDirty = hasPendingEdit
        pendingValue = null
    }

    fun commit(
        externalValue: T,
        parse: (String) -> T?,
        save: (T) -> Unit
    ) {
        if (!isDirty) return

        val parsedValue = parse(text) ?: return
        if (pendingValue == parsedValue) return

        if (parsedValue == externalValue) {
            pendingValue = null
            isDirty = false
        } else {
            pendingValue = parsedValue
            save(parsedValue)
        }
    }

    fun onFocusChanged(
        isFocusedNow: Boolean,
        externalValue: T,
        format: (T) -> String,
        parse: (String) -> T?,
        save: (T) -> Unit
    ) {
        if (isFocused && !isFocusedNow) {
            if (isDirty) {
                commit(externalValue, parse, save)
            } else if (pendingValue == null) {
                text = format(externalValue)
            }
        }
        isFocused = isFocusedNow
    }

    fun syncFromExternal(externalValue: T, format: (T) -> String) {
        val pending = pendingValue
        if (pending != null && pending == externalValue) {
            pendingValue = null
            isDirty = false
            return
        }

        if (!isFocused && !isDirty && pending == null) {
            text = format(externalValue)
        }
    }
}

@Composable
private fun <T> rememberDeferredSettingInput(
    externalValue: T,
    format: (T) -> String,
    parse: (String) -> T?,
    save: (T) -> Unit
): DeferredSettingInput<T> {
    val state = rememberSaveable(
        saver = listSaver<DeferredSettingInput<T>, Any>(
            save = { state -> listOf(state.text, state.hasPendingEdit) },
            restore = { savedState ->
                val savedText = savedState.firstOrNull() as? String ?: ""
                val hasPendingEdit = savedState.getOrNull(1) as? Boolean ?: false
                DeferredSettingInput<T>().also { it.restoreText(savedText, hasPendingEdit) }
            }
        )
    ) {
        DeferredSettingInput<T>()
    }
    val latestExternalValue by rememberUpdatedState(externalValue)
    val latestParse by rememberUpdatedState(parse)
    val latestSave by rememberUpdatedState(save)

    LaunchedEffect(externalValue) {
        state.syncFromExternal(externalValue, format)
    }

    LaunchedEffect(state.text) {
        if (!state.hasPendingEdit) return@LaunchedEffect
        delay(SETTING_INPUT_COMMIT_DEBOUNCE_MILLIS)
        state.commit(latestExternalValue, latestParse, latestSave)
    }

    DisposableEffect(Unit) {
        onDispose {
            state.commit(latestExternalValue, latestParse, latestSave)
        }
    }

    return state
}

private fun formatSettingDouble(value: Double): String = if (value % 1.0 == 0.0) {
    value.toInt().toString()
} else {
    value.toString()
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
