package com.anthurium.soilcontroller.ui.logs

import androidx.lifecycle.ViewModel
import com.anthurium.soilcontroller.data.ThingSpeakRepository
import com.anthurium.soilcontroller.model.SensorReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DataLogHistoryUiState(
    val logs: List<SensorReading> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class DataLogHistoryViewModel(
    private val thingSpeakRepository: ThingSpeakRepository = ThingSpeakRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataLogHistoryUiState())
    val uiState: StateFlow<DataLogHistoryUiState> = _uiState.asStateFlow()

    fun startListening() {
        _uiState.value = _uiState.value.copy(isLoading = true)

        // Load history feeds from ThingSpeak Cloud
        thingSpeakRepository.fetchFeedHistory(30, object : ThingSpeakRepository.FeedCallback {
            override fun onSuccess(readings: List<SensorReading>) {
                _uiState.value = DataLogHistoryUiState(logs = readings, isLoading = false)
            }

            override fun onError(message: String) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "No history feeds in ThingSpeak Cloud yet",
                    isLoading = false
                )
            }
        })
    }

    fun stopListening() {
        // Nothing to stop
    }

    fun clearHistory() {
        _uiState.value = DataLogHistoryUiState(logs = emptyList(), isLoading = false, errorMessage = "ThingSpeak history cleared from view")
    }
}
