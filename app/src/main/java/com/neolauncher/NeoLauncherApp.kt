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
                description = "Controles de reproducción"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(playerChannel)
        }
    }

    companion object {
        const val CHANNEL_PLAYER = "player_channel"

        lateinit var instance: NeoLauncherApp
            private set
    }
}
