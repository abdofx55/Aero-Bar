package com.aero.bar.ui.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aero.bar.domain.model.AppItem
import com.aero.bar.domain.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppDrawerViewModel @Inject constructor(
    private val appRepository: AppRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppDrawerState())
    val uiState: StateFlow<AppDrawerState> = _uiState.asStateFlow()

    init {
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            appRepository.getInstalledApps().collect { allApps ->
                val visibleApps = allApps.filter { !it.isHidden }
                val hiddenApps = allApps.filter { it.isHidden }
                _uiState.update { current ->
                    val activeSource = if (current.currentTab == AppDrawerTab.ALL) visibleApps else hiddenApps
                    current.copy(
                        apps = visibleApps,
                        hiddenApps = hiddenApps,
                        filteredApps = filterList(activeSource, current.searchQuery),
                        isLoading = false
                    )
                }
            }
        }
    }

    fun onIntent(intent: AppDrawerIntent) {
        when (intent) {
            is AppDrawerIntent.OnTabChanged -> {
                _uiState.update { current ->
                    val activeSource = if (intent.tab == AppDrawerTab.ALL) current.apps else current.hiddenApps
                    current.copy(
                        currentTab = intent.tab,
                        filteredApps = filterList(activeSource, current.searchQuery),
                        selectedAppForContext = null
                    )
                }
            }
            is AppDrawerIntent.OnSearchQueryChanged -> {
                _uiState.update { current ->
                    val activeSource = if (current.currentTab == AppDrawerTab.ALL) current.apps else current.hiddenApps
                    current.copy(
                        searchQuery = intent.query,
                        filteredApps = filterList(activeSource, intent.query)
                    )
                }
            }
            is AppDrawerIntent.OnAppClicked -> {
                viewModelScope.launch {
                    appRepository.launchApp(intent.app.packageName, intent.app.activityName)
                }
            }
            is AppDrawerIntent.OnAppLongClicked -> {
                _uiState.update { it.copy(selectedAppForContext = intent.app) }
            }
            is AppDrawerIntent.OnDismissContextMenu -> {
                _uiState.update { it.copy(selectedAppForContext = null) }
            }
            is AppDrawerIntent.OnPinApp -> {
                viewModelScope.launch {
                    appRepository.pinApp(intent.app.packageName, intent.slot)
                    _uiState.update { it.copy(selectedAppForContext = null) }
                }
            }
            is AppDrawerIntent.OnHideApp -> {
                viewModelScope.launch {
                    appRepository.hideApp(intent.app.packageName)
                    loadApps()
                    _uiState.update { it.copy(selectedAppForContext = null) }
                }
            }
            is AppDrawerIntent.OnUnhideApp -> {
                viewModelScope.launch {
                    appRepository.unhideApp(intent.app.packageName)
                    loadApps()
                    _uiState.update { it.copy(selectedAppForContext = null) }
                }
            }
            is AppDrawerIntent.OnAutoFilterOemApps -> {
                viewModelScope.launch {
                    val oemGarbage = setOf(
                        "com.qualcomm.qti.logkit",
                        "com.touch.opentouch",
                        "com.touch.improvetouch",
                        "com.dongfeng.dab",
                        "com.dongfeng.radio",
                        "com.android.camera2"
                    )
                    oemGarbage.forEach { pkg ->
                        appRepository.hideApp(pkg)
                    }
                    loadApps()
                }
            }
            is AppDrawerIntent.OnAppDetails -> {
                viewModelScope.launch {
                    appRepository.openAppDetails(intent.app.packageName)
                    _uiState.update { it.copy(selectedAppForContext = null) }
                }
            }
            is AppDrawerIntent.OnUninstallApp -> {
                // Optimistically remove uninstalled app immediately from list
                val remainingApps = _uiState.value.apps.filterNot { it.packageName == intent.app.packageName }
                _uiState.update { current ->
                    current.copy(
                        apps = remainingApps,
                        filteredApps = filterList(remainingApps, current.searchQuery),
                        selectedAppForContext = null
                    )
                }
                viewModelScope.launch {
                    appRepository.uninstallApp(intent.app.packageName)
                    loadApps()
                }
            }
            is AppDrawerIntent.OnOpenHiddenAppsPanel -> {
                onIntent(AppDrawerIntent.OnTabChanged(AppDrawerTab.HIDDEN))
            }
        }
    }

    private fun filterList(apps: List<AppItem>, query: String): List<AppItem> {
        if (query.isBlank()) return apps
        return apps.filter { it.label.contains(query, ignoreCase = true) }
    }
}
