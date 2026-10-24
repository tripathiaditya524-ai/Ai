package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity

object SmartNotificationManager {
    const val CHANNEL_URGENT = "aura_urgent_channel"
    const val CHANNEL_LOCATION = "aura_location_channel"
    const val CHANNEL_PATTERN = "aura_pattern_channel"

    fun initNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val urgentChannel = NotificationChannel(
                CHANNEL_URGENT,
                "Urgent Task Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority and urgent task deadline notifications"
                enableVibration(true)
            }

            val locationChannel = NotificationChannel(
                CHANNEL_LOCATION,
                "Location Context Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Smart alerts triggered by location changes (Airport, Office, Home)"
            }

            val patternChannel = NotificationChannel(
                CHANNEL_PATTERN,
                "Productivity Pattern Insights",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Smart suggestions based on historical completion habits"
            }

            notificationManager.createNotificationChannels(listOf(urgentChannel, locationChannel, patternChannel))
        }
    }

    fun showUrgentTaskAlert(context: Context, taskId: Long, title: String, reason: String) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_URGENT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 Urgent Task: $title")
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText("Aura AI Prioritization:\n$reason"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(taskId.toInt() + 1000, notification)
    }

    fun showLocationSmartAlert(context: Context, locationContext: String, message: String) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            2001,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_LOCATION)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📍 Location Alert: $locationContext")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(2001, notification)
    }

    fun showPatternInsightAlert(context: Context, insight: String) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            3001,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_PATTERN)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("⚡ Productivity Pattern Detected")
            .setContentText(insight)
            .setStyle(NotificationCompat.BigTextStyle().bigText(insight))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(3001, notification)
    }
}
