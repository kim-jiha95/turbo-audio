package com.turboaudio

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import android.os.Build
import androidx.annotation.RequiresApi

@Suppress("NewApi")
class AudioService : Service() {
    
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "turbo_audio_channel"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("notification", Notification::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("notification")
        }

        val finalNotification = notification ?: createDefaultNotification()
        startForeground(NOTIFICATION_ID, finalNotification)
        return START_STICKY
    }

    private fun createDefaultNotification(): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Audio Player")
            .setContentText("Playing...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()
    }

    override fun onDestroy() {
        stopForeground(true)
        super.onDestroy()
    }
}