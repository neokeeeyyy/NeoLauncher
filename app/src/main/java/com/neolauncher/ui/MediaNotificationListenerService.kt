package com.neolauncher.ui

import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService

class MediaNotificationListenerService : NotificationListenerService() {

    companion object {
        private var instance: MediaNotificationListenerService? = null

        fun getActiveController(): MediaController? {
            return try {
                val msm = instance?.getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager
                val controllers = msm?.getActiveSessions(null)
                controllers?.firstOrNull()
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
