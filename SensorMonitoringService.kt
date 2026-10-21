package com.anthurium.soilcontroller.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.anthurium.soilcontroller.data.AppPrefs
import com.anthurium.soilcontroller.data.ThingSpeakRepository
import com.anthurium.soilcontroller.model.SensorReading
import com.anthurium.soilcontroller.net.ArduinoApiClient
import kotlinx.coroutines.*

class SensorMonitoringService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var notificationHelper: NotificationHelper
    private lateinit var prefs: AppPrefs
    private var apiClient: ArduinoApiClient? = null
    private var apiBaseUrl: String? = null
    private lateinit var thingSpeakRepository: ThingSpeakRepository

    // Cool-down tracking map: plantIndex/key -> lastNotifiedTimestampMs
    private val lastNotificationMap = HashMap<String, Long>()
    private val cooldownPeriodMs = 5 * 60 * 1000L // 5 minutes cool-down between alerts

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        prefs = AppPrefs(this)
        thingSpeakRepository = ThingSpeakRepository()

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
        serviceScope.launch {
            while (isActive) {
                try {
                    // 1. Try local HTTP direct from Arduino
                    // Reuse one client instance to avoid leaking executor threads
                    val url = prefs.getDeviceBaseUrl()
                    if (apiClient == null || url != apiBaseUrl) {
                        apiClient = ArduinoApiClient(url)
                        apiBaseUrl = url
                    }
                    apiClient?.fetchSensorData(object : ArduinoApiClient.SensorCallback {
                        override fun onSuccess(reading: SensorReading) {
                            checkSensorThresholds(reading)
                        }

                        override fun onError(message: String) {
                            // 2. If local fails, check ThingSpeak Cloud feed
                            thingSpeakRepository.fetchLatestFeed(object : ThingSpeakRepository.LatestFeedCallback {
                                override fun onSuccess(reading: SensorReading) {
                                    checkSensorThresholds(reading)
                                }

                                override fun onError(msg: String) {
                                    // Silent on error in background loop
                                }
                            })
                        }
                    })
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                delay(15_000) // Poll every 15 seconds
            }
        }
    }

    private fun checkSensorThresholds(reading: SensorReading) {
        val now = System.currentTimeMillis()

        // 1. Check Soil Moisture Levels (< 25%)
        val soilLevels = reading.soilMoistureLevels
        for (i in soilLevels.indices) {
            val level = soilLevels[i]
            if (level in 0f..24.9f) { // 0% is a real reading for bone-dry soil
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
        serviceJob.cancel()
    }
}
