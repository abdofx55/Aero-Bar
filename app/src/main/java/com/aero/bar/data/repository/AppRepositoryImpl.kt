package com.aero.bar.data.repository

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import com.aero.bar.data.local.AeroBarPreferences
import com.aero.bar.domain.model.AppItem
import com.aero.bar.domain.repository.AppRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AeroBarPreferences
) : AppRepository {

    private val packageManager: PackageManager = context.packageManager

    companion object {
        // The 8 Essential Car Apps on Dongfeng Shine (Qinggan/Pateo ROM)
        // Mapped to their default launch activity if CATEGORY_LAUNCHER is omitted by OEM
        val ESSENTIAL_CAR_APPS = linkedMapOf(
            "com.qinggan.bluetoothphone" to "com.qinggan.bluetoothphone.MainActivity",
            "pateo.dls.bluetooth" to "pateo.dls.bluetooth.MainActivity",
            "com.qinggan.app.radio" to "com.qinggan.app.radio.MainActivity",
            "com.qinggan.app.music" to "com.qinggan.app.music.MainActivity",
            "com.qinggan.app.video" to "com.qinggan.app.video.MainActivity",
            "com.qinggan.app.vehicle" to "com.qinggan.app.vehicle.MainActivity",
            "com.qinggan.app.setting" to "com.qinggan.app.setting.MainActivity",
            "com.qinggan.carlifeapp" to "com.qinggan.carlifeapp.MainActivity",
            "com.pateo.CarPlay.App" to "com.pateo.CarPlay.App.MainActivity",
            "pateo.dls.plinkui" to "pateo.dls.plinkui.MainActivity",
            "com.miren.dvr" to "com.miren.dvr.MainActivity",
            "pateo.Application.Camera" to "pateo.Application.Camera.MainActivity",
            "pateo.Application.CarRecord" to "pateo.Application.CarRecord.MainActivity"
        )

        // Internal OEM/System packages that should be hidden from All Apps drawer
        private val HIDDEN_OEM_PACKAGES = setOf(
            "com.android.launcher3",
            "com.android.systemui",
            "com.teslacoilsw.launcher",
            "altergames.carlauncher",
            "com.qinggan.app.launcher",
            "com.pateo.launcher",
            "com.android.traceur",
            "com.android.development",
            "com.android.customlocale2",
            "com.android.emu.testing"
        )
    }

    override fun getInstalledApps(): Flow<List<AppItem>> {
        return getSystemAppsFlow().combine(preferences.pinnedAppsFlow) { allApps, pinnedList ->
            val pinnedMap = pinnedList.mapIndexed { index, pkg -> pkg to index }.toMap()
            allApps.map { app ->
                val slot = pinnedMap[app.packageName]
                app.copy(
                    isPinned = slot != null,
                    pinnedSlot = slot
                )
            }
        }.combine(preferences.hiddenAppsFlow) { appsWithPin, hiddenSet ->
            appsWithPin.map { app ->
                app.copy(isHidden = app.packageName in hiddenSet)
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun getPinnedApps(): Flow<List<AppItem>> {
        return getInstalledApps().combine(preferences.pinnedAppsFlow) { allApps, pinnedList ->
            val appsMap = allApps.associateBy { it.packageName }
            pinnedList.mapNotNull { pkg -> appsMap[pkg] }
        }.flowOn(Dispatchers.IO)
    }

    override fun getHiddenApps(): Flow<List<AppItem>> {
        return getInstalledApps().combine(preferences.hiddenAppsFlow) { allApps, hiddenSet ->
            allApps.filter { it.packageName in hiddenSet }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun pinApp(packageName: String, slot: Int): Unit = withContext(Dispatchers.IO) {
        preferences.pinApp(packageName, slot)
    }

    override suspend fun unpinApp(packageName: String): Unit = withContext(Dispatchers.IO) {
        preferences.unpinApp(packageName)
    }

    override suspend fun hideApp(packageName: String): Unit = withContext(Dispatchers.IO) {
        preferences.hideApp(packageName)
    }

    override suspend fun unhideApp(packageName: String): Unit = withContext(Dispatchers.IO) {
        preferences.unhideApp(packageName)
    }

    override suspend fun unhideAllApps(): Unit = withContext(Dispatchers.IO) {
        preferences.unhideAllApps()
    }

    override suspend fun launchApp(packageName: String, activityName: String): Unit = withContext(Dispatchers.Main) {
        try {
            RecentAppsRepositoryImpl.unmarkDismissed(context, packageName)

            // 1. Specialized Android Settings Launch (bulletproof on all Android ROMs and car head units)
            if (packageName == "com.android.settings") {
                val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                return@withContext
            }

            // 2. Specialized Car Settings Launch (Dongfeng Shine Qinggan / Pateo OEM)
            if (packageName == "com.qinggan.app.setting") {
                val carSettingsIntent = packageManager.getLaunchIntentForPackage("com.qinggan.app.setting")
                    ?: Intent(Intent.ACTION_MAIN).apply {
                        component = ComponentName("com.qinggan.app.setting", "com.qinggan.app.setting.MainActivity")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    }
                try {
                    context.startActivity(carSettingsIntent)
                    return@withContext
                } catch (_: Exception) {
                    // Fallback to vehicle app if separate setting package is absent
                    try {
                        val vehicleIntent = packageManager.getLaunchIntentForPackage("com.qinggan.app.vehicle")
                            ?: Intent(Intent.ACTION_MAIN).apply {
                                component = ComponentName("com.qinggan.app.vehicle", "com.qinggan.app.vehicle.MainActivity")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                            }
                        context.startActivity(vehicleIntent)
                        return@withContext
                    } catch (_: Exception) {
                        android.widget.Toast.makeText(
                            context,
                            "Car Settings (com.qinggan.app.setting) will open on Dongfeng vehicle",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        return@withContext
                    }
                }
            }

            // 3. Standard application launch
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            if (launchIntent != null) {
                context.startActivity(launchIntent)
                return@withContext
            }

            if (activityName.isNotBlank()) {
                val explicitIntent = Intent(Intent.ACTION_MAIN).apply {
                    component = ComponentName(packageName, activityName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
                context.startActivity(explicitIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun openAppDetails(packageName: String): Unit = withContext(Dispatchers.Main) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun uninstallApp(packageName: String): Unit = withContext(Dispatchers.Main) {
        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getSystemAppsFlow(): Flow<List<AppItem>> = flow {
        val discoveredPackages = linkedMapOf<String, AppItem>()
        val isArabic = java.util.Locale.getDefault().language == "ar"

        // 1. Guaranteed shortcut: Android Settings (System App)
        val androidSettingsLabel = if (isArabic) "إعدادات أندرويد" else "Android Settings"
        val androidSettingsIcon = try { packageManager.getApplicationIcon("com.android.settings") } catch (_: Exception) { null }
        discoveredPackages["com.android.settings"] = AppItem(
            packageName = "com.android.settings",
            activityName = "com.android.settings.Settings",
            label = androidSettingsLabel,
            icon = androidSettingsIcon,
            isSystemApp = true
        )

        // 2. Guaranteed shortcut: Car Settings (System App)
        val carSettingsLabel = if (isArabic) "إعدادات السيارة" else "Car Settings"
        val carSettingsIcon = try { packageManager.getApplicationIcon("com.qinggan.app.setting") } catch (_: Exception) {
            try { packageManager.getApplicationIcon("com.qinggan.app.vehicle") } catch (_: Exception) { null }
        }
        discoveredPackages["com.qinggan.app.setting"] = AppItem(
            packageName = "com.qinggan.app.setting",
            activityName = "com.qinggan.app.setting.MainActivity",
            label = carSettingsLabel,
            icon = carSettingsIcon,
            isSystemApp = true
        )

        // 3. Discover installed OEM Essential Car Apps (All system apps)
        for ((pkg, defaultActivity) in ESSENTIAL_CAR_APPS) {
            if (pkg == "com.qinggan.app.setting") continue // Handled explicitly above
            try {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                val label = when (pkg) {
                    "com.qinggan.app.vehicle" -> if (isArabic) "المركبة" else "Vehicle Controls"
                    else -> packageManager.getApplicationLabel(appInfo).toString()
                }
                val icon = packageManager.getApplicationIcon(appInfo)
                discoveredPackages[pkg] = AppItem(
                    packageName = pkg,
                    activityName = defaultActivity,
                    label = label,
                    icon = icon,
                    isSystemApp = true
                )
            } catch (_: PackageManager.NameNotFoundException) {
                // Not installed on this ROM/emulator
            }
        }

        // 4. Query standard CATEGORY_LAUNCHER activities
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = packageManager.queryIntentActivities(mainIntent, 0)

        for (resolveInfo in resolveInfos) {
            val pkg = resolveInfo.activityInfo.packageName
            if (pkg == context.packageName || pkg in HIDDEN_OEM_PACKAGES) continue

            // Filter out internal non-essential Qinggan / Pateo OEM engineering apps (~94 internal services)
            val lower = pkg.lowercase()
            val isOemInternal = (lower.startsWith("com.qinggan.") ||
                    lower.startsWith("com.pateo.") ||
                    lower.startsWith("pateo.")) && pkg !in ESSENTIAL_CAR_APPS.keys
            if (isOemInternal) continue

            // Don't overwrite the dedicated Android Settings item
            if (pkg == "com.android.settings") continue

            if (!discoveredPackages.containsKey(pkg)) {
                val label = resolveInfo.loadLabel(packageManager).toString()
                val icon = resolveInfo.loadIcon(packageManager)
                val actName = resolveInfo.activityInfo.name
                val appInfo = resolveInfo.activityInfo.applicationInfo
                val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0

                discoveredPackages[pkg] = AppItem(
                    packageName = pkg,
                    activityName = actName,
                    label = label,
                    icon = icon,
                    isSystemApp = isSystem
                )
            }
        }

        // Sort with Essential Car Apps & Settings prioritized first, then alphabetical by label
        val prioritizedPkgs = setOf("com.qinggan.app.setting", "com.android.settings") + ESSENTIAL_CAR_APPS.keys
        val sortedList = discoveredPackages.values.sortedWith(
            compareByDescending<AppItem> { it.packageName in prioritizedPkgs }
                .thenBy { it.label.lowercase() }
        )

        emit(sortedList)
    }
}
