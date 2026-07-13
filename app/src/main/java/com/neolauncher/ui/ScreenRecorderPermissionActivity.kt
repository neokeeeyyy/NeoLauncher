package com.neolauncher.ui

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ScreenRecorderPermissionActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_MEDIA_PROJECTION = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val captureIntent = intent?.getParcelableExtra<Intent>("capture_intent")
        if (captureIntent != null) {
            startActivityForResult(captureIntent, REQUEST_MEDIA_PROJECTION)
        } else {
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val serviceIntent = Intent(this, ScreenRecorderService::class.java).apply {
                    putExtra("result_code", resultCode)
                    putExtra("result_data", data)
                }
                startService(serviceIntent)
            }
            finish()
        }
    }
}
