package com.aero.bar.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aero.bar.service.AeroBarOverlayService
import com.aero.bar.ui.theme.AeroBarTheme
import com.aero.bar.ui.theme.AeroBlack
import com.aero.bar.ui.theme.ElectricCyan
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val hasOverlay = Settings.canDrawOverlays(this)
        val hasAccessibility = isAccessibilityServiceEnabled()

        if (hasOverlay) {
            startOverlayService()
        }

        setContent {
            AeroBarTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AeroBlack
                ) {
                    SetupScreen(
                        hasOverlayPermission = hasOverlay,
                        hasAccessibilityPermission = hasAccessibility,
                        onRequestPermission = { requestOverlayPermission() },
                        onRequestAccessibility = { requestAccessibilityPermission() },
                        onStartService = { startOverlayService() }
                    )
                }
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedService = "$packageName/${AeroBarOverlayService::class.java.`package`?.name}.AeroAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        return enabledServices.contains(packageName)
    }

    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, AeroBarOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

@Composable
fun SetupScreen(
    hasOverlayPermission: Boolean,
    hasAccessibilityPermission: Boolean,
    onRequestPermission: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onStartService: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Aero Bar - Dongfeng Shine",
            style = MaterialTheme.typography.headlineMedium,
            color = ElectricCyan
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Ensure Overlay and Accessibility are enabled for full hardware navigation (Back, Home, Recents) on all vehicle apps.",
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (!hasOverlayPermission) {
            Button(onClick = onRequestPermission) {
                Text(text = "1. Grant Overlay Permission")
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (!hasAccessibilityPermission) {
            Button(onClick = onRequestAccessibility) {
                Text(text = "2. Enable Accessibility (Back Button)")
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Button(onClick = onStartService) {
            Text(text = "Start Aero Bar Dock")
        }
    }
}
