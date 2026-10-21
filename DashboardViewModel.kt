package com.anthurium.soilcontroller.ui.dashboard

import androidx.lifecycle.ViewModel
import com.anthurium.soilcontroller.data.AppPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DashboardUiState(
    val deviceUrl: String = "",
    val mode: String = "MANUAL",
    val connectionStatus: String = "Disconnected"
)

class DashboardViewModel(private val prefs: AppPrefs) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    fun refreshStatus() {
        _uiState.value = DashboardUiState(
            deviceUrl = prefs.getDeviceBaseUrl() ?: "Not set",
            mode = prefs.getMode() ?: "MANUAL",
            connectionStatus = "Device: ${prefs.getDeviceBaseUrl()}"
        )
    }
}
