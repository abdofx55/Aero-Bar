package com.aero.bar.ui.hidden

import com.aero.bar.domain.model.AppItem

data class HiddenAppsState(
    val hiddenApps: List<AppItem> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true
)

sealed interface HiddenAppsIntent {
    data class OnSearchQueryChanged(val query: String) : HiddenAppsIntent
    data class OnUnhideApp(val app: AppItem) : HiddenAppsIntent
    data object OnUnhideAllApps : HiddenAppsIntent
    data object OnBackClicked : HiddenAppsIntent
}
