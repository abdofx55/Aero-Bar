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
                _uiState.update { current ->
                    current.copy(
                        apps = visibleApps,
                        filteredApps = filterList(visibleApps, current.searchQuery),
                        isLoading = false
                    )
                }
            }
        }
    }

    fun onIntent(intent: AppDrawerIntent) {
        when (intent) {
            is AppDrawerIntent.OnSearchQueryChanged -> {
                _uiState.update { current ->
                    current.copy(
                        searchQuery = intent.query,
                        filteredApps = filterList(current.apps, intent.query)
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
                    _uiState.update { it.copy(selectedAppForContext = null) }
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
                // Navigates or sets flag for hidden apps screen
            }
        }
    }

    private fun filterList(apps: List<AppItem>, query: String): List<AppItem> {
        if (query.isBlank()) return apps
        return apps.filter { it.label.contains(query, ignoreCase = true) }
    }
}
