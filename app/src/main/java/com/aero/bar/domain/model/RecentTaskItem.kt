package com.aero.bar.domain.model

import android.graphics.drawable.Drawable

/**
 * Domain model representing a running / recent automotive task.
 * Designed for automotive cockpit cards: displays high-res app icon,
 * label, memory usage, and kill actions without heavy thumbnail caching.
 */
data class RecentTaskItem(
    val persistentId: Int,
    val packageName: String,
    val label: String,
    val icon: Drawable? = null,
    val memoryUsageMb: Int = 0
)
