package com.neolauncher.ui

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class VolumeControlService : AccessibilityService() {

    private lateinit var audioManager: AudioManager
    private lateinit var windowManager: WindowManager
    private var overlayView: VolumeOverlayView? = null
    private var currentStream = AudioManager.STREAM_MUSIC
    private var dismissRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastVolumeValues = mutableMapOf<Int, Int>()
    private var debounceTime = 0L

    private val volumeObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            val now = System.currentTimeMillis()
            if (now - debounceTime < 100) return
            debounceTime = now
            onVolumeChanged()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        saveCurrentVolumes()
        for (stream in getStreamList()) {
            contentResolver.registerContentObserver(
                Settings.System.getUriFor(getVolumeSetting(stream)),
                false,
                volumeObserver
            )
        }
    }

    private fun getVolumeSetting(stream: Int): String {
        return when (stream) {
            AudioManager.STREAM_MUSIC -> "volume_music"
            AudioManager.STREAM_RING -> "volume_ring"
            AudioManager.STREAM_ALARM -> "volume_alarm"
            AudioManager.STREAM_NOTIFICATION -> "volume_notification"
            AudioManager.STREAM_SYSTEM -> "volume_system"
            else -> "volume_music"
        }
    }

    private fun saveCurrentVolumes() {
        for (stream in getStreamList()) {
            lastVolumeValues[stream] = audioManager.getStreamVolume(stream)
        }
    }

    private fun onVolumeChanged() {
        for (stream in getStreamList()) {
            val cur = audioManager.getStreamVolume(stream)
            val prev = lastVolumeValues[stream] ?: cur
            if (cur != prev) {
                currentStream = stream
                lastVolumeValues[stream] = cur
                showOverlay()
                return
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                showOverlay()
            }
        }
        return super.onKeyEvent(event)
    }

    private fun showOverlay() {
        removeDismiss()

        if (overlayView == null) {
            overlayView = VolumeOverlayView(this).apply {
                setStreamChangedCallback { dir ->
                    val streams = getStreamList()
                    val idx = streams.indexOf(currentStream)
                    var newIdx = (idx + dir) % streams.size
                    if (newIdx < 0) newIdx += streams.size
                    currentStream = streams[newIdx]
                    updateOverlay()
                    resetDismiss()
                }
                setDismissCallback { dismissOverlay() }
            }

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_SYSTEM_ERROR
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.FILL }

            try {
                windowManager.addView(overlayView!!, params)
            } catch (_: Exception) {
                overlayView = null
                return
            }
        }

        updateOverlay()
        resetDismiss()
    }

    private fun getStreamList(): IntArray {
        return intArrayOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_SYSTEM
        )
    }

    private fun resetDismiss() {
        removeDismiss()
        val r = Runnable { dismissOverlay() }
        dismissRunnable = r
        handler.postDelayed(r, 2000)
    }

    private fun removeDismiss() {
        dismissRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun updateOverlay() {
        val v = overlayView ?: return
        val max = audioManager.getStreamMaxVolume(currentStream)
        val cur = audioManager.getStreamVolume(currentStream)
        val pct = if (max > 0) cur.toFloat() / max.toFloat() else 0f
        v.setVolume(pct)
        v.setStream(currentStream)
    }

    private fun dismissOverlay() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        try { contentResolver.unregisterContentObserver(volumeObserver) } catch (_: Exception) {}
        removeDismiss()
        dismissOverlay()
    }
}
