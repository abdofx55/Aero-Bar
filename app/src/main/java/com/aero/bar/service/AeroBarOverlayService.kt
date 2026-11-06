package com.aero.bar.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.aero.bar.crash.CrashHandler
import com.aero.bar.domain.repository.AppRepository
import com.aero.bar.domain.repository.RecentAppsRepository
import com.aero.bar.domain.repository.VehicleHvacRepository
import com.aero.bar.ui.bar.AeroBarView
import com.aero.bar.ui.bar.AeroBarViewModel
import com.aero.bar.ui.drawer.AppDrawerView
import com.aero.bar.ui.drawer.AppDrawerViewModel
import com.aero.bar.ui.recents.RecentAppsView
import com.aero.bar.ui.recents.RecentAppsViewModel
import com.aero.bar.ui.theme.AeroBarTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Dual-Window Zero-Flicker Overlay Architecture:
 * 1. `barComposeView`: Fixed 960x88px bottom dock window that NEVER resizes or reallocates its surface buffer.
 * 2. `popupComposeView`: Dedicated overlay window for Recent Apps & All Apps above the bar that toggles
 *    `FLAG_NOT_TOUCHABLE` without ever changing width/height, guaranteeing 60 FPS animations with zero flicker.
 */
@AndroidEntryPoint
class AeroBarOverlayService : Service() {

    companion object {
        private const val TAG = "AeroBarService"
        private const val BAR_HEIGHT_DP = 88
        private const val TYPE_NAVIGATION_BAR = 2019
        private const val TYPE_NAVIGATION_BAR_PANEL = 2024
    }

    @Inject
    lateinit var appRepository: AppRepository

    @Inject
    lateinit var recentAppsRepository: RecentAppsRepository

