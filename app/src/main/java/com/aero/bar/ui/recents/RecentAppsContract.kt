package com.aero.bar.ui.recents

import com.aero.bar.domain.model.RecentTaskItem

data class RecentAppsState(
    val recentTasks: List<RecentTaskItem> = emptyList(),
    val totalRamUsedMb: Int = 1840,
    val totalRamDeviceMb: Int = 4096,
    val isLoading: Boolean = false
)

sealed interface RecentAppsIntent {
    data object OnLoadRecentTasks : RecentAppsIntent
    data class OnTaskClicked(val task: RecentTaskItem) : RecentAppsIntent
    data class OnDismissTask(val task: RecentTaskItem) : RecentAppsIntent
    data object OnClearAllTasks : RecentAppsIntent
}
