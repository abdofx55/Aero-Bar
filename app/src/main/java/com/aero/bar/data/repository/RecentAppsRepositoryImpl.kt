package com.aero.bar.data.repository

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.aero.bar.data.recents.ForegroundAppTracker
import com.aero.bar.domain.model.RecentTaskItem
import com.aero.bar.domain.repository.RecentAppsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentAppsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : RecentAppsRepository {

    private val packageManager: PackageManager = context.packageManager
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    companion object {
        const val TAG = "RecentAppsRepo"
        private const val PREFS_NAME = "aero_recents_prefs"
        private const val MAX_RECENTS = 15

        // Maps taskId -> packageName
        val taskToPackageMap = mutableMapOf<Int, String>()
        // Maps packageName -> set of real system taskIds
        val packageToTaskIdsMap = mutableMapOf<String, MutableSet<Int>>()

        // Cache dynamic TRANSACTION_removeTask code
        @Volatile
        private var cachedRemoveTaskTxCode: Int = -1

        // 8 Essential Car Apps allowed in Recents & All Apps alongside user apps
        val ESSENTIAL_CAR_PACKAGES = setOf(
            "com.qinggan.bluetoothphone",
            "pateo.dls.bluetooth",
            "com.qinggan.app.radio",
            "com.qinggan.app.music",
            "com.qinggan.app.video",
            "com.qinggan.app.vehicle",
            "com.qinggan.app.setting",
            "com.qinggan.carlifeapp",
            "com.pateo.CarPlay.App",
            "pateo.dls.plinkui",
            "com.miren.dvr",
            "pateo.Application.Camera",
            "pateo.Application.CarRecord"
        )

        // Bluetooth packages protected from forceStopPackage so audio/HFP never drops
        val BLUETOOTH_PROTECTED_PACKAGES = setOf(
            "com.qinggan.bluetoothphone",
            "pateo.dls.bluetooth",
            "com.android.bluetooth"
        )

        // Core launchers & system UI that should never appear in Recent Apps
        private val EXCLUDED_SYSTEM_PACKAGES = setOf(
            "com.android.launcher3",
            "com.android.systemui",
            "com.teslacoilsw.launcher",
            "altergames.carlauncher",
            "com.qinggan.app.launcher",
            "com.pateo.launcher",
            "com.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.android.defcontainer",
            "com.android.keychain"
        )

        fun unmarkDismissed(context: Context, packageName: String) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().remove(packageName).apply()
                Log.d(TAG, "Unmarked dismissed package: $packageName")
            } catch (e: Exception) {
                Log.w(TAG, "Error unmarking dismissed package: ${e.message}")
            }
        }
    }

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    // UsageStats from the last full fetch; lets the instant phase apply the exact same ordering rule
    // without a binder call.
    @Volatile
    private var lastUsageSnapshot: Map<String, Long> = emptyMap()

    init {
        ForegroundAppTracker.init(context)
    }

    private fun getDismissedMap(): MutableMap<String, Long> {
        val map = mutableMapOf<String, Long>()
        val all = prefs.all
        for ((k, v) in all) {
            if (v is Long) {
                map[k] = v
            }
        }
        return map
    }

    private fun markDismissed(packageName: String) {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(packageName, now).apply()
        Log.d(TAG, "Marked dismissed: $packageName at $now")
    }

    private fun unmarkDismissedInternal(packageName: String) {
        prefs.edit().remove(packageName).apply()
        Log.d(TAG, "Unmarked dismissed: $packageName")
    }

    override fun getSystemMemoryTelemetryMb(): Pair<Int, Int> {
        return try {
            val memInfo = ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memInfo)
            val totalMb = (memInfo.totalMem / (1024L * 1024L)).toInt().coerceAtLeast(1024)
            val availMb = (memInfo.availMem / (1024L * 1024L)).toInt().coerceIn(0, totalMb)
            val usedMb = (totalMb - availMb).coerceIn(128, totalMb)
            usedMb to totalMb
        } catch (e: Exception) {
            1840 to 4096
        }
    }

    @Volatile
    private var cachedRecentTasks: List<RecentTaskItem> = emptyList()

    // Bumped on every explicit cache mutation (task click, dismiss, clear all). A fetch that started
    // before such a mutation holds a stale snapshot and must not overwrite the cache with it.
    private val cacheGeneration = java.util.concurrent.atomic.AtomicLong(0)

    override fun updateCachedTasks(tasks: List<RecentTaskItem>) {
        cacheGeneration.incrementAndGet()
        cachedRecentTasks = tasks
    }

    override fun getRecentTasks(): Flow<List<RecentTaskItem>> = flow {
        val startGeneration = cacheGeneration.get()

        // ── Phase 1: instant open (no slow system queries) ─────────────────────────────────────
        // Re-order the last known list with the live MRU tracker and add any app that came to the
        // foreground since the last fetch. Because Phase 2 uses the exact same ordering rule, the
        // full result lands on the same positions => no visible jumps.
        val dismissedAtOpen = getDismissedMap()
        val cachedByPkg = cachedRecentTasks.associateBy { it.packageName }
        val quickCandidates = LinkedHashSet<String>().apply {
            addAll(cachedByPkg.keys)
            addAll(ForegroundAppTracker.trackedPackages())
        }
        val quickOrder = orderPackages(quickCandidates, lastUsageSnapshot, dismissedAtOpen, MAX_RECENTS)
        val quickList = quickOrder.mapNotNull { pkg -> cachedByPkg[pkg] ?: buildTaskItem(pkg, memoryMb = null) }
        if (quickList.isNotEmpty()) {
            emit(quickList)
        }

        // ── Phase 2: full refresh (task IDs, real memory, newly discovered apps) ────────────────
        Log.d(TAG, "Fetching recent tasks...")

        // 1. Query UsageStatsManager first (fast in-process binder call)
        val usageStats = getUsageStatsMap()
        lastUsageSnapshot = usageStats

        // 2. Query ActivityManager for real TaskRecords (skip slow dumpsys shell if UsageStats already has apps)
        val amTasksMap = getTasksFromActivityManager(allowSlowShellDumpsys = usageStats.isEmpty())
        packageToTaskIdsMap.clear()
        for ((pkg, ids) in amTasksMap) {
            packageToTaskIdsMap.getOrPut(pkg) { mutableSetOf() }.addAll(ids)
        }

        // 3. Build process PID map for real PSS memory calculation
        val processMemoryMap = getRunningProcessMemoryMbMap()

        // 4. Candidates: MRU tracker + UsageStats + tasks with real IDs + task-less running processes.
        //    The insertion order is only the tie-breaker for apps with no known timestamp.
        val candidates = LinkedHashSet<String>().apply {
            addAll(ForegroundAppTracker.trackedPackages())
            addAll(usageStats.keys)
            for ((pkg, ids) in packageToTaskIdsMap) if (ids.isNotEmpty()) add(pkg)
            addAll(packageToTaskIdsMap.keys)
        }
        val ordered = orderPackages(candidates, usageStats, getDismissedMap(), MAX_RECENTS)

        taskToPackageMap.clear()
        val recentList = ordered.mapNotNull { pkg ->
            buildTaskItem(pkg, memoryMb = processMemoryMap[pkg] ?: estimatePackageMemoryMb(pkg))
        }

        // A task click / dismiss happened while we were querying the system: this snapshot is stale.
        // Don't overwrite the newer cache and don't emit an order that would visibly undo the change.
        if (cacheGeneration.get() != startGeneration) {
            Log.d(TAG, "Discarding stale recents snapshot")
            return@flow
        }

        cachedRecentTasks = recentList
        emit(recentList)
    }.flowOn(Dispatchers.IO)

    /**
     * Recency of a package = the most recent of:
     *  - Aero's MRU tracker (updated instantly on switch + live via accessibility)
     *  - UsageStats lastTimeUsed (catches anything the tracker missed, e.g. before it was enabled)
     */
    private fun recencyOf(pkg: String, usageStats: Map<String, Long>): Long =
        maxOf(ForegroundAppTracker.lastForegroundTime(pkg), usageStats[pkg] ?: 0L)

    /**
     * Single ordering rule shared by the instant list, the full refresh and Alt+Tab:
     * filter out non-recents packages, then sort by [recencyOf] (stable, so apps without any
     * timestamp keep the candidate order and land at the end).
     */
    private fun orderPackages(
        candidates: Collection<String>,
        usageStats: Map<String, Long>,
        dismissedMap: Map<String, Long>,
        limit: Int
    ): List<String> {
        return candidates
            .filter { it != context.packageName && it !in EXCLUDED_SYSTEM_PACKAGES }
            .map { it to recencyOf(it, usageStats) }
            .sortedByDescending { it.second }
            .asSequence()
            .filter { (pkg, recency) -> !isHiddenByDismiss(pkg, recency, dismissedMap) }
            .filter { (pkg, _) -> isEligibleForRecents(pkg) }
            .take(limit)
            .map { it.first }
            .toList()
    }

    /** A dismissed app stays hidden until it is brought to the foreground again after the dismiss. */
    private fun isHiddenByDismiss(pkg: String, recency: Long, dismissedMap: Map<String, Long>): Boolean {
        val dismissedTime = dismissedMap[pkg] ?: return false
        if (recency > dismissedTime + 2000L) {
            unmarkDismissedInternal(pkg)
            return false
        }
        return true
    }

    private fun buildTaskItem(pkgName: String, memoryMb: Int?): RecentTaskItem? {
        val label = getFriendlyAppLabel(pkgName)
        val icon = try {
            packageManager.getApplicationIcon(pkgName)
        } catch (e: Exception) {
            return null // uninstalled
        }

        // Fallback ID must be stable across fetches and must never fall in the real taskId range,
        // otherwise dismiss could call removeTask on another app's task.
        val taskId = packageToTaskIdsMap[pkgName]?.firstOrNull() ?: stableFallbackId(pkgName)
        taskToPackageMap[taskId] = pkgName

        return RecentTaskItem(
            persistentId = taskId,
            packageName = pkgName,
            label = label,
            icon = icon,
            memoryUsageMb = memoryMb ?: estimatePackageMemoryMb(pkgName)
        )
    }

    private fun stableFallbackId(pkgName: String): Int = -(1 + (pkgName.hashCode() and 0x7FFFFFFF) % 1_000_000)

    private fun isEligibleForRecents(pkgName: String): Boolean {
        if (pkgName in ESSENTIAL_CAR_PACKAGES) return true
        val launchIntent = packageManager.getLaunchIntentForPackage(pkgName)
        if (launchIntent == null) return false

        // Filter out internal Qinggan/Pateo hidden engineering & factory test tools
        val lower = pkgName.lowercase()
        if ((lower.startsWith("com.qinggan.") || lower.startsWith("com.pateo.") || lower.startsWith("pateo.")) &&
            pkgName !in ESSENTIAL_CAR_PACKAGES
        ) {
            return false
        }
        return true
    }

    private fun getFriendlyAppLabel(pkgName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(pkgName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            pkgName.substringAfterLast(".")
        }
    }

    private fun getRunningProcessMemoryMbMap(): Map<String, Int> {
        val map = mutableMapOf<String, Int>()

        // 1. Try reading real process PSS from dumpsys meminfo -s
        try {
            val lines = executeCommandAndReadLines("dumpsys meminfo -s")
            val pssRegex = Regex("""^\s*([\d,]+)K:\s+([a-zA-Z0-9._]+)""")
            for (line in lines) {
                val match = pssRegex.find(line)
                if (match != null) {
                    val kbStr = match.groupValues[1].replace(",", "")
                    val procName = match.groupValues[2]
                    val kb = kbStr.toIntOrNull() ?: 0
                    if (kb > 0 && procName.contains(".")) {
                        val mb = (kb / 1024).coerceAtLeast(12)
                        map[procName] = mb
                    }
                }
            }
        } catch (e: Exception) {
            Log.v(TAG, "dumpsys meminfo -s parsing error: ${e.message}")
        }

        // 2. If dumpsys didn't populate, fallback to ActivityManager.getProcessMemoryInfo
        if (map.isEmpty()) {
            try {
                val am = activityManager ?: return map
                val procs = am.runningAppProcesses ?: return map
                val pids = procs.map { it.pid }.toIntArray()
                if (pids.isNotEmpty()) {
                    val memInfos = am.getProcessMemoryInfo(pids)
                    for (i in procs.indices) {
                        val proc = procs[i]
                        val totalPssKb = memInfos.getOrNull(i)?.totalPss ?: 0
                        val mb = if (totalPssKb > 0) {
                            (totalPssKb / 1024).coerceAtLeast(14)
                        } else {
                            estimatePackageMemoryMb(proc.processName)
                        }
                        proc.pkgList?.forEach { pkg ->
                            map[pkg] = mb
                        }
                    }
                }
            } catch (e: Exception) {
                Log.v(TAG, "getRunningProcessMemoryMbMap fallback: ${e.message}")
            }
        }

        return map
    }

    private fun estimatePackageMemoryMb(pkgName: String): Int {
        val lower = pkgName.lowercase()
        return when {
            lower.contains("calc") || lower.contains("clock") -> 16
            lower.contains("message") || lower.contains("sms") -> 22
            lower.contains("contact") || lower.contains("dialer") || lower.contains("phone") -> 26
            lower.contains("setting") -> 32
            lower.contains("music") || lower.contains("audio") || lower.contains("radio") -> 48
            lower.contains("video") || lower.contains("camera") || lower.contains("gallery") -> 68
            lower.contains("map") || lower.contains("navi") -> 85
            lower.contains("browser") || lower.contains("chrome") -> 95
            else -> 28 + (kotlin.math.abs(pkgName.hashCode()) % 32)
        }
    }

    /**
     * Quick-Switch (Alt+Tab) on Recents double-tap, driven by the same MRU order as the panel:
     *  - If the foreground app is the most recent one → jump to the previous app.
     *  - If on Home / a non-recents screen → jump back to the most recent app.
     * Because every switch updates the MRU instantly, repeated double-taps toggle A ↔ B.
     */
    override suspend fun quickSwitchLastApp(): Boolean = withContext(Dispatchers.IO) {
        try {
            val usageStats = getUsageStatsMap()
            val candidates = LinkedHashSet<String>().apply {
                addAll(ForegroundAppTracker.trackedPackages())
                addAll(usageStats.keys)
                addAll(packageToTaskIdsMap.keys)
            }
            val ordered = orderPackages(candidates, usageStats, getDismissedMap(), limit = 2)
            if (ordered.isEmpty()) return@withContext false

            val current = currentForegroundPackage(usageStats)
            val targetPkg = if (current == ordered[0]) {
                ordered.getOrNull(1) ?: return@withContext false
            } else {
                ordered[0]
            }

            switchToPackage(targetPkg)
            Log.i(TAG, "Quick-switched (Alt+Tab) from $current to: $targetPkg")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error in quickSwitchLastApp", e)
            false
        }
    }

    /** Live value from accessibility when available, otherwise the most recent UsageStats entry. */
    private fun currentForegroundPackage(usageStats: Map<String, Long>): String? {
        if (ForegroundAppTracker.isLive) {
            ForegroundAppTracker.currentForegroundPackage?.let { return it }
        }
        return usageStats.keys.firstOrNull { it != context.packageName && it != "com.android.systemui" }
    }

    /**
     * Brings [pkgName] to the front. The MRU tracker is updated *before* the system even starts the
     * switch, so Recents is correct no matter how quickly it is reopened.
     */
    private suspend fun switchToPackage(pkgName: String, knownTaskId: Int = -1) {
        ForegroundAppTracker.onForeground(pkgName)
        unmarkDismissedInternal(pkgName)

        val taskId = knownTaskId.takeIf { it > 0 }
            ?: packageToTaskIdsMap[pkgName]?.firstOrNull { it > 0 }
            ?: -1

        withContext(Dispatchers.Main) {
            if (taskId > 0) {
                try {
                    activityManager?.moveTaskToFront(taskId, ActivityManager.MOVE_TASK_WITH_HOME)
                } catch (_: Exception) {}
            }
            launchPackageOrCarApp(pkgName)
        }
    }

    private fun launchPackageOrCarApp(pkgName: String) {
        unmarkDismissedInternal(pkgName)
        val launchIntent = packageManager.getLaunchIntentForPackage(pkgName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            context.startActivity(launchIntent)
            return
        }
        // Fallback explicit component for essential car apps without standard LAUNCHER category
        val explicitComponent = when (pkgName) {
            "com.qinggan.app.vehicle" -> ComponentName("com.qinggan.app.vehicle", "com.qinggan.app.vehicle.MainActivity")
            "com.qinggan.bluetoothphone" -> ComponentName("com.qinggan.bluetoothphone", "com.qinggan.bluetoothphone.MainActivity")
            "com.qinggan.app.radio" -> ComponentName("com.qinggan.app.radio", "com.qinggan.app.radio.MainActivity")
            "com.qinggan.app.music" -> ComponentName("com.qinggan.app.music", "com.qinggan.app.music.MainActivity")
            "com.qinggan.app.video" -> ComponentName("com.qinggan.app.video", "com.qinggan.app.video.MainActivity")
            "com.qinggan.app.setting" -> ComponentName("com.qinggan.app.setting", "com.qinggan.app.setting.MainActivity")
            else -> null
        }
        if (explicitComponent != null) {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                component = explicitComponent
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Query UsageStats sorted by lastTimeUsed in the last 30 days.
     */
    private fun getUsageStatsMap(): Map<String, Long> {
        val result = linkedMapOf<String, Long>()
        val usm = usageStatsManager ?: return result

        val endTime = System.currentTimeMillis()
        val startTime = endTime - (1000L * 60 * 60 * 24 * 30)

        try {
            val statsList = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, startTime, endTime)
            if (!statsList.isNullOrEmpty()) {
                val sorted = statsList
                    .filter { it.lastTimeUsed > 0 && it.packageName != null }
                    .sortedByDescending { it.lastTimeUsed }

                for (stat in sorted) {
                    val pkg = stat.packageName
                    if (pkg != null && !result.containsKey(pkg)) {
                        result[pkg] = stat.lastTimeUsed
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying UsageStats", e)
        }

        return result
    }

    /**
     * Query ActivityManager for real system TaskRecords and their persistent taskIds.
     */
    private fun getTasksFromActivityManager(allowSlowShellDumpsys: Boolean = false): Map<String, Set<Int>> {
        val resultMap = linkedMapOf<String, MutableSet<Int>>()

        // 1. Try public ActivityManager.getRecentTasks
        try {
            @Suppress("DEPRECATION")
            val recentTasks = activityManager?.getRecentTasks(50, ActivityManager.RECENT_IGNORE_UNAVAILABLE)
            if (recentTasks != null) {
                for (task in recentTasks) {
                    val pkg = extractPackageFromTask(task)
                    if (pkg != null && task.persistentId > 0) {
                        resultMap.getOrPut(pkg) { mutableSetOf() }.add(task.persistentId)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "am.getRecentTasks failed: ${e.message}")
        }

        // 2. Try IActivityManager.getRecentTasks via reflection
        try {
            val amClass = Class.forName("android.app.ActivityManager")
            val getServiceMethod = amClass.getDeclaredMethod("getService")
            getServiceMethod.isAccessible = true
            val iam = getServiceMethod.invoke(null)
            if (iam != null) {
                val getRecentTasksMethod = iam.javaClass.getMethod(
                    "getRecentTasks",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                val parceledListSlice = getRecentTasksMethod.invoke(iam, 50, 2, 0)
                if (parceledListSlice != null) {
                    val getListMethod = parceledListSlice.javaClass.getMethod("getList")
                    val list = getListMethod.invoke(parceledListSlice) as? List<*>
                    if (list != null) {
                        for (item in list) {
                            val info = item as? ActivityManager.RecentTaskInfo ?: continue
                            val pkg = extractPackageFromTask(info)
                            if (pkg != null && info.persistentId > 0) {
                                resultMap.getOrPut(pkg) { mutableSetOf() }.add(info.persistentId)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "IActivityManager.getRecentTasks reflection failed: ${e.message}")
        }

        // 3. Check running processes to ensure running apps are recognized
        try {
            val runningProcesses = activityManager?.runningAppProcesses
            if (runningProcesses != null) {
                for (proc in runningProcesses) {
                    val pkg = proc.pkgList?.firstOrNull() ?: proc.processName
                    if (pkg != null && !resultMap.containsKey(pkg)) {
                        resultMap.getOrPut(pkg) { mutableSetOf() }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "runningAppProcesses failed: ${e.message}")
        }

        // 4. Fallback: Query dumpsys activity recents via root/shell only if allowed & in-process queries returned nothing
        if (allowSlowShellDumpsys && resultMap.size <= 1) {
            try {
                val dumpsysMap = getTasksFromDumpsys()
                for ((pkg, ids) in dumpsysMap) {
                    resultMap.getOrPut(pkg) { mutableSetOf() }.addAll(ids)
                }
            } catch (e: Exception) {
                Log.w(TAG, "dumpsys activity recents failed: ${e.message}")
            }
        }

        return resultMap
    }

    private fun extractPackageFromTask(task: Any): String? {
        try {
            val realActField = task.javaClass.getField("realActivity")
            val realAct = realActField.get(task) as? ComponentName
            if (realAct != null && realAct.packageName.isNotEmpty()) {
                return realAct.packageName
            }
        } catch (_: Exception) {}

        try {
            val origActField = task.javaClass.getField("origActivity")
            val origAct = origActField.get(task) as? ComponentName
            if (origAct != null && origAct.packageName.isNotEmpty()) {
                return origAct.packageName
            }
        } catch (_: Exception) {}

        try {
            val baseIntentField = task.javaClass.getField("baseIntent")
            val baseIntent = baseIntentField.get(task) as? Intent
            val pkg = baseIntent?.component?.packageName ?: baseIntent?.`package`
            if (!pkg.isNullOrEmpty()) {
                return pkg
            }
        } catch (_: Exception) {}

        return null
    }

    private fun getTasksFromDumpsys(): Map<String, Set<Int>> {
        val map = linkedMapOf<String, MutableSet<Int>>()
        val lines = executeCommandAndReadLines("dumpsys activity recents")
        val regex = Regex("""TaskRecord\{[0-9a-fA-F]+\s+#(\d+)\s+.*?(?:[AI]=)([a-zA-Z0-9._/]+)""")
        for (line in lines) {
            val match = regex.find(line)
            if (match != null) {
                val taskId = match.groupValues[1].toIntOrNull()
                var pkg = match.groupValues[2]
                if (pkg.contains("/")) {
                    pkg = pkg.substringBefore("/")
                }
                if (taskId != null && taskId > 0 && pkg.isNotEmpty()) {
                    map.getOrPut(pkg) { mutableSetOf() }.add(taskId)
                }
            }
        }
        return map
    }

    private fun executeCommandAndReadLines(command: String): List<String> {
        val shellCandidates: List<Array<String>> = listOf(
            arrayOf("su", "0", "sh", "-c", command),
            arrayOf("su", "-c", command),
            arrayOf("/system/xbin/su", "0", "sh", "-c", command),
            arrayOf("/system/xbin/su", "-c", command),
            arrayOf("sh", "-c", command)
        )
        for (cmdArgs in shellCandidates) {
            try {
                val process = Runtime.getRuntime().exec(cmdArgs)
                val lines = process.inputStream.bufferedReader().readLines()
                if (lines.isNotEmpty()) {
                    return lines
                }
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    override suspend fun dismissTask(persistentId: Int, packageName: String): Unit = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Dismissing task: $packageName (id=$persistentId)")
            updateCachedTasks(cachedRecentTasks.filterNot { it.packageName == packageName })
            markDismissed(packageName)

            val taskIdsToRemove = mutableSetOf<Int>()
            if (persistentId in 1..999999) {
                taskIdsToRemove.add(persistentId)
            }
            packageToTaskIdsMap[packageName]?.let { taskIdsToRemove.addAll(it) }

            removeTasksAndKill(taskIdsToRemove, listOf(packageName))

            taskToPackageMap.remove(persistentId)
            packageToTaskIdsMap.remove(packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing task", e)
        }
    }

    override suspend fun clearAllTasks(): Unit = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Clearing all tasks...")
            updateCachedTasks(emptyList())
            val allPackages = (taskToPackageMap.values + packageToTaskIdsMap.keys)
                .distinct()
                .filter {
                    it != context.packageName && it !in EXCLUDED_SYSTEM_PACKAGES
                }

            for (pkg in allPackages) {
                markDismissed(pkg)
            }

            val allTaskIds = mutableSetOf<Int>()
            for (pkg in allPackages) {
                packageToTaskIdsMap[pkg]?.let { allTaskIds.addAll(it) }
            }
            for ((id, pkg) in taskToPackageMap) {
                if (pkg in allPackages && id in 1..999999) {
                    allTaskIds.add(id)
                }
            }

            removeTasksAndKill(allTaskIds, allPackages)

            taskToPackageMap.clear()
            packageToTaskIdsMap.clear()
            Log.d(TAG, "All user tasks cleared successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all tasks", e)
        }
    }

    /**
     * Multi-tier task removal with SMART BLUETOOTH PROTECTION:
     * - For Bluetooth packages (`com.qinggan.bluetoothphone`, `pateo.dls.bluetooth`, `com.android.bluetooth`),
     *   ONLY the UI task window (`removeTask`) is closed so background HFP/A2DP audio streaming never disconnects.
     * - For all other packages (Radio, Music, Video, Navigation, user apps), both `removeTask` and `forceStopPackage`
     *   are executed to reclaim RAM completely.
     */
    private fun removeTasksAndKill(taskIds: Set<Int>, packageNames: Collection<String>) {
        val forceStopTargets = packageNames.filter { it !in BLUETOOTH_PROTECTED_PACKAGES }
        Log.d(TAG, "removeTasksAndKill: taskIds=$taskIds, forceStopTargets=$forceStopTargets (BT protected=${packageNames - forceStopTargets.toSet()})")

        // -----------------------------------------------------------------
        // Tier 1: In-process Java API & Reflection
        // -----------------------------------------------------------------
        for (taskId in taskIds) {
            try {
                val am = activityManager
                if (am != null) {
                    val method = am.javaClass.getMethod("removeTask", Int::class.javaPrimitiveType)
                    method.isAccessible = true
                    method.invoke(am, taskId)
                }
            } catch (_: Exception) {}

            try {
                val amClass = Class.forName("android.app.ActivityManager")
                val getService = amClass.getDeclaredMethod("getService")
                getService.isAccessible = true
                val iam = getService.invoke(null)
                if (iam != null) {
                    val method = iam.javaClass.getMethod("removeTask", Int::class.javaPrimitiveType)
                    method.isAccessible = true
                    method.invoke(iam, taskId)
                }
            } catch (_: Exception) {}
        }

        for (pkg in forceStopTargets) {
            try {
                val am = activityManager
                if (am != null) {
                    val method = am.javaClass.getMethod("forceStopPackage", String::class.java)
                    method.isAccessible = true
                    method.invoke(am, pkg)
                }
            } catch (_: Exception) {}

            try {
                val amClass = Class.forName("android.app.ActivityManager")
                val getService = amClass.getDeclaredMethod("getService")
                getService.isAccessible = true
                val iam = getService.invoke(null)
                if (iam != null) {
                    val method = iam.javaClass.getMethod("forceStopPackage", String::class.java, Int::class.javaPrimitiveType)
                    method.isAccessible = true
                    method.invoke(iam, pkg, 0)
                }
            } catch (_: Exception) {}
        }

        // -----------------------------------------------------------------
        // Tier 2: Root Shell Execution (service call activity + am force-stop)
        // -----------------------------------------------------------------
        val txCode = getRemoveTaskTxCode()
        val rootCommands = mutableListOf<String>()

        for (taskId in taskIds) {
            rootCommands.add("service call activity $txCode i32 $taskId")
        }
        for (pkg in forceStopTargets) {
            rootCommands.add("am force-stop $pkg")
        }

        if (rootCommands.isNotEmpty()) {
            executeRootCommands(rootCommands)
        }

        // -----------------------------------------------------------------
        // Tier 3: Standard ActivityManager killBackgroundProcesses fallback
        // -----------------------------------------------------------------
        for (pkg in forceStopTargets) {
            try {
                activityManager?.killBackgroundProcesses(pkg)
            } catch (_: Exception) {}
        }
    }

    private fun getRemoveTaskTxCode(): Int {
        if (cachedRemoveTaskTxCode > 0) return cachedRemoveTaskTxCode
        return try {
            val stubClass = Class.forName("android.app.IActivityManager\$Stub")
            val txField = stubClass.getDeclaredField("TRANSACTION_removeTask")
            txField.isAccessible = true
            val code = txField.getInt(null)
            cachedRemoveTaskTxCode = code
            code
        } catch (e: Exception) {
            126
        }
    }

    private fun executeRootCommands(commands: List<String>): Boolean {
        if (commands.isEmpty()) return true
        val script = commands.joinToString(" ; ")

        try {
            val socket = android.net.LocalSocket()
            socket.connect(
                android.net.LocalSocketAddress(
                    "aero_root_daemon",
                    android.net.LocalSocketAddress.Namespace.ABSTRACT
                )
            )
            socket.soTimeout = 3000
            socket.outputStream.write("$script\n".toByteArray(Charsets.UTF_8))
            socket.outputStream.flush()
            val resp = socket.inputStream.bufferedReader().readLine()
            socket.close()
            if (resp != null && resp.startsWith("EXIT:0")) {
                return true
            }
        } catch (_: Exception) {}

        val execCandidates: List<Array<String>> = listOf(
            arrayOf("su", "-c", script),
            arrayOf("/system/xbin/su", "-c", script),
            arrayOf("su", "0", "sh", "-c", script),
            arrayOf("/system/xbin/su", "0", "sh", "-c", script),
            arrayOf("sh", "-c", script)
        )
        for (cmdArgs in execCandidates) {
            try {
                val process = Runtime.getRuntime().exec(cmdArgs)
                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    return true
                }
            } catch (_: Exception) {}
        }

        val shellPipes: List<Array<String>> = listOf(
            arrayOf("su"),
            arrayOf("/system/xbin/su"),
            arrayOf("su", "0"),
            arrayOf("/system/xbin/su", "0"),
            arrayOf("sh")
        )
        for (shellArgs in shellPipes) {
            try {
                val process = Runtime.getRuntime().exec(shellArgs)
                DataOutputStream(process.outputStream).use { os ->
                    for (cmd in commands) {
                        os.writeBytes("$cmd\n")
                    }
                    os.writeBytes("exit\n")
                    os.flush()
                }
                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    return true
                }
            } catch (_: Exception) {}
        }
        return false
    }

    override suspend fun switchTask(persistentId: Int, packageName: String) {
        try {
            switchToPackage(packageName, knownTaskId = persistentId)
        } catch (e: Exception) {
            Log.e(TAG, "Error switching task", e)
        }
    }
}
