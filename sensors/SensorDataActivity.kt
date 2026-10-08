package com.anthurium.soilcontroller.ui.sensors

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.anthurium.soilcontroller.data.AppPrefs
import com.anthurium.soilcontroller.model.SensorReading
import com.anthurium.soilcontroller.net.ArduinoApiClient
import com.anthurium.soilcontroller.ui.theme.SoilMoistureTheme

class SensorDataActivity : ComponentActivity() {

    private lateinit var viewModel: SensorDataViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = AppPrefs(this)
        val apiClient = ArduinoApiClient(prefs.getDeviceBaseUrl())

        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SensorDataViewModel(apiClient) as T
            }
        })[SensorDataViewModel::class.java]

        setContent {
            val state by viewModel.uiState.collectAsState()

            SoilMoistureTheme {
                SensorDataScreen(
                    state = state,
                    onRefresh = { viewModel.fetchData() },
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.startPolling()
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopPolling()
    }
}

@Preview(showBackground = true)
@Composable
fun SensorDataActivityPreview() {
    SoilMoistureTheme {
        SensorDataScreen(
            state = SensorDataUiState(
                reading = SensorReading().apply {
                    soilMoisturePercent = 45f
                    temperatureC = 24f
                    humidityPercent = 60f
                    phLevel = 6.5f
                }
            ),
            onRefresh = {},
            onBack = {}
        )
    }
}
