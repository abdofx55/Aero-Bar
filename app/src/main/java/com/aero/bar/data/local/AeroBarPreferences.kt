package com.aero.bar.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

/**
 * Lightweight, instant, zero-overhead storage for Aero Bar.
 * Combines synchronous cold-boot reads from SharedPreferences with
 * reactive Kotlin StateFlow for Compose and ViewModels.
 */
@Singleton
class AeroBarPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _pinnedAppsFlow = MutableStateFlow(loadPinnedApps())
    val pinnedAppsFlow: StateFlow<List<String>> = _pinnedAppsFlow.asStateFlow()

    private val _hiddenAppsFlow = MutableStateFlow(loadHiddenApps())
    val hiddenAppsFlow: StateFlow<Set<String>> = _hiddenAppsFlow.asStateFlow()

    private fun loadPinnedApps(): List<String> {
        val raw = prefs.getString(KEY_PINNED_APPS, null) ?: return DEFAULT_PINNED_APPS
        return if (raw.isBlank()) emptyList() else raw.split(",")
    }

    private fun loadHiddenApps(): Set<String> {
        return prefs.getStringSet(KEY_HIDDEN_APPS, emptySet()) ?: emptySet()
    }

    fun pinApp(packageName: String, slot: Int = -1) {
        val current = _pinnedAppsFlow.value.toMutableList()
        current.remove(packageName)

        if (slot in 0..current.size) {
            current.add(slot, packageName)
        } else {
            if (current.size >= MAX_PINNED_APPS) {
                current.removeAt(current.lastIndex)
            }
            current.add(packageName)
        }

        persistPinnedApps(current)
    }

    fun unpinApp(packageName: String) {
        val current = _pinnedAppsFlow.value.toMutableList()
        if (current.remove(packageName)) {
            persistPinnedApps(current)
        }
    }

    fun hideApp(packageName: String) {
        val current = _hiddenAppsFlow.value.toMutableSet()
        if (current.add(packageName)) {
            persistHiddenApps(current)
        }
        // Also unpin if it was pinned
        unpinApp(packageName)
    }

    fun unhideApp(packageName: String) {
        val current = _hiddenAppsFlow.value.toMutableSet()
        if (current.remove(packageName)) {
            persistHiddenApps(current)
        }
    }

    fun unhideAllApps() {
        persistHiddenApps(emptySet())
    }

    private fun persistPinnedApps(list: List<String>) {
        _pinnedAppsFlow.value = list
        prefs.edit { putString(KEY_PINNED_APPS, list.joinToString(",")) }
    }

    private fun persistHiddenApps(set: Set<String>) {
        _hiddenAppsFlow.value = set
        prefs.edit { putStringSet(KEY_HIDDEN_APPS, set) }
    }

    companion object {
        private const val PREFS_NAME = "aero_bar_preferences"
        private const val KEY_PINNED_APPS = "pinned_apps"
        private const val KEY_HIDDEN_APPS = "hidden_apps"
        const val MAX_PINNED_APPS = 10

        // Empty by default: User pins apps from App Drawer
        private val DEFAULT_PINNED_APPS: List<String> = emptyList()
    }
}
