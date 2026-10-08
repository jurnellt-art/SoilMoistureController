package com.anthurium.soilcontroller.ui.pump

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anthurium.soilcontroller.data.AppPrefs
import com.anthurium.soilcontroller.data.FirebaseLiveRepository
import com.anthurium.soilcontroller.model.SensorReading
import com.anthurium.soilcontroller.net.ArduinoApiClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PumpControlUiState(
    val isManual: Boolean = true,
    val pumpStates: List<Boolean> = listOf(false, false),
    val valveStates: List<Boolean> = listOf(false, false, false, false, false, false),
    val mistStates: List<Boolean> = listOf(false, false, false, false),
    val isLoading: Boolean = false,
    val message: String? = null
)

class PumpControlViewModel(
    private val apiClient: ArduinoApiClient,
    private val prefs: AppPrefs,
    private val liveRepository: FirebaseLiveRepository = FirebaseLiveRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PumpControlUiState(isManual = "MANUAL".equals(prefs.getMode(), ignoreCase = true))
    )
    val uiState: StateFlow<PumpControlUiState> = _uiState.asStateFlow()

    init {
        // Start listening to real-time status from Firebase Cloud
        liveRepository.startListeningLiveData(object : FirebaseLiveRepository.LiveDataListener {
            override fun onDataReceived(reading: SensorReading) {
                val isManualMode = "MANUAL".equals(reading.mode ?: "MANUAL", ignoreCase = true)
                prefs.setMode(if (isManualMode) "MANUAL" else "AUTOMATIC")

                _uiState.value = _uiState.value.copy(
                    isManual = isManualMode,
                    pumpStates = if (reading.pumpStates.isNotEmpty()) reading.pumpStates else _uiState.value.pumpStates,
                    valveStates = if (reading.valveStates.isNotEmpty()) reading.valveStates else _uiState.value.valveStates,
                    mistStates = if (reading.mistStates.isNotEmpty()) reading.mistStates else _uiState.value.mistStates,
                    isLoading = false
                )
            }

            override fun onError(message: String) {
                // Silently ignore in UI background listener
            }
        })
    }

    fun setMode(manual: Boolean) {
        val modeStr = if (manual) "MANUAL" else "AUTOMATIC"
        _uiState.value = _uiState.value.copy(isLoading = true)

        // 1. Send Mode command to Firebase Cloud
        liveRepository.setMode(modeStr, object : FirebaseLiveRepository.CommandCallback {
            override fun onSuccess() {
                prefs.setMode(modeStr)
                _uiState.value = _uiState.value.copy(isManual = manual, isLoading = false, message = "Mode updated to $modeStr")
                clearMessageAfterDelay()
            }

            override fun onError(message: String) {
                // Fallback to local HTTP
                apiClient.setMode(modeStr.lowercase(), object : ArduinoApiClient.SimpleCallback {
                    override fun onSuccess(response: String) {
                        prefs.setMode(modeStr)
                        _uiState.value = _uiState.value.copy(isManual = manual, isLoading = false, message = "Mode updated")
                        clearMessageAfterDelay()
                    }

                    override fun onError(msg: String) {
                        _uiState.value = _uiState.value.copy(isLoading = false, message = "Error updating mode: $msg")
                        clearMessageAfterDelay()
                    }
                })
            }
        })
    }

    fun setPumpState(id: Int, on: Boolean) {
        if (!_uiState.value.isManual) return // Ignore in Auto mode

        _uiState.value = _uiState.value.copy(isLoading = true)
        val pumpIdx = id - 1

        liveRepository.setPumpState(pumpIdx, on, object : FirebaseLiveRepository.CommandCallback {
            override fun onSuccess() {
                val newPumps = _uiState.value.pumpStates.toMutableList()
                if (pumpIdx in 0 until newPumps.size) newPumps[pumpIdx] = on
                _uiState.value = _uiState.value.copy(pumpStates = newPumps, isLoading = false)
            }

            override fun onError(message: String) {
                // Fallback to local HTTP
                apiClient.setPumpState(id, on, object : ArduinoApiClient.SimpleCallback {
                    override fun onSuccess(response: String) {
                        val newPumps = _uiState.value.pumpStates.toMutableList()
                        if (pumpIdx in 0 until newPumps.size) newPumps[pumpIdx] = on
                        _uiState.value = _uiState.value.copy(pumpStates = newPumps, isLoading = false)
                    }

                    override fun onError(msg: String) {
                        _uiState.value = _uiState.value.copy(isLoading = false, message = msg)
                        clearMessageAfterDelay()
                    }
                })
            }
        })
    }

    fun setValveState(id: Int, on: Boolean) {
        if (!_uiState.value.isManual) return // Ignore in Auto mode

        _uiState.value = _uiState.value.copy(isLoading = true)
        val valveIdx = id - 1

        liveRepository.setValveState(valveIdx, on, object : FirebaseLiveRepository.CommandCallback {
            override fun onSuccess() {
                val newValves = _uiState.value.valveStates.toMutableList()
                if (valveIdx in 0 until newValves.size) newValves[valveIdx] = on
                _uiState.value = _uiState.value.copy(valveStates = newValves, isLoading = false)
            }

            override fun onError(message: String) {
                apiClient.setValveState(id, on, object : ArduinoApiClient.SimpleCallback {
                    override fun onSuccess(response: String) {
                        val newValves = _uiState.value.valveStates.toMutableList()
                        if (valveIdx in 0 until newValves.size) newValves[valveIdx] = on
                        _uiState.value = _uiState.value.copy(valveStates = newValves, isLoading = false)
                    }

                    override fun onError(msg: String) {
                        _uiState.value = _uiState.value.copy(isLoading = false, message = msg)
                        clearMessageAfterDelay()
                    }
                })
            }
        })
    }

    fun setMistState(id: Int, on: Boolean) {
        if (!_uiState.value.isManual) return // Ignore in Auto mode

        _uiState.value = _uiState.value.copy(isLoading = true)
        val mistIdx = id - 1

        liveRepository.setMistState(mistIdx, on, object : FirebaseLiveRepository.CommandCallback {
            override fun onSuccess() {
                val newMists = _uiState.value.mistStates.toMutableList()
                if (mistIdx in 0 until newMists.size) newMists[mistIdx] = on
                _uiState.value = _uiState.value.copy(mistStates = newMists, isLoading = false)
            }

            override fun onError(message: String) {
                apiClient.setMistState(id, on, object : ArduinoApiClient.SimpleCallback {
                    override fun onSuccess(response: String) {
                        val newMists = _uiState.value.mistStates.toMutableList()
                        if (mistIdx in 0 until newMists.size) newMists[mistIdx] = on
                        _uiState.value = _uiState.value.copy(mistStates = newMists, isLoading = false)
                    }

                    override fun onError(msg: String) {
                        _uiState.value = _uiState.value.copy(isLoading = false, message = msg)
                        clearMessageAfterDelay()
                    }
                })
            }
        })
    }

    private fun clearMessageAfterDelay() {
        viewModelScope.launch {
            delay(3000)
            _uiState.value = _uiState.value.copy(message = null)
        }
    }

    override fun onCleared() {
        super.onCleared()
        liveRepository.stopListeningLiveData()
    }
}
