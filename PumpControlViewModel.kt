package com.anthurium.soilcontroller.ui.pump

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anthurium.soilcontroller.data.AppPrefs
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
    private val prefs: AppPrefs
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PumpControlUiState(isManual = "MANUAL".equals(prefs.getMode(), ignoreCase = true))
    )
    val uiState: StateFlow<PumpControlUiState> = _uiState.asStateFlow()

    init {
        refreshFromDevice()
    }

    /** Syncs mode and on/off states with what the Arduino is actually doing. */
    fun refreshFromDevice() {
        apiClient.fetchSensorData(object : ArduinoApiClient.SensorCallback {
            override fun onSuccess(reading: SensorReading) {
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
                // When local connection is pending/offline, keep switches usable in Manual mode
            }
        })
    }

    fun setMode(manual: Boolean) {
        val modeStr = if (manual) "MANUAL" else "AUTOMATIC"
        prefs.setMode(modeStr)
        // Immediately update UI state so switches unlock when Manual is selected
        _uiState.value = _uiState.value.copy(isManual = manual, isLoading = false, message = "Mode set to $modeStr")
        clearMessageAfterDelay()

        apiClient.setMode(modeStr.lowercase(), object : ArduinoApiClient.SimpleCallback {
            override fun onSuccess(response: String) {
                // Confirmed by Arduino
            }

            override fun onError(msg: String) {
                showMessage("Mode set on app (Arduino offline: $msg)")
            }
        })
    }

    fun setPumpState(id: Int, on: Boolean) {
        if (!_uiState.value.isManual) return
        val pumpIdx = id - 1
        _uiState.value = _uiState.value.copy(
            pumpStates = withState(_uiState.value.pumpStates, pumpIdx, on)
        )

        apiClient.setPumpState(id, on, object : ArduinoApiClient.SimpleCallback {
            override fun onSuccess(response: String) {
                if (response.contains("invalid", ignoreCase = true) || response.contains("not found", ignoreCase = true)) {
                    showMessage("Arduino rejected: $response")
                }
            }

            override fun onError(msg: String) {
                showMessage("Toggle sent (Arduino offline: $msg)")
            }
        })
    }

    fun setValveState(id: Int, on: Boolean) {
        if (!_uiState.value.isManual) return
        val valveIdx = id - 1
        _uiState.value = _uiState.value.copy(
            valveStates = withState(_uiState.value.valveStates, valveIdx, on)
        )

        apiClient.setValveState(id, on, object : ArduinoApiClient.SimpleCallback {
            override fun onSuccess(response: String) {
                if (response.contains("invalid", ignoreCase = true) || response.contains("not found", ignoreCase = true)) {
                    showMessage("Arduino rejected: $response")
                }
            }

            override fun onError(msg: String) {
                showMessage("Toggle sent (Arduino offline: $msg)")
            }
        })
    }

    fun setMistState(id: Int, on: Boolean) {
        if (!_uiState.value.isManual) return
        val mistIdx = id - 1
        _uiState.value = _uiState.value.copy(
            mistStates = withState(_uiState.value.mistStates, mistIdx, on)
        )

        apiClient.setMistState(id, on, object : ArduinoApiClient.SimpleCallback {
            override fun onSuccess(response: String) {
                if (response.contains("invalid", ignoreCase = true) || response.contains("not found", ignoreCase = true)) {
                    showMessage("Arduino rejected: $response")
                }
            }

            override fun onError(msg: String) {
                showMessage("Toggle sent (Arduino offline: $msg)")
            }
        })
    }

    private fun withState(list: List<Boolean>, index: Int, on: Boolean): List<Boolean> {
        val copy = list.toMutableList()
        if (index in copy.indices) copy[index] = on
        return copy
    }

    private fun showMessage(text: String) {
        _uiState.value = _uiState.value.copy(message = text)
        clearMessageAfterDelay()
    }

    private fun clearMessageAfterDelay() {
        viewModelScope.launch {
            delay(3000)
            _uiState.value = _uiState.value.copy(message = null)
        }
    }
}
