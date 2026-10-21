package com.anthurium.soilcontroller.ui.logs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.anthurium.soilcontroller.model.SensorReading
import com.anthurium.soilcontroller.ui.theme.SoilMoistureTheme
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataLogHistoryScreen(
    state: DataLogHistoryUiState,
    onClearHistory: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data Log History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onClearHistory) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All")
                    }
                }
            )
        }
    ) { padding ->
        if (state.logs.isEmpty() && !state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Inbox,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.LightGray
                    )
                    Text("No logs found", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.logs) { log ->
                    LogItemCard(log)
                }
            }
        }
        
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun LogItemCard(log: SensorReading) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = log.timestamp ?: "Unknown time",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = log.mode ?: "MANUAL",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val avgSoil = if (log.soilMoistureLevels.isEmpty()) 0f else log.soilMoistureLevels.average().toFloat()
                val avgTemp = if (log.temperatures.isEmpty()) 0f else log.temperatures.average().toFloat()
                
                LogMetric("Avg Soil", String.format(Locale.getDefault(), "%.0f%%", avgSoil))
                LogMetric("Avg Temp", String.format(Locale.getDefault(), "%.1f°C", avgTemp))
                LogMetric("pH", String.format(Locale.getDefault(), "%.1f", log.phLevel))
                LogMetric("Pumps", if (log.pumpStates.contains(true)) "ON" else "OFF")
            }
        }
    }
}

@Composable
fun LogMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Black)
    }
}

@Preview(showBackground = true)
@Composable
fun DataLogHistoryPreview() {
    SoilMoistureTheme {
        DataLogHistoryScreen(
            state = DataLogHistoryUiState(
                logs = listOf(
                    SensorReading().apply {
                        timestamp = "2026-08-13 12:00:00"
                        soilMoistureLevels = listOf(45f, 40f, 50f)
                        temperatures = listOf(24f)
                    },
                    SensorReading().apply {
                        timestamp = "2026-08-13 11:00:00"
                        soilMoistureLevels = listOf(40f)
                        temperatures = listOf(25f)
                    }
                )
            ),
            onClearHistory = {},
            onBack = {}
        )
    }
}
