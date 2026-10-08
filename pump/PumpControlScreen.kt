package com.anthurium.soilcontroller.ui.pump

import androidx.compose.animation.core.*
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.anthurium.soilcontroller.ui.theme.SoilMoistureTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PumpControlScreen(
    state: PumpControlUiState,
    onModeChange: (Boolean) -> Unit,
    onPumpToggle: (Int, Boolean) -> Unit,
    onValveToggle: (Int, Boolean) -> Unit,
    onMistToggle: (Int, Boolean) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("6-Plant Water Control") },
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
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Mode Selector
            item {
                ModeCard(isManual = state.isManual, onModeChange = onModeChange)
            }

            // Pumps Section
            item {
                ControlHeader(title = "Water Pumps", icon = Icons.Default.WaterDrop)
            }
            
            items(state.pumpStates.size) { index ->
                ToggleCard(
                    title = "Pump ${index + 1}",
                    icon = Icons.Default.WaterDrop,
                    isOn = state.pumpStates[index],
                    onToggle = { onPumpToggle(index + 1, it) },
                    enabled = state.isManual
                )
            }

            // Valves Section
            item {
                ControlHeader(title = "Plant Valves", icon = Icons.Default.Grass)
            }
            
            items(state.valveStates.size) { index ->
                ToggleCard(
                    title = "Valve ${index + 1} (Plant ${index + 1})",
                    icon = Icons.Default.Grass,
                    isOn = state.valveStates[index],
                    onToggle = { onValveToggle(index + 1, it) },
                    enabled = state.isManual
                )
            }

            // Mist makers Section
            item {
                ControlHeader(title = "Mist Makers", icon = Icons.Default.Cloud)
            }
            
            items(state.mistStates.size) { index ->
                ToggleCard(
                    title = "Mist Maker ${index + 1}",
                    icon = Icons.Default.Cloud,
                    isOn = state.mistStates[index],
                    onToggle = { onMistToggle(index + 1, it) },
                    enabled = state.isManual
                )
            }

            if (state.isLoading) {
                item {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
fun ModeCard(isManual: Boolean, onModeChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("System Mode", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Button(
                    onClick = { onModeChange(true) },
                    modifier = Modifier.weight(1f).padding(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isManual) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (isManual) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Manual") }
                Button(
                    onClick = { onModeChange(false) },
                    modifier = Modifier.weight(1f).padding(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isManual) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (!isManual) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Auto") }
            }
        }
    }
}

@Composable
fun ControlHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ToggleCard(
    title: String,
    icon: ImageVector,
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
    enabled: Boolean
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "squish"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotate"
    )
    val animatedRotation = if (isOn) rotation else 0f

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = { onToggle(!isOn) }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isOn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(animatedRotation),
                    tint = if (isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(title, fontWeight = FontWeight.SemiBold)
            }
            Switch(
                checked = isOn,
                onCheckedChange = onToggle,
                enabled = enabled
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PumpControlPreview() {
    SoilMoistureTheme {
        PumpControlScreen(
            state = PumpControlUiState(
                isManual = true,
                pumpStates = listOf(true, false),
                valveStates = listOf(false, true, false, false, false, false),
                mistStates = listOf(false, false, false, false)
            ),
            onModeChange = {},
            onPumpToggle = { _, _ -> },
            onValveToggle = { _, _ -> },
            onMistToggle = { _, _ -> },
            onBack = {}
        )
    }
}
