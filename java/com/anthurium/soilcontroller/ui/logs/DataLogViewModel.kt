package com.anthurium.soilcontroller.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anthurium.soilcontroller.data.FirebaseLogRepository
import com.anthurium.soilcontroller.model.SensorReading
import com.anthurium.soilcontroller.net.ArduinoApiClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DataLogUiState(
    val lastFetched: SensorReading? = null,
    val isLoading: Boolean = false,
    val message: String? = null
)

class DataLogViewModel(
    private val apiClient: ArduinoApiClient,
    private val logRepository: FirebaseLogRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(DataLogUiState())
    val uiState: StateFlow<DataLogUiState> = _uiState.asStateFlow()

    fun fetchAndSave() {
        _uiState.value = _uiState.value.copy(isLoading = true, message = "Fetching data...")
        apiClient.fetchSensorData(object : ArduinoApiClient.SensorCallback {
            override fun onSuccess(reading: SensorReading) {
                saveToFirebase(reading)
            }

            override fun onError(message: String) {
                _uiState.value = _uiState.value.copy(isLoading = false, message = "Error fetching: $message")
                clearMessageAfterDelay()
            }
        })
    }

    private fun saveToFirebase(reading: SensorReading) {
        logRepository.saveReading(reading, object : FirebaseLogRepository.WriteCallback {
            override fun onSuccess() {
                _uiState.value = DataLogUiState(lastFetched = reading, message = "Snapshot saved to cloud")
                clearMessageAfterDelay()
            }

            override fun onError(message: String) {
                _uiState.value = _uiState.value.copy(isLoading = false, message = "Error saving: $message")
                clearMessageAfterDelay()
            }
        })
    }

    private fun clearMessageAfterDelay() {
        viewModelScope.launch {
            delay(3000)
            _uiState.value = _uiState.value.copy(message = null)
        }
    }
}
