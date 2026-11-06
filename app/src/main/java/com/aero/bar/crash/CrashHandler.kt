package com.aero.bar.crash

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.aero.bar.service.AeroBarOverlayService
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Mission-Critical Crash Recovery & Watchdog for Aero Bar.
 *
 * Provides:
 * 1. Global uncaught exception interception across all threads.
 * 2. Crash logging to internal storage (`/files/crash_logs/`) for post-incident diagnostics.
 * 3. Crash Loop Protection: If >= 3 crashes occur within a 25-second window, triggers Safe Mode
 *    (fallback to minimal dock) to prevent infinite restart loops on the vehicle screen.
 * 4. Zero-delay resurrection: Schedules [AeroBarOverlayService] restart via AlarmManager in 600ms.
 * 5. Clean process termination without system ANR dialog hang.
 */
class CrashHandler private constructor(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "AeroCrashHandler"
        private const val PREFS_NAME = "aero_crash_guard"
        private const val KEY_CRASH_TIMESTAMPS = "crash_timestamps"
        private const val KEY_SAFE_MODE = "is_safe_mode"
        private const val CRASH_WINDOW_MS = 25_000L // 25 seconds
        private const val MAX_CRASHES_IN_WINDOW = 3
        private const val RESTART_DELAY_MS = 600L

        const val EXTRA_RESTARTED_FROM_CRASH = "com.aero.bar.EXTRA_RESTARTED_FROM_CRASH"
        const val EXTRA_SAFE_MODE = "com.aero.bar.EXTRA_SAFE_MODE"

        @Volatile
        private var installed = false

        fun install(context: Context) {
            if (installed) return
            synchronized(this) {
                if (installed) return
                val current = Thread.getDefaultUncaughtExceptionHandler()
                Thread.setDefaultUncaughtExceptionHandler(CrashHandler(context.applicationContext, current))
                installed = true
                Log.i(TAG, "Aero CrashHandler & Watchdog successfully installed.")
            }
        }

        fun isSafeModeActive(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_SAFE_MODE, false)
        }

        fun clearSafeMode(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_SAFE_MODE, false).remove(KEY_CRASH_TIMESTAMPS).apply()
            Log.i(TAG, "Safe mode cleared.")
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            Log.e(TAG, "FATAL EXCEPTION in thread '${thread.name}'", throwable)

            // 1. Record crash diagnostic log to disk
            saveCrashLog(thread, throwable)

            // 2. Assess Crash-Loop
            val isSafeMode = evaluateCrashLoop()

            // 3. Schedule immediate service resurrection
            scheduleServiceRestart(isSafeMode)

        } catch (e: Exception) {
            Log.e(TAG, "Error in crash handler routine", e)
        } finally {
            // 4. Force kill process cleanly to allow immediate reboot
            killCurrentProcess()
        }
    }

    private fun saveCrashLog(thread: Thread, throwable: Throwable) {
        try {
            val logsDir = File(context.filesDir, "crash_logs")
            if (!logsDir.exists()) logsDir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val logFile = File(logsDir, "crash_$timestamp.txt")

            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))

            val content = buildString {
                appendLine("=== AERO BAR CRASH REPORT ===")
                appendLine("Date: ${Date()}")
                appendLine("Thread: ${thread.name} (id=${thread.id})")
                appendLine("App Package: ${context.packageName}")
                appendLine("Android SDK: ${Build.VERSION.SDK_INT}")
                appendLine("Device Model: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
                appendLine("------------------------------")
                appendLine("StackTrace:")
                appendLine(sw.toString())
            }

            logFile.writeText(content)
            Log.i(TAG, "Crash log written to: ${logFile.absolutePath}")

            // Keep only latest 5 logs to save disk space
            logsDir.listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?.drop(5)
                ?.forEach { it.delete() }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash log", e)
        }
    }

    private fun evaluateCrashLoop(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        val rawTimestamps = prefs.getString(KEY_CRASH_TIMESTAMPS, "") ?: ""
        val history = rawTimestamps.split(",")
            .mapNotNull { it.toLongOrNull() }
            .filter { now - it < CRASH_WINDOW_MS }
            .toMutableList()

        history.add(now)
        val isCrashLoop = history.size >= MAX_CRASHES_IN_WINDOW

        val editor = prefs.edit()
        editor.putString(KEY_CRASH_TIMESTAMPS, history.joinToString(","))
        if (isCrashLoop) {
            editor.putBoolean(KEY_SAFE_MODE, true)
            Log.w(TAG, "CRASH LOOP DETECTED (${history.size} crashes in ${CRASH_WINDOW_MS}ms). Entering Safe Mode!")
        }
        editor.apply()

        return isCrashLoop || prefs.getBoolean(KEY_SAFE_MODE, false)
    }

    private fun scheduleServiceRestart(isSafeMode: Boolean) {
        try {
            val intent = Intent(context, AeroBarOverlayService::class.java).apply {
                putExtra(EXTRA_RESTARTED_FROM_CRASH, true)
                putExtra(EXTRA_SAFE_MODE, isSafeMode)
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(context, 7771, intent, flags)
            } else {
                PendingIntent.getService(context, 7771, intent, flags)
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val triggerTime = SystemClock.elapsedRealtime() + RESTART_DELAY_MS

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager?.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager?.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }

            Log.i(TAG, "Resurrection alarm scheduled for AeroBarOverlayService in ${RESTART_DELAY_MS}ms (safeMode=$isSafeMode)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule service resurrection alarm", e)
        }
    }

    private fun killCurrentProcess() {
        android.os.Process.killProcess(android.os.Process.myPid())
        exitProcess(10)
    }
}
