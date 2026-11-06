package com.aero.bar.domain.model

import android.graphics.drawable.Drawable

/**
 * Domain representation of an installed vehicle application.
 */
data class AppItem(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: Drawable? = null,
    val isPinned: Boolean = false,
    val pinnedSlot: Int? = null,
    val isHidden: Boolean = false,
    val launchCount: Int = 0,
    val lastLaunchedTimestamp: Long = 0L,
    val isSystemApp: Boolean = false
) {
    val canUninstall: Boolean get() = !isSystemApp
}
