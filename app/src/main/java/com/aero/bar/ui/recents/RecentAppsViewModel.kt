package com.aero.bar.ui.recents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aero.bar.domain.model.RecentTaskItem
import com.aero.bar.domain.repository.RecentAppsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecentAppsViewModel @Inject constructor(
    private val recentAppsRepository: RecentAppsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecentAppsState())
    val uiState: StateFlow<RecentAppsState> = _uiState.asStateFlow()

    // Only one fetch may update the UI at a time; a newer load (or a task click) cancels the older one
    // so a slow, stale snapshot can never overwrite a fresher order.
    private var loadJob: Job? = null

    // Reorder produced by a task click. It is applied only once the panel is fully hidden
    // (see onPanelHidden) so the user never sees cards jumping around.
    private var pendingReorder: List<RecentTaskItem>? = null

    init {
        loadRecents()
    }

    fun loadRecents() {
        // If the panel is reopened before the hide callback ran, commit the pending order first.
        applyPendingReorder()
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = it.recentTasks.isEmpty()) }
            recentAppsRepository.getRecentTasks().collect { tasks ->
                val (usedMb, totalMb) = recentAppsRepository.getSystemMemoryTelemetryMb()
                _uiState.update {
                    it.copy(
                        recentTasks = tasks,
                        totalRamUsedMb = usedMb,
                        totalRamDeviceMb = totalMb,
                        isLoading = false
                    )
                }
            }
        }
    }

    /**
     * Called by the overlay once the Recents panel exit animation has finished and its content left
     * composition. Safe point to reorder the list invisibly.
     */
    fun onPanelHidden() {
        applyPendingReorder()
    }

    private fun applyPendingReorder() {
        val reordered = pendingReorder ?: return
        pendingReorder = null
        _uiState.update { it.copy(recentTasks = reordered) }
    }

    fun onIntent(intent: RecentAppsIntent) {
        when (intent) {
            is RecentAppsIntent.OnLoadRecentTasks -> loadRecents()
            is RecentAppsIntent.OnTaskClicked -> {
                // Drop any in-flight fetch: its snapshot was taken before this switch and would
                // otherwise restore the old order (and poison the cache) after we promote the task.
                loadJob?.cancel()

                // Promote the launched task to index 0, but keep the visible list untouched while
                // the panel is animating out. The repository cache gets the new order right away
                // so the next open shows it instantly on frame 0.
                val current = _uiState.value.recentTasks
                val reordered = listOf(intent.task) + current.filterNot { it.packageName == intent.task.packageName }
                pendingReorder = reordered
                recentAppsRepository.updateCachedTasks(reordered)

                viewModelScope.launch {
                    recentAppsRepository.switchTask(intent.task.persistentId, intent.task.packageName)
                }
            }
            is RecentAppsIntent.OnDismissTask -> {
                // Instant responsive UI feedback: immediately remove task and subtract its memory
                val dismissedMb = intent.task.memoryUsageMb
                val currentTasks = _uiState.value.recentTasks.filterNot { it.persistentId == intent.task.persistentId }
                val currentUsedMb = (_uiState.value.totalRamUsedMb - dismissedMb).coerceAtLeast(128)
                _uiState.update {
                    it.copy(
                        recentTasks = currentTasks,
                        totalRamUsedMb = currentUsedMb
                    )
                }
                viewModelScope.launch {
                    recentAppsRepository.dismissTask(intent.task.persistentId, intent.task.packageName)
                    loadRecents()
                }
            }
            is RecentAppsIntent.OnClearAllTasks -> {
                // Instant responsive UI feedback: clear list immediately and drop used RAM
                val totalFreed = _uiState.value.recentTasks.sumOf { it.memoryUsageMb }
                val currentUsedMb = (_uiState.value.totalRamUsedMb - totalFreed).coerceAtLeast(128)
                _uiState.update {
                    it.copy(
                        recentTasks = emptyList(),
                        totalRamUsedMb = currentUsedMb
                    )
                }
                viewModelScope.launch {
                    recentAppsRepository.clearAllTasks()
                    loadRecents()
                }
            }
        }
    }
}
