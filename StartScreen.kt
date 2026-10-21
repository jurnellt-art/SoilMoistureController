package com.anthurium.soilcontroller.ui.start

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anthurium.soilcontroller.ui.theme.SoilMoistureTheme

@Composable
fun StartScreen(onStart: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "scale")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primaryContainer
                    )
                )
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier
                .size(160.dp)
                .scale(scale),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Grass,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = "Soil Moisture\nController",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onPrimary,
            lineHeight = 44.sp
        )
        
        Spacer(modifier = Modifier.height(80.dp))
        
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ),
            shape = MaterialTheme.shapes.large
        ) {
            Text("GET STARTED", fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun StartScreenPreview() {
    SoilMoistureTheme {
        StartScreen {}
    }
}
