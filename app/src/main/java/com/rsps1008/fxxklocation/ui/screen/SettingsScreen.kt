package com.rsps1008.fxxklocation.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rsps1008.fxxklocation.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val enableDrift by viewModel.enableDrift.collectAsState()
    val driftRadius by viewModel.driftRadius.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            // Random Drift Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Random Drift", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Slightly move the location every 2 seconds to simulate human behavior.",
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
                Text("Drift Radius (meters)", style = MaterialTheme.typography.titleMedium)
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
                    "The maximum distance the location can drift from the center.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
