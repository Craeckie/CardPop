/*
 * Copyright (C) 2026 FloFla Dev
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.cardpop.app.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardpop.app.data.repository.FlashcardRepository
import com.cardpop.app.domain.usecase.OnboardingUseCase
import com.cardpop.app.domain.usecase.StatisticsUseCase
import com.cardpop.app.domain.usecase.SimpleStatistics
import com.cardpop.app.service.LearningServiceManager
import com.cardpop.app.data.repository.SettingsRepository
import com.cardpop.app.util.PermissionHelper
import com.cardpop.app.util.IntervalConstants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val isServiceActive: Boolean = false,
    val nextFlashcardCountdown: Long = 0L,
    val activeFlashcardCount: Int = 0,
    val selectedInterval: Int = 5, // minutes
    val isLoading: Boolean = false,
    val statistics: SimpleStatistics? = null,
    val pendingInterval: Int? = null, // For storing interval when waiting for permission
    val isSnoozing: Boolean = false,
    val snoozeRemainingSeconds: Long = 0L
)

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: FlashcardRepository,
    private val onboardingUseCase: OnboardingUseCase,
    private val statisticsUseCase: StatisticsUseCase,
    private val learningServiceManager: LearningServiceManager,
    private val settingsManager: SettingsRepository,
    private val permissionHelper: PermissionHelper
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        initializeApp()
        observeServiceState()
        loadActiveFlashcardCount()
        loadStatistics()
        observeSettings()
        observeSnoozeState()
    }
    
    private fun initializeApp() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            onboardingUseCase.createSampleDataIfNeeded()
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }
    
    private fun observeServiceState() {
        viewModelScope.launch {
            learningServiceManager.isServiceActive.collect { isActive ->
                _uiState.value = _uiState.value.copy(isServiceActive = isActive)
            }
        }
        
        viewModelScope.launch {
            learningServiceManager.countdownTime.collect { countdown ->
                _uiState.value = _uiState.value.copy(nextFlashcardCountdown = countdown)
            }
        }
    }
    
    private fun loadActiveFlashcardCount() {
        viewModelScope.launch {
            val count = repository.getActiveFlashcardCount()
            _uiState.value = _uiState.value.copy(activeFlashcardCount = count)
        }
    }
    
    private fun observeSettings() {
        viewModelScope.launch {
            settingsManager.intervalMinutes.collect { interval ->
                _uiState.value = _uiState.value.copy(selectedInterval = interval)
            }
        }
    }

    private fun observeSnoozeState() {
        viewModelScope.launch {
            settingsManager.pausedUntilMs.collectLatest { pausedUntil ->
                if (pausedUntil <= 0L) {
                    _uiState.value = _uiState.value.copy(
                        isSnoozing = false,
                        snoozeRemainingSeconds = 0L
                    )
                    return@collectLatest
                }
                while (true) {
                    val remaining = ((pausedUntil - System.currentTimeMillis() + 999L) / 1000L)
                        .coerceAtLeast(0L)
                    _uiState.value = _uiState.value.copy(
                        isSnoozing = remaining > 0L,
                        snoozeRemainingSeconds = remaining
                    )
                    if (remaining <= 0L) break
                    delay(1000L)
                }
            }
        }
    }
    
    /**
     * Toggles the learning service state.
     * Follows SRP: single responsibility for service state management.
     */
    fun toggleLearningService() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.isServiceActive || state.isSnoozing) {
                stopLearningService()
            } else {
                startLearningService(state.selectedInterval)
            }
        }
    }
    
    /**
     * Starts learning service with a specific interval.
     * Updates settings and starts service - follows DRY principle.
     */
    fun startLearningWithInterval(intervalMinutes: Int) {
        viewModelScope.launch {
            updateInterval(intervalMinutes)
            startLearningService(intervalMinutes)
        }
    }
    
    /**
     * Sets pending interval for contextual permission checking.
     * Follows SRP by separating interval storage from service start.
     */
    fun setPendingInterval(intervalMinutes: Int) {
        _uiState.value = _uiState.value.copy(pendingInterval = intervalMinutes)
    }
    
    /**
     * Starts learning with pending interval after permission is granted.
     */
    fun startPendingLearning() {
        val state = _uiState.value
        if (state.pendingInterval != null) {
            _uiState.value = state.copy(pendingInterval = null)
            startLearningWithInterval(state.pendingInterval)
        }
    }
    
    /**
     * Checks if first-time demo should be shown.
     * Follows SRP by separating demo logic from main learning flow.
     */
    fun shouldShowFirstDemo(): Boolean {
        return !settingsManager.hasShownFirstDemo()
    }
    
    /**
     * Shows the first-time demo flashcard.
     * Follows SRP by handling only demo display logic.
     */
    fun showFirstDemo() {
        viewModelScope.launch {
            // Import the OverlayService class and use its demo method
            // This will be connected to the UI in Phase 5
        }
    }
    
    /**
     * Private method to start learning service.
     * Follows DRY principle by centralizing start logic.
     * CRITICAL: Only starts service if overlay permission is granted and no demo is running.
     */
    private suspend fun startLearningService(intervalMinutes: Int) {
        // CRITICAL BUG FIX: Never start service without overlay permission
        if (!permissionHelper.hasOverlayPermission()) {
            // Log or handle permission missing - service should not start
            return
        }
        
        // CRITICAL: Don't start timer if demo is currently running
        if (settingsManager.isDemoRunning()) {
            // Log that timer start is skipped due to demo running
            return
        }
        
        learningServiceManager.startLearningService(intervalMinutes)
    }
    
    /**
     * Private method to stop learning service.
     * Follows DRY principle by centralizing stop logic.
     */
    private suspend fun stopLearningService() {
        learningServiceManager.stopLearningService()
    }
    
    fun updateInterval(intervalMinutes: Int) {
        if (IntervalConstants.isValidInterval(intervalMinutes)) {
            settingsManager.setIntervalMinutes(intervalMinutes)
        }
    }

    private fun loadStatistics() {
        viewModelScope.launch {
            statisticsUseCase.getSimpleStatistics()
                .onSuccess { stats ->
                    _uiState.value = _uiState.value.copy(statistics = stats)
                }
                .onFailure { error ->
                    // Log error but don't crash - statistics are non-critical
                    println("Failed to load statistics: ${error.message}")
                }
        }
    }
    
    fun refreshFlashcardCount() {
        loadActiveFlashcardCount()
        loadStatistics()
    }
    
    /**
     * Checks if user has skipped battery optimization during welcome flow.
     * Used to determine if battery optimization dialog should be shown.
     * Follows SRP by delegating to SettingsRepository.
     */
    fun isBatteryOptimizationSkipped(): Boolean {
        return settingsManager.isBatteryOptimizationSkipped()
    }
    
    /**
     * Checks if user has ever successfully disabled battery optimization.
     * Used to determine if contextual dialogs should be shown when system re-enables it.
     * Follows SRP by delegating to SettingsRepository.
     */
    fun hasBatteryOptimizationEverBeenDisabled(): Boolean {
        return settingsManager.hasBatteryOptimizationEverBeenDisabled()
    }
}
