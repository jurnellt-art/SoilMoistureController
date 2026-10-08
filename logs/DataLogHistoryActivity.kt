package com.anthurium.soilcontroller.ui.logs

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
import com.anthurium.soilcontroller.data.FirebaseLogRepository
import com.anthurium.soilcontroller.model.SensorReading
import com.anthurium.soilcontroller.ui.theme.SoilMoistureTheme

class DataLogHistoryActivity : ComponentActivity() {
    private lateinit var viewModel: DataLogHistoryViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val logRepo = FirebaseLogRepository()
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return DataLogHistoryViewModel(logRepo) as T
            }
        })[DataLogHistoryViewModel::class.java]

        setContent {
            val state by viewModel.uiState.collectAsState()
            SoilMoistureTheme {
                DataLogHistoryScreen(
                    state = state,
                    onClearHistory = { viewModel.clearHistory() },
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.startListening()
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopListening()
    }
}

@Preview(showBackground = true)
@Composable
fun DataLogHistoryActivityPreview() {
    SoilMoistureTheme {
        DataLogHistoryScreen(
            state = DataLogHistoryUiState(
                logs = listOf(
                    SensorReading().apply {
                        timestamp = "2026-08-13 12:00:00"
                        soilMoisturePercent = 45f
                    }
                )
            ),
            onClearHistory = {},
            onBack = {}
        )
    }
}
