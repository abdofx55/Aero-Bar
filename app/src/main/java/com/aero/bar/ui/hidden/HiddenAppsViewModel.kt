package com.aero.bar.ui.hidden

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
class HiddenAppsViewModel @Inject constructor(
    private val appRepository: AppRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HiddenAppsState())
    val uiState: StateFlow<HiddenAppsState> = _uiState.asStateFlow()

    init {
        loadHiddenApps()
    }

    private fun loadHiddenApps() {
        viewModelScope.launch {
            appRepository.getHiddenApps().collect { hidden ->
                _uiState.update { current ->
                    current.copy(
                        hiddenApps = hidden,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun onIntent(intent: HiddenAppsIntent) {
        when (intent) {
            is HiddenAppsIntent.OnSearchQueryChanged -> {
                _uiState.update { it.copy(searchQuery = intent.query) }
            }
            is HiddenAppsIntent.OnUnhideApp -> {
                viewModelScope.launch {
                    appRepository.unhideApp(intent.app.packageName)
                }
            }
            is HiddenAppsIntent.OnUnhideAllApps -> {
                viewModelScope.launch {
                    appRepository.unhideAllApps()
                }
            }
            is HiddenAppsIntent.OnBackClicked -> {
                // Handled in UI navigation
            }
        }
    }
}
