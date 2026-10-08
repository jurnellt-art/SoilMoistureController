package com.anthurium.soilcontroller.ui.logs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.anthurium.soilcontroller.model.SensorReading
import com.anthurium.soilcontroller.ui.theme.SoilMoistureTheme
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataLogScreen(
    state: DataLogUiState,
    onFetchAndSave: () -> Unit,
    onViewHistory: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log Snapshot") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Camera, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Current Snapshot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (state.lastFetched != null) {
                            val reading = state.lastFetched
                            SnapshotDetailRow("Moisture Avg", String.format(Locale.getDefault(), "%.1f%%", reading.soilMoistureLevels.average()))
                            SnapshotDetailRow("Temp Avg", String.format(Locale.getDefault(), "%.1f°C", reading.temperatures.average()))
                            SnapshotDetailRow("pH Level", String.format(Locale.getDefault(), "%.2f", reading.phLevel))
                            SnapshotDetailRow("Mode", reading.mode ?: "MANUAL")
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            reading.soilMoistureLevels.forEachIndexed { index, level ->
                                SnapshotDetailRow("Plant ${index + 1}", String.format(Locale.getDefault(), "%.0f%%", level))
                            }
                        } else {
                            Text(
                                "No snapshot taken yet. Click the button below to fetch live data and save it to history.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (state.isLoading) {
                item {
                    CircularProgressIndicator()
                }
            }

            state.message?.let {
                item {
                    Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }

            item {
                Button(
                    onClick = onFetchAndSave,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !state.isLoading
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("FETCH & SAVE TO CLOUD", fontWeight = FontWeight.Bold)
                }
            }

            item {
                OutlinedButton(
                    onClick = onViewHistory,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.History, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("VIEW ALL HISTORY")
                }
            }
        }
    }
}

@Composable
fun SnapshotDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.ExtraBold)
    }
}

@Preview(showBackground = true)
@Composable
fun DataLogPreview() {
    SoilMoistureTheme {
        DataLogScreen(
            state = DataLogUiState(
                lastFetched = SensorReading().apply {
                    soilMoistureLevels = listOf(42f, 38f, 50f, 44f, 40f, 46f)
                    temperatures = listOf(26f, 26f, 26f)
                    phLevel = 6.4f
                }
            ),
            onFetchAndSave = {},
            onViewHistory = {},
            onBack = {}
        )
    }
}
