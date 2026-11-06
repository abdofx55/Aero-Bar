package com.aero.bar.ui.hidden

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aero.bar.ui.theme.ElectricCyan
import com.aero.bar.ui.theme.ObsidianSurface

/**
 * Screen 5: Hidden Apps Management Panel.
 * Base Composable shell ready for visual UI hand-coding matching:
 * screens/screen_5_hidden_apps.html
 */
@Composable
fun HiddenAppsView(
    state: HiddenAppsState,
    onIntent: (HiddenAppsIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianSurface)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        // Ready for user's Compose hidden apps list matching screen_5_hidden_apps.html
        Text(text = "Hidden Apps Management", color = ElectricCyan)
    }
}
