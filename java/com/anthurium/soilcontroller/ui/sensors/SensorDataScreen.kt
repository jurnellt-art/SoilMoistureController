package com.anthurium.soilcontroller.ui.sensors

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anthurium.soilcontroller.model.SensorReading
import androidx.compose.ui.tooling.preview.Preview
import com.anthurium.soilcontroller.ui.theme.SoilMoistureTheme
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorDataScreen(
    state: SensorDataUiState,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("6-Plant Live Sensors") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            state.reading?.let { reading ->
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item(span = { GridItemSpan(2) }) {
                        EnvironmentSection(reading)
                    }

                    item(span = { GridItemSpan(2) }) {
                        Text("Plant Soil Moisture", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    
                    items(reading.soilMoistureLevels.size) { index ->
                        CircularGaugeCard(
                            label = "Plant ${index + 1}",
                            value = reading.soilMoistureLevels[index],
                            unit = "%",
                            color = Color(0xFF2196F3)
                        )
                    }
                }
            } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.isLoading) {
                    CircularProgressIndicator()
                } else {
                    Text(state.errorMessage ?: "No data available")
                }
            }
        }
    }
}

@Composable
fun EnvironmentSection(reading: SensorReading) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Cloud, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("General Environment", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val avgTemp = if (reading.temperatures.isEmpty()) 0f else reading.temperatures.average().toFloat()
                val avgHum = if (reading.humidities.isEmpty()) 0f else reading.humidities.average().toFloat()
                
                EnvMetric("Temp", String.format(Locale.getDefault(), "%.1f°C", avgTemp))
                EnvMetric("Humidity", String.format(Locale.getDefault(), "%.1f%%", avgHum))
                EnvMetric("pH Level", String.format(Locale.getDefault(), "%.1f", reading.phLevel))
            }
        }
    }
}

@Composable
fun EnvMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
    }
}

@Composable
fun CircularGaugeCard(
    label: String,
    value: Float,
    unit: String,
    color: Color,
    max: Float = 100f
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "squish"
    )

    val animatedValue by animateFloatAsState(
        targetValue = value / max,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "gauge"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "low_moisture_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val gaugeAlpha = if (value < 25f) pulseAlpha else 1f

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = {}
            ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(70.dp)) {
                    drawArc(
                        color = color.copy(alpha = 0.2f),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = color.copy(alpha = gaugeAlpha),
                        startAngle = 135f,
                        sweepAngle = 270f * animatedValue.coerceIn(0f, 1f),
                        useCenter = false,
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${value.toInt()}$unit",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SensorDataPreview() {
    SoilMoistureTheme {
        SensorDataScreen(
            state = SensorDataUiState(
                reading = SensorReading().apply {
                    soilMoistureLevels = listOf(45f, 30f, 65f, 20f, 80f, 50f)
                    temperatures = listOf(24f, 25f, 23f)
                    humidities = listOf(60f, 62f, 58f)
                    phLevel = 6.5f
                }
            ),
            onRefresh = {},
            onBack = {}
        )
    }
}
