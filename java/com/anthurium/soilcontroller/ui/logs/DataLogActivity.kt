package com.anthurium.soilcontroller.ui.logs

import android.content.Intent
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
import com.anthurium.soilcontroller.data.FirebaseLogRepository
import com.anthurium.soilcontroller.model.SensorReading
import com.anthurium.soilcontroller.net.ArduinoApiClient
import com.anthurium.soilcontroller.ui.theme.SoilMoistureTheme

class DataLogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = AppPrefs(this)
        val apiClient = ArduinoApiClient(prefs.getDeviceBaseUrl())
        val logRepo = FirebaseLogRepository()

        val viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return DataLogViewModel(apiClient, logRepo) as T
            }
        })[DataLogViewModel::class.java]

        setContent {
            val state by viewModel.uiState.collectAsState()
            SoilMoistureTheme {
                DataLogScreen(
                    state = state,
                    onFetchAndSave = { viewModel.fetchAndSave() },
                    onViewHistory = { startActivity(Intent(this, DataLogHistoryActivity::class.java)) },
                    onBack = { finish() }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DataLogActivityPreview() {
    SoilMoistureTheme {
        DataLogScreen(
            state = DataLogUiState(
                lastFetched = SensorReading().apply {
                    soilMoistureLevels = listOf(42f, 40f, 44f)
                    temperatures = listOf(26f)
                }
            ),
            onFetchAndSave = {},
            onViewHistory = {},
            onBack = {}
        )
    }
}
