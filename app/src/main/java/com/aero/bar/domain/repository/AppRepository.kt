package com.aero.bar.domain.repository

import com.aero.bar.domain.model.AppItem
import kotlinx.coroutines.flow.Flow

interface AppRepository {
    fun getInstalledApps(): Flow<List<AppItem>>
    fun getPinnedApps(): Flow<List<AppItem>>
    fun getHiddenApps(): Flow<List<AppItem>>
    suspend fun pinApp(packageName: String, slot: Int)
    suspend fun unpinApp(packageName: String)
    suspend fun hideApp(packageName: String)
    suspend fun unhideApp(packageName: String)
    suspend fun unhideAllApps()
    suspend fun launchApp(packageName: String, activityName: String)
    suspend fun openAppDetails(packageName: String)
    suspend fun uninstallApp(packageName: String)
}
