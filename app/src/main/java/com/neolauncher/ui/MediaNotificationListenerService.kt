package com.neolauncher.ui

import android.media.session.MediaController
import android.media.session.MediaSession
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MediaNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras ?: return
        activePackage = sbn.packageName
        val token = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(android.app.Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(android.app.Notification.EXTRA_MEDIA_SESSION)
        }
        if (token != null) {
            activeController = MediaController(this, token)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val hadToken = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras?.getParcelable(android.app.Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras?.getParcelable(android.app.Notification.EXTRA_MEDIA_SESSION)
        }
        if (hadToken != null && activeController?.packageName == sbn.packageName) {
            activeController = null
            activePackage = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        activeController = null
        activePackage = null
    }

    companion object {
        private var instance: MediaNotificationListenerService? = null

        @Volatile
        var activeController: MediaController? = null

        @Volatile
        var activePackage: String? = null

        fun getFromInstance(): MediaController? {
            val inst = instance ?: return null
            return try {
                val msm = inst.getSystemService(MEDIA_SESSION_SERVICE) as? android.media.session.MediaSessionManager
                val controllers = msm?.getActiveSessions(null)
                controllers?.firstOrNull()
            } catch (_: Exception) {
                null
            }
        }
    }
}
