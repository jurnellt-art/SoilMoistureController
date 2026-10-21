package com.anthurium.soilcontroller.ui.pump

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.anthurium.soilcontroller.data.AppPrefs
import com.anthurium.soilcontroller.net.ArduinoApiClient
import com.anthurium.soilcontroller.ui.theme.SoilMoistureTheme

class PumpControlActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = AppPrefs(this)
        val apiClient = ArduinoApiClient(prefs.getDeviceBaseUrl())

        val viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return PumpControlViewModel(apiClient, prefs) as T
            }
        })[PumpControlViewModel::class.java]

        setContent {
            val state by viewModel.uiState.collectAsState()

            SoilMoistureTheme {
                PumpControlScreen(
                    state = state,
                    onModeChange = { viewModel.setMode(it) },
                    onPumpToggle = { id, on -> viewModel.setPumpState(id, on) },
                    onValveToggle = { id, on -> viewModel.setValveState(id, on) },
                    onMistToggle = { id, on -> viewModel.setMistState(id, on) },
                    onBack = { finish() }
                )
            }
        }
    }
}
