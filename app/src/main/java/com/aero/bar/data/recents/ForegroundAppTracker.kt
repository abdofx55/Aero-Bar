package com.aero.bar.data.recents

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Aero's own Most-Recently-Used (MRU) tracker — the single source of truth for Recents ordering.
 *
 * Why: the system sources (ActivityManager recents / UsageStats) update asynchronously and lag behind
 * the real switch, which made cards visibly jump when Recents was reopened right after switching apps.
 * This tracker is updated synchronously:
 *  - Optimistically, the instant Aero launches/switches to an app (Recents tap, Alt+Tab, drawer).
 *  - Live, from [com.aero.bar.service.AeroAccessibilityService] window-state events for every activity
 *    that comes to the foreground (launcher icons, notifications, other apps launching apps, ...).
 *
 * Timestamps are persisted, so the order survives process / device restarts.
 */
object ForegroundAppTracker {

    private const val TAG = "ForegroundAppTracker"
    private const val PREFS_NAME = "aero_mru_prefs"
    private const val MAX_ENTRIES = 60

    private val lastForeground = ConcurrentHashMap<String, Long>()

    @Volatile
    private var prefs: SharedPreferences? = null

    /**
     * Package of the activity currently in the foreground (including launchers), or null if unknown
     * (e.g. accessibility service not enabled yet).
     */
    @Volatile
    var currentForegroundPackage: String? = null
        private set

    /**
     * True while the accessibility service is connected and feeding live events. When false,
     * [currentForegroundPackage] may be stale and callers should use a system fallback.
     */
    @Volatile
    var isLive: Boolean = false

    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            for ((pkg, value) in p.all) {
                if (value is Long) lastForeground.putIfAbsent(pkg, value)
            }
            prefs = p
        }
    }

    /** Records [packageName] as the foreground app at [timeMs]. Never moves a timestamp backwards. */
    fun onForeground(packageName: String, timeMs: Long = System.currentTimeMillis()) {
        currentForegroundPackage = packageName
        val previous = lastForeground[packageName] ?: 0L
        if (timeMs <= previous) return
        lastForeground[packageName] = timeMs
        prefs?.edit()?.putLong(packageName, timeMs)?.apply()
        trimIfNeeded()
    }

    /** Last time [packageName] was seen in the foreground, or 0 if never. */
    fun lastForegroundTime(packageName: String): Long = lastForeground[packageName] ?: 0L

    fun trackedPackages(): Set<String> = lastForeground.keys.toSet()

    fun remove(packageNames: Collection<String>) {
        if (packageNames.isEmpty()) return
        val editor = prefs?.edit()
        for (pkg in packageNames) {
            lastForeground.remove(pkg)
            editor?.remove(pkg)
        }
        editor?.apply()
    }

    private fun trimIfNeeded() {
        if (lastForeground.size <= MAX_ENTRIES) return
        val stale = lastForeground.entries
            .sortedByDescending { it.value }
            .drop(MAX_ENTRIES)
            .map { it.key }
        remove(stale)
        Log.v(TAG, "Trimmed ${stale.size} stale MRU entries")
    }
}
