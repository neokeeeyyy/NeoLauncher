package com.neolauncher

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class NeoLauncherApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playerChannel = NotificationChannel(
                CHANNEL_PLAYER,
                "Reproductor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controles de reproducci\u00f3n"
                setShowBadge(false)
            }

            val recorderChannel = NotificationChannel(
                CHANNEL_RECORDER,
                "Grabaci\u00f3n de pantalla",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificaci\u00f3n de grabaci\u00f3n de pantalla"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(playerChannel)
            manager.createNotificationChannel(recorderChannel)
        }
    }

    companion object {
        const val CHANNEL_PLAYER = "player_channel"
        const val CHANNEL_RECORDER = "screen_recorder_channel"

        lateinit var instance: NeoLauncherApp
            private set
    }
}
