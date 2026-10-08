package com.anthurium.soilcontroller.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.anthurium.soilcontroller.data.AppPrefs
import com.anthurium.soilcontroller.data.FirebaseLiveRepository
import com.anthurium.soilcontroller.model.SensorReading
import com.anthurium.soilcontroller.net.ArduinoApiClient
import kotlinx.coroutines.*

class SensorMonitoringService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var notificationHelper: NotificationHelper
    private lateinit var prefs: AppPrefs
    private lateinit var apiClient: ArduinoApiClient
    private lateinit var liveRepository: FirebaseLiveRepository

    // Cool-down tracking map: plantIndex/key -> lastNotifiedTimestampMs
    private val lastNotificationMap = HashMap<String, Long>()
    private val cooldownPeriodMs = 5 * 60 * 1000L // 5 minutes cool-down between alerts

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        prefs = AppPrefs(this)
        apiClient = ArduinoApiClient(prefs.getDeviceBaseUrl())
        liveRepository = FirebaseLiveRepository()

        startForeground(
            NotificationHelper.SERVICE_NOTIFICATION_ID,
            notificationHelper.getForegroundNotification()
        )

        startMonitoringLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMonitoringLoop() {
        // 1. Listen to real-time Firebase Cloud sensor readings
        liveRepository.startListeningLiveData(object : FirebaseLiveRepository.LiveDataListener {
            override fun onDataReceived(reading: SensorReading) {
                checkSensorThresholds(reading)
            }

            override fun onError(message: String) {
                // Ignore in background loop
            }
        })

        // 2. Also poll local HTTP in background loop as backup
        serviceScope.launch {
            while (isActive) {
                try {
                    apiClient = ArduinoApiClient(prefs.getDeviceBaseUrl())
                    apiClient.fetchSensorData(object : ArduinoApiClient.SensorCallback {
                        override fun onSuccess(reading: SensorReading) {
                            checkSensorThresholds(reading)
                        }

                        override fun onError(message: String) {
                            // Silent on local error in background loop
                        }
                    })
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                delay(10_000) // Poll local every 10 seconds
            }
        }
    }

    private fun checkSensorThresholds(reading: SensorReading) {
        val now = System.currentTimeMillis()

        // 1. Check Soil Moisture Levels (< 25%)
        val soilLevels = reading.soilMoistureLevels
        for (i in soilLevels.indices) {
            val level = soilLevels[i]
            if (level in 0.1f..24.9f) {
                val key = "soil_plant_$i"
                val lastTime = lastNotificationMap[key] ?: 0L
                if (now - lastTime > cooldownPeriodMs) {
                    lastNotificationMap[key] = now
                    notificationHelper.postLowMoistureAlert(i, level)
                }
            }
        }

        // 2. Check pH Level (< 5.5 or > 7.5)
        val ph = reading.phLevel
        if (ph > 0.5f && (ph < 5.5f || ph > 7.5f)) {
            val key = "ph_level"
            val lastTime = lastNotificationMap[key] ?: 0L
            if (now - lastTime > cooldownPeriodMs) {
                lastNotificationMap[key] = now
                notificationHelper.postPhAlert(ph)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        liveRepository.stopListeningLiveData()
        serviceJob.cancel()
    }
}
