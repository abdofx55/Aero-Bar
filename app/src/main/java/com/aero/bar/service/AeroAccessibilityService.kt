package com.aero.bar.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.aero.bar.data.recents.ForegroundAppTracker

/**
 * Accessibility Service to perform global system actions for Aero Bar.
 * Specifically provides GLOBAL_ACTION_BACK, GLOBAL_ACTION_HOME, and GLOBAL_ACTION_RECENTS
 * without requiring root or instrumentation hacks.
 */
class AeroAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AeroAccessibilityService? = null
            private set

        fun performBack(): Boolean {
            val service = instance ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_BACK)
        }

        fun performHome(): Boolean {
            val service = instance ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_HOME)
        }

        fun performRecents(): Boolean {
            val service = instance ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_RECENTS)
        }
    }

    // className -> is it a real Activity? (cached: getActivityInfo is a binder call)
    private val activityClassCache = HashMap<String, Boolean>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        ForegroundAppTracker.init(this)
        ForegroundAppTracker.isLive = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return
        if (!isActivity(pkg, cls)) return
        ForegroundAppTracker.onForeground(pkg)
    }

    private fun isActivity(pkg: String, cls: String): Boolean {
        val key = "$pkg/$cls"
        activityClassCache[key]?.let { return it }
        val result = try {
            packageManager.getActivityInfo(ComponentName(pkg, cls), 0)
            true
        } catch (_: Exception) {
            false
        }
        if (activityClassCache.size > 500) activityClassCache.clear()
        activityClassCache[key] = result
        return result
    }

    override fun onInterrupt() {
        // No interrupt action needed
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
            ForegroundAppTracker.isLive = false
        }
    }
}
