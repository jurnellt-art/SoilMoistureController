package com.anthurium.soilcontroller.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.anthurium.soilcontroller.ui.dashboard.DashboardActivity
import com.anthurium.soilcontroller.ui.sensors.SensorDataActivity

class NotificationHelper(private val context: Context) {

    companion object {
        const val SERVICE_CHANNEL_ID = "soil_monitoring_service_channel"
        const val ALERT_CHANNEL_ID = "soil_alert_channel"
        const val SERVICE_NOTIFICATION_ID = 1001
        const val ALERT_NOTIFICATION_ID = 2001
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Service Channel (Low importance for persistent foreground service)
            val serviceChannel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                "Soil Monitoring Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active background monitoring status for soil moisture and sensors"
            }

            // Alert Channel (High importance for urgent low moisture alerts)
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Soil & Sensor Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Sends immediate push alerts when soil moisture or sensor readings are low"
                enableVibration(true)
                enableLights(true)
            }

            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    fun getForegroundNotification(): Notification {
        val intent = Intent(context, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setContentTitle("Soil Monitoring Active")
            .setContentText("Monitoring plant sensors in background")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun postLowMoistureAlert(plantIndex: Int, level: Float) {
        val intent = Intent(context, SensorDataActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            plantIndex,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "⚠️ Low Soil Moisture Alert!"
        val message = "Plant ${plantIndex + 1} soil moisture is at ${level.toInt()}% (below threshold 25%)!"

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(ALERT_NOTIFICATION_ID + plantIndex, notification)
    }

    fun postPhAlert(phLevel: Float) {
        val intent = Intent(context, SensorDataActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            99,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "⚠️ Critical pH Level Alert!"
        val message = "Current pH level is ${String.format(java.util.Locale.US, "%.1f", phLevel)} (Optimal Anthurium pH: 5.5 - 6.5)!"

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(ALERT_NOTIFICATION_ID + 10, notification)
    }
}
