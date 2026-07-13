package com.neolauncher.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.projection.MediaProjectionManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class ScreenRecorderTileService : TileService() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("screen_recorder", Context.MODE_PRIVATE)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (isRecording()) {
            stopService(Intent(this, ScreenRecorderService::class.java))
            setRecordingState(false)
        } else {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val intent = mpm.createScreenCaptureIntent()
            startActivityAndCollapse(
                Intent(this, ScreenRecorderPermissionActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("capture_intent", intent)
                }
            )
        }
    }

    private fun isRecording(): Boolean {
        return prefs.getBoolean("recording", false)
    }

    fun setRecordingState(recording: Boolean) {
        prefs.edit().putBoolean("recording", recording).apply()
        updateTile()
    }

    private fun updateTile() {
        if (qsTile == null) return
        if (isRecording()) {
            qsTile.label = "Detener"
            qsTile.state = Tile.STATE_ACTIVE
        } else {
            qsTile.label = "Grabar"
            qsTile.state = Tile.STATE_INACTIVE
        }
        qsTile.updateTile()
    }

    companion object {
        const val PREFS_RECORDING = "recording"
    }
}
