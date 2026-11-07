package com.aero.bar.ui.drawer

import com.aero.bar.domain.model.AppItem

enum class AppDrawerTab {
    ALL,
    HIDDEN
}

data class AppDrawerState(
    val apps: List<AppItem> = emptyList(),
    val filteredApps: List<AppItem> = emptyList(),
    val hiddenApps: List<AppItem> = emptyList(),
    val currentTab: AppDrawerTab = AppDrawerTab.ALL,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val selectedAppForContext: AppItem? = null
)

sealed interface AppDrawerIntent {
    data class OnSearchQueryChanged(val query: String) : AppDrawerIntent
    data class OnAppClicked(val app: AppItem) : AppDrawerIntent
    data class OnAppLongClicked(val app: AppItem) : AppDrawerIntent
    data object OnDismissContextMenu : AppDrawerIntent
    data class OnPinApp(val app: AppItem, val slot: Int) : AppDrawerIntent
    data class OnHideApp(val app: AppItem) : AppDrawerIntent
    data class OnUnhideApp(val app: AppItem) : AppDrawerIntent
    data object OnAutoFilterOemApps : AppDrawerIntent
    data class OnTabChanged(val tab: AppDrawerTab) : AppDrawerIntent
    data class OnAppDetails(val app: AppItem) : AppDrawerIntent
    data class OnUninstallApp(val app: AppItem) : AppDrawerIntent
    data object OnOpenHiddenAppsPanel : AppDrawerIntent
}