    @Inject
    lateinit var vehicleHvacRepository: VehicleHvacRepository

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Uncaught coroutine exception intercepted in AeroBarOverlayService", throwable)
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + coroutineExceptionHandler)

    private var windowManager: WindowManager? = null

    // Window 1: Permanent Fixed 88dp Bottom Bar (Never resized -> Zero flicker)
    private var barComposeView: ComposeView? = null
    private var barLayoutParams: WindowManager.LayoutParams? = null

    // Window 2: Pre-allocated Popup Overlay Window (Only toggles FLAG_NOT_TOUCHABLE)
    private var popupComposeView: ComposeView? = null
    private var popupLayoutParams: WindowManager.LayoutParams? = null

    private var aeroBarViewModel: AeroBarViewModel? = null
    private var recentAppsViewModel: RecentAppsViewModel? = null
    private var appDrawerViewModel: AppDrawerViewModel? = null

    private val packageChangeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            Log.d(TAG, "Package change detected: ${intent?.action}, refreshing App Drawer...")
            appDrawerViewModel?.loadApps()
        }
    }

    /**
     * Toggles touchability on the popup window without changing any window dimensions,
     * eliminating SurfaceFlinger buffer reallocation and preventing any bar flicker.
     */
    fun setPopupWindowTouchable(touchable: Boolean) {
        val wm = windowManager ?: return
        val view = popupComposeView ?: return
        val params = popupLayoutParams ?: return

        val baseFlags = (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)

        val targetFlags = if (touchable) {
            baseFlags
        } else {
            baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        if (params.flags != targetFlags) {
            params.flags = targetFlags
            try {
                wm.updateViewLayout(view, params)
            } catch (e: Exception) {
                Log.w(TAG, "setPopupWindowTouchable error: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        com.aero.bar.security.SecurityGuard.enforceIntegrity(this)
        startForegroundServiceNotification()
        ensureAccessibilityEnabled()
        enforcePermanentNonImmersivePolicy()

        recentAppsViewModel = RecentAppsViewModel(recentAppsRepository)
        aeroBarViewModel = AeroBarViewModel(
            appRepository = appRepository,
            recentAppsRepository = recentAppsRepository,
            vehicleHvacRepository = vehicleHvacRepository,
            recentAppsViewModel = recentAppsViewModel,
            onOverlayResize = ::setPopupWindowTouchable,
            onHomeAction = ::navigateToHome,
            onBarCollapseChange = ::onBarCollapseChanged
        )
        appDrawerViewModel = AppDrawerViewModel(appRepository)

        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        registerReceiver(packageChangeReceiver, packageFilter)

        setupDualOverlayWindows()

        // Background Pre-warming: Prefetch App Drawer & Recent Apps in RAM during boot so first tap opens in 0ms!
        serviceScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Pre-warming App Drawer and Recent Apps in RAM...")
                appDrawerViewModel?.loadApps()
                recentAppsRepository.getRecentTasks()
                Log.d(TAG, "Pre-warming complete: Caches warmed and ready.")
            } catch (e: Exception) {
                Log.w(TAG, "Silent pre-warming encountered non-critical error: ${e.message}")
            }
        }
    }

    private fun onBarCollapseChanged(collapsed: Boolean) {
        val targetHeight = if (collapsed) dpToPx(16) else dpToPx(BAR_HEIGHT_DP)
        barLayoutParams?.height = targetHeight
        barComposeView?.let { v ->
            try {
                windowManager?.updateViewLayout(v, barLayoutParams)
                Log.i(TAG, "AeroBar dock height updated to $targetHeight px (collapsed=$collapsed)")
            } catch (e: Exception) {
                Log.w(TAG, "updateViewLayout error: ${e.message}")
            }
        }
    }

    /**
     * Deletes forced immersive fullscreen so apps cleanly receive bottom window insets,
     * ensures hardware keys override is active, and eliminates any lingering OEM 200px bars.
     */
    private fun enforcePermanentNonImmersivePolicy() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                android.provider.Settings.Global.putString(
                    contentResolver,
                    "policy_control",
                    "null"
                )
            } catch (_: Exception) {}

            val commands = listOf(
                "settings delete global policy_control",
                "setprop qemu.hw.mainkeys 1",
                "killall com.android.systemui",
                "pkill -f com.android.systemui",
                "dumpsys deviceidle whitelist +$packageName",
                "cmd deviceidle whitelist +$packageName",
                "dumpsys deviceidle whitelist +com.aero.bar",
                "dumpsys deviceidle whitelist +com.aero.bar.dongfeng_shine"
            )
            executePrivilegeCommands(commands)
        }
    }

    private fun executePrivilegeCommands(commands: List<String>) {
        val script = commands.joinToString(" ; ")
        val candidates = listOf(
            arrayOf("su", "0", "sh", "-c", script),
            arrayOf("su", "-c", script),
            arrayOf("/system/xbin/su", "0", "sh", "-c", script),
            arrayOf("sh", "-c", script)
        )
        for (cmd in candidates) {
            try {
                val proc = Runtime.getRuntime().exec(cmd)
                if (proc.waitFor() == 0) return
            } catch (_: Exception) {}
        }
    }

    private fun ensureAccessibilityEnabled() {
        try {
            val serviceComponent = "$packageName/${AeroAccessibilityService::class.java.name}"
            val enabledServices = android.provider.Settings.Secure.getString(
                contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            if (!enabledServices.contains(serviceComponent)) {
                val newServices = if (enabledServices.isEmpty()) serviceComponent else "$enabledServices:$serviceComponent"
                android.provider.Settings.Secure.putString(
                    contentResolver,
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    newServices
                )
                android.provider.Settings.Secure.putInt(
                    contentResolver,
                    android.provider.Settings.Secure.ACCESSIBILITY_ENABLED,
                    1
                )
                Log.i(TAG, "Auto-enabled accessibility service: $serviceComponent")
            }
        } catch (e: Exception) {
            Log.w(TAG, "ensureAccessibilityEnabled error: ${e.message}")
        }
    }

    private fun navigateToHome() {
        AeroAccessibilityService.performHome()
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            startActivity(homeIntent)
        } catch (e: Exception) {
            Log.w(TAG, "startActivity Home error: ${e.message}")
        }
    }

    private fun setupDualOverlayWindows() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val defaultOverlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ERROR
        }

        val basePermanentFlags = (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)

        // Lifecycle owner shared by both Compose windows
        val serviceLifecycleOwner = OverlayServiceLifecycleOwner()
        serviceLifecycleOwner.performRestore(null)
        serviceLifecycleOwner.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_CREATE)
        serviceLifecycleOwner.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_START)
        serviceLifecycleOwner.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_RESUME)

        // =========================================================================
        // WINDOW 1: Pre-allocated Popup Overlay Window (Recent Apps & All Apps)
        // Positioned above the 88dp bar. Starts with FLAG_NOT_TOUCHABLE so touches
        // pass through when closed, and never resizes its Surface buffer.
        // =========================================================================
        val popupParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            defaultOverlayType,
            basePermanentFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            title = "AeroBarPopupOverlay"
        }
        popupLayoutParams = popupParams

        popupComposeView = ComposeView(this).apply {
            @Suppress("DEPRECATION")
            systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE

            setViewTreeLifecycleOwner(serviceLifecycleOwner)
            setViewTreeViewModelStoreOwner(serviceLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(serviceLifecycleOwner)

            setContent {
                AeroBarTheme {
                    val vm = aeroBarViewModel ?: return@AeroBarTheme
                    val state by vm.uiState.collectAsState()

                    val rVm = recentAppsViewModel ?: return@AeroBarTheme
                    val rState by rVm.uiState.collectAsState()

                    val dVm = appDrawerViewModel ?: return@AeroBarTheme
                    val dState by dVm.uiState.collectAsState()

                    // Synchronize popup touchability with overlay state (Recents, All Apps, HVAC Slider, or Standby Tap-to-Wake)
                    val isAnyPopupOpen = state.isRecentsOpen ||
                        state.isDrawerOpen ||
                        state.activeHvacSlider != com.aero.bar.ui.bar.ActiveHvacSlider.NONE ||
                        state.hvacState.isScreenStandby

                    LaunchedEffect(isAnyPopupOpen) {
                        setPopupWindowTouchable(isAnyPopupOpen)
                    }

                    // Note: recents are (re)loaded by AeroBarViewModel when the panel opens.
                    // A second load here used to start a parallel fetch that raced with the first.

                    // Refresh installed apps on drawer open, and dismiss any open context menu on drawer close
                    LaunchedEffect(state.isDrawerOpen) {
                        if (state.isDrawerOpen) {
                            dVm.loadApps()
                        } else {
                            dVm.onIntent(com.aero.bar.ui.drawer.AppDrawerIntent.OnDismissContextMenu)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = BAR_HEIGHT_DP.dp)
                    ) {
                        // Standby Tap-to-Wake Overlay Catcher: tapping anywhere on the upper screen wakes the display
                        if (state.hvacState.isScreenStandby) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.90f))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        vm.wakeScreenIfOff()
                                    }
                            )
                        }

                        // Dark Scrim Backdrop for Recents / All Apps
                        AnimatedVisibility(
                            visible = state.isRecentsOpen || state.isDrawerOpen,
                            enter = fadeIn(animationSpec = tween(140, easing = FastOutSlowInEasing)),
                            exit = fadeOut(animationSpec = tween(120, easing = FastOutSlowInEasing))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.60f))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        vm.closeOverlays()
                                    }
                            )
                        }

                        // Transparent Outside-Tap Dismiss Catcher when only HVAC Slider is open
                        if (state.activeHvacSlider != com.aero.bar.ui.bar.ActiveHvacSlider.NONE &&
                            !state.isRecentsOpen && !state.isDrawerOpen
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        vm.onIntent(com.aero.bar.ui.bar.AeroBarIntent.OnDismissHvacSlider)
                                    }
                            )
                        }

                        // 1. Floating Recent Apps Overlay
                        AnimatedVisibility(
                            visible = state.isRecentsOpen,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 18.dp),
                            enter = fadeIn(tween(140, easing = FastOutSlowInEasing)) +
                                    scaleIn(initialScale = 0.96f, animationSpec = tween(150, easing = FastOutSlowInEasing)) +
                                    slideInVertically(initialOffsetY = { it / 10 }, animationSpec = tween(150, easing = FastOutSlowInEasing)),
                            exit = fadeOut(tween(120, easing = FastOutSlowInEasing)) +
                                    scaleOut(targetScale = 0.96f, animationSpec = tween(120, easing = FastOutSlowInEasing)) +
                                    slideOutVertically(targetOffsetY = { it / 10 }, animationSpec = tween(120, easing = FastOutSlowInEasing))
                        ) {
                            // Runs when the exit animation has fully finished and the panel content
                            // left composition: the only moment a reorder is invisible to the user.
                            DisposableEffect(Unit) {
                                onDispose { rVm.onPanelHidden() }
                            }
                            RecentAppsView(
                                state = rState,
                                countdown = state.overlayDismissCountdown,
                                onResetTimer = { vm.onIntent(com.aero.bar.ui.bar.AeroBarIntent.OnResetOverlayTimer) },
                                onIntent = rVm::onIntent,
                                onDismissOverlay = { vm.closeOverlays() }
                            )
                        }

                        // 2. Floating All Apps Drawer Overlay
                        AnimatedVisibility(
                            visible = state.isDrawerOpen,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 18.dp),
                            enter = fadeIn(tween(140, easing = FastOutSlowInEasing)) +
                                    scaleIn(initialScale = 0.96f, animationSpec = tween(150, easing = FastOutSlowInEasing)) +
                                    slideInVertically(initialOffsetY = { it / 10 }, animationSpec = tween(150, easing = FastOutSlowInEasing)),
                            exit = fadeOut(tween(120, easing = FastOutSlowInEasing)) +
                                    scaleOut(targetScale = 0.96f, animationSpec = tween(120, easing = FastOutSlowInEasing)) +
                                    slideOutVertically(targetOffsetY = { it / 10 }, animationSpec = tween(120, easing = FastOutSlowInEasing))
                        ) {
                            AppDrawerView(
                                state = dState,
                                countdown = state.overlayDismissCountdown,
                                onResetTimer = { vm.onIntent(com.aero.bar.ui.bar.AeroBarIntent.OnResetOverlayTimer) },
                                onIntent = dVm::onIntent,
                                onDismissOverlay = { vm.closeOverlays() }
                            )
                        }

                        // 3. Minimal Pop-up HVAC Slider (Design #2 Temperature Ruler / Design #3 Fan Segments)
                        AnimatedVisibility(
                            visible = state.activeHvacSlider != com.aero.bar.ui.bar.ActiveHvacSlider.NONE,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 14.dp),
                            enter = fadeIn(tween(130, easing = FastOutSlowInEasing)) +
                                    scaleIn(initialScale = 0.95f, animationSpec = tween(140, easing = FastOutSlowInEasing)) +
                                    slideInVertically(initialOffsetY = { it / 4 }, animationSpec = tween(140, easing = FastOutSlowInEasing)),
                            exit = fadeOut(tween(110, easing = FastOutSlowInEasing)) +
                                    scaleOut(targetScale = 0.95f, animationSpec = tween(110, easing = FastOutSlowInEasing)) +
                                    slideOutVertically(targetOffsetY = { it / 4 }, animationSpec = tween(110, easing = FastOutSlowInEasing))
                        ) {
                            com.aero.bar.ui.bar.HvacSliderPopupView(
                                sliderType = state.activeHvacSlider,
                                hvacState = state.hvacState,
                                onIntent = vm::onIntent
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // WINDOW 2: Permanent Fixed 960x88px Bottom Bar
        // Never resized, never re-measured -> 0% flicker guaranteed.
        // =========================================================================
        val barParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            dpToPx(BAR_HEIGHT_DP),
            defaultOverlayType,
            basePermanentFlags,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = 0
            y = 0
            title = "AeroBarPersistentDock"
        }
        barLayoutParams = barParams

        barComposeView = ComposeView(this).apply {
            @Suppress("DEPRECATION")
            systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE

            setViewTreeLifecycleOwner(serviceLifecycleOwner)
            setViewTreeViewModelStoreOwner(serviceLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(serviceLifecycleOwner)

            setContent {
                AeroBarTheme {
                    val vm = aeroBarViewModel ?: return@AeroBarTheme
                    val state by vm.uiState.collectAsState()

                    AeroBarView(
                        state = state,
                        onIntent = vm::onIntent
                    )
                }
            }
        }

        // Add Popup Window first, then Permanent Bar Window on top so Bar has highest Z-order
        addWindowWithPrivilegeFallback(popupComposeView!!, popupParams, defaultOverlayType)
        addBarWindowWithPrivilegeFallback(barComposeView!!, barParams, defaultOverlayType)
    }

    private fun addBarWindowWithPrivilegeFallback(
        view: View,
        params: WindowManager.LayoutParams,
        fallbackType: Int
    ) {
        fun safeRemove() {
            try {
                windowManager?.removeViewImmediate(view)
            } catch (_: Exception) {}
        }

        // Tier 1: True System Navigation Bar (reserves native insets in WindowManager for all apps)
        try {
            safeRemove()
            params.type = TYPE_NAVIGATION_BAR
            windowManager?.addView(view, params)
            Log.i(TAG, "Added ${params.title} with native TYPE_NAVIGATION_BAR (2019)")
            return
        } catch (e: Exception) {
            Log.w(TAG, "Failed TYPE_NAVIGATION_BAR (2019): ${e.message}")
            safeRemove()
        }

        // Tier 2: Navigation Bar Panel
        try {
            safeRemove()
            params.type = TYPE_NAVIGATION_BAR_PANEL
            windowManager?.addView(view, params)
            Log.i(TAG, "Added ${params.title} with TYPE_NAVIGATION_BAR_PANEL (2024)")
            return
        } catch (e: Exception) {
            Log.w(TAG, "Failed TYPE_NAVIGATION_BAR_PANEL (2024): ${e.message}")
            safeRemove()
        }

        // Tier 3: Standard Overlay type
        try {
            safeRemove()
            params.type = fallbackType
            windowManager?.addView(view, params)
            Log.i(TAG, "Added ${params.title} with standard overlay type ($fallbackType)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add window ${params.title}", e)
        }
    }

    private fun addWindowWithPrivilegeFallback(
        view: View,
        params: WindowManager.LayoutParams,
        fallbackType: Int
    ) {
        fun safeRemove() {
            try {
                windowManager?.removeViewImmediate(view)
            } catch (_: Exception) {}
        }

        try {
            safeRemove()
            params.type = TYPE_NAVIGATION_BAR_PANEL
            windowManager?.addView(view, params)
            Log.i(TAG, "Added ${params.title} with TYPE_NAVIGATION_BAR_PANEL (2024)")
        } catch (_: Exception) {
            safeRemove()
            params.type = fallbackType
            windowManager?.addView(view, params)
            Log.i(TAG, "Added ${params.title} with standard overlay type ($fallbackType)")
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "aero_bar_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Aero Bar Service",
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("Aero Bar Active")
                .setContentText("Persistent Navigation & HVAC Bar is active.")
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Aero Bar Active")
                .setContentText("Persistent Navigation & HVAC Bar is active.")
                .setPriority(Notification.PRIORITY_MIN)
                .build()
        }

        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val restartedFromCrash = intent?.getBooleanExtra(CrashHandler.EXTRA_RESTARTED_FROM_CRASH, false) == true
        val safeMode = intent?.getBooleanExtra(CrashHandler.EXTRA_SAFE_MODE, false) == true || CrashHandler.isSafeModeActive(this)

        if (restartedFromCrash) {
            Log.w(TAG, "AeroBarOverlayService successfully resurrected from crash! (safeMode=$safeMode)")
            // After 30 seconds of stable running without further crashes, clear safe mode
            serviceScope.launch {
                delay(30_000L)
                CrashHandler.clearSafeMode(this@AeroBarOverlayService)
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        try {
            unregisterReceiver(packageChangeReceiver)
        } catch (_: Exception) {}
        popupComposeView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
        }
        barComposeView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
        }
    }
}
