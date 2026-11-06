package com.aero.bar.domain.repository

import com.aero.bar.domain.model.RecentTaskItem
import kotlinx.coroutines.flow.Flow

interface RecentAppsRepository {
    fun getRecentTasks(): Flow<List<RecentTaskItem>>
    suspend fun dismissTask(persistentId: Int, packageName: String)
    suspend fun clearAllTasks()
    suspend fun switchTask(persistentId: Int, packageName: String)
    suspend fun quickSwitchLastApp(): Boolean
    fun getSystemMemoryTelemetryMb(): Pair<Int, Int> // Pair(usedMb, totalMb)
    fun updateCachedTasks(tasks: List<RecentTaskItem>)
}
