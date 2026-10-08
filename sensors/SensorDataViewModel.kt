package com.anthurium.soilcontroller.ui.sensors

import androidx.lifecycle.ViewModel
import com.anthurium.soilcontroller.data.FirebaseLiveRepository
import com.anthurium.soilcontroller.model.SensorReading
import com.anthurium.soilcontroller.net.ArduinoApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SensorDataUiState(
    val reading: SensorReading? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val lastUpdated: String? = null
)

class SensorDataViewModel(
    private val apiClient: ArduinoApiClient,
    private val liveRepository: FirebaseLiveRepository = FirebaseLiveRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(SensorDataUiState())
    val uiState: StateFlow<SensorDataUiState> = _uiState.asStateFlow()

    fun startPolling() {
        _uiState.value = _uiState.value.copy(isLoading = _uiState.value.reading == null)

        // 1. Listen to Firebase Realtime Cloud Data
        liveRepository.startListeningLiveData(object : FirebaseLiveRepository.LiveDataListener {
            override fun onDataReceived(reading: SensorReading) {
                _uiState.value = SensorDataUiState(
                    reading = reading,
                    isLoading = false,
                    lastUpdated = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                )
            }

            override fun onError(message: String) {
                // If Firebase Cloud is empty/waiting, fallback to local HTTP API
                fetchLocalData()
            }
        })

        // Also attempt a direct local HTTP fetch once
        fetchLocalData()
    }

    fun stopPolling() {
        liveRepository.stopListeningLiveData()
    }

    fun fetchData() {
        fetchLocalData()
    }

    private fun fetchLocalData() {
        apiClient.fetchSensorData(object : ArduinoApiClient.SensorCallback {
            override fun onSuccess(reading: SensorReading) {
                _uiState.value = SensorDataUiState(
                    reading = reading,
                    isLoading = false,
                    lastUpdated = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                )
            }

            override fun onError(message: String) {
                if (_uiState.value.reading == null) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Cloud & Local Connection Waiting: $message",
                        isLoading = false
                    )
                }
            }
        })
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
