package com.anthurium.soilcontroller.ui.sensors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anthurium.soilcontroller.data.ThingSpeakRepository
import com.anthurium.soilcontroller.model.SensorReading
import com.anthurium.soilcontroller.net.ArduinoApiClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    private val thingSpeakRepository: ThingSpeakRepository = ThingSpeakRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SensorDataUiState())
    val uiState: StateFlow<SensorDataUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    fun startPolling() {
        if (pollJob?.isActive == true) return
        _uiState.value = _uiState.value.copy(isLoading = _uiState.value.reading == null)
        pollJob = viewModelScope.launch {
            while (isActive) {
                fetchSensorData()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun fetchData() {
        fetchSensorData()
    }

    private fun fetchSensorData() {
        // 1. Try local HTTP direct from Arduino
        apiClient.fetchSensorData(object : ArduinoApiClient.SensorCallback {
            override fun onSuccess(reading: SensorReading) {
                _uiState.value = SensorDataUiState(
                    reading = reading,
                    isLoading = false,
                    lastUpdated = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                )
            }

            override fun onError(localError: String) {
                // 2. If local HTTP fails, fetch live feed from ThingSpeak Cloud
                thingSpeakRepository.fetchLatestFeed(object : ThingSpeakRepository.LatestFeedCallback {
                    override fun onSuccess(reading: SensorReading) {
                        _uiState.value = SensorDataUiState(
                            reading = reading,
                            isLoading = false,
                            lastUpdated = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                        )
                    }

                    override fun onError(tsError: String) {
                        if (_uiState.value.reading == null) {
                            _uiState.value = _uiState.value.copy(
                                errorMessage = "Waiting for Arduino or ThingSpeak data...",
                                isLoading = false
                            )
                        }
                    }
                })
            }
        })
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }

    private companion object {
        const val POLL_INTERVAL_MS = 5000L
    }
}
