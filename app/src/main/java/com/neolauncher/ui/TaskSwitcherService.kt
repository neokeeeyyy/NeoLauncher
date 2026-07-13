package com.neolauncher.ui

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class TaskSwitcherService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_APP_SWITCH && event.action == KeyEvent.ACTION_UP) {
            val intent = Intent(this, LauncherActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            intent.action = ACTION_SHOW_TASKS
            startActivity(intent)
            return true
        }
        return false
    }

    companion object {
        const val ACTION_SHOW_TASKS = "com.neolauncher.SHOW_TASKS"
    }
}
