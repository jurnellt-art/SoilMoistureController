package com.anthurium.soilcontroller.ui.logs

import androidx.lifecycle.ViewModel
import com.anthurium.soilcontroller.data.FirebaseLogRepository
import com.anthurium.soilcontroller.model.SensorReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DataLogHistoryUiState(
    val logs: List<SensorReading> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class DataLogHistoryViewModel(private val logRepository: FirebaseLogRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(DataLogHistoryUiState())
    val uiState: StateFlow<DataLogHistoryUiState> = _uiState.asStateFlow()

    fun startListening() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        logRepository.listenForHistory(object : FirebaseLogRepository.HistoryListener {
            override fun onDataChanged(readings: List<SensorReading>) {
                _uiState.value = DataLogHistoryUiState(logs = readings, isLoading = false)
            }

            override fun onError(message: String) {
                _uiState.value = _uiState.value.copy(errorMessage = message, isLoading = false)
            }
        })
    }

    fun stopListening() {
        logRepository.stopListening()
    }

    fun clearHistory() {
        logRepository.clearAll(object : FirebaseLogRepository.WriteCallback {
            override fun onSuccess() {}
            override fun onError(message: String) {
                _uiState.value = _uiState.value.copy(errorMessage = message)
            }
        })
    }
}
