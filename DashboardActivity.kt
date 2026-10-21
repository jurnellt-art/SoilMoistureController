package com.anthurium.soilcontroller.ui.dashboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.anthurium.soilcontroller.data.AppPrefs
import com.anthurium.soilcontroller.service.SensorMonitoringService
import com.anthurium.soilcontroller.ui.logs.DataLogActivity
import com.anthurium.soilcontroller.ui.pump.PumpControlActivity
import com.anthurium.soilcontroller.ui.sensors.SensorDataActivity
import com.anthurium.soilcontroller.ui.settings.SettingsActivity
import com.anthurium.soilcontroller.ui.theme.SoilMoistureTheme

class DashboardActivity : ComponentActivity() {

    private lateinit var viewModel: DashboardViewModel

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startMonitoringService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = AppPrefs(this)
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return DashboardViewModel(prefs) as T
            }
        })[DashboardViewModel::class.java]

        checkAndStartMonitoringService()

        setContent {
            val state by viewModel.uiState.collectAsState()

            SoilMoistureTheme {
                DashboardScreen(
                    state = state,
                    onSensorDataClick = {
                        startActivity(Intent(this, SensorDataActivity::class.java))
                    },
                    onControlPumpClick = {
                        startActivity(Intent(this, PumpControlActivity::class.java))
                    },
                    onDataLogClick = {
                        startActivity(Intent(this, DataLogActivity::class.java))
                    },
                    onSettingsClick = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    }
                )
            }
        }
    }

    private fun checkAndStartMonitoringService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                startMonitoringService()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startMonitoringService()
        }
    }

    private fun startMonitoringService() {
        val intent = Intent(this, SensorMonitoringService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshStatus()
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardActivityPreview() {
    SoilMoistureTheme {
        DashboardScreen(
            state = DashboardUiState(
                deviceUrl = "http://192.168.1.50",
                mode = "MANUAL",
                connectionStatus = "Connected"
            ),
            onSensorDataClick = {},
            onControlPumpClick = {},
            onDataLogClick = {},
            onSettingsClick = {}
        )
    }
}
