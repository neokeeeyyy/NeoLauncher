package com.neolauncher.ui

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.media.AudioManager
import android.os.Build
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
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private val streams = intArrayOf(
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_RING,
        AudioManager.STREAM_ALARM,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_SYSTEM
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                audioManager.adjustStreamVolume(currentStream, AudioManager.ADJUST_RAISE, 0)
                showOverlay()
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                audioManager.adjustStreamVolume(currentStream, AudioManager.ADJUST_LOWER, 0)
                showOverlay()
                return true
            }
        }
        return false
    }

    private fun showOverlay() {
        handler.removeCallbacks(dismissRunnable)

        if (overlayView == null) {
            overlayView = VolumeOverlayView(this).apply {
                setStreamChangedCallback { dir ->
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
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
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

    private fun resetDismiss() {
        handler.removeCallbacks(dismissRunnable)
        dismissRunnable = Runnable { dismissOverlay() }
        handler.postDelayed(dismissRunnable!!, 2000)
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
        handler.removeCallbacks(dismissRunnable)
        dismissOverlay()
    }
}
