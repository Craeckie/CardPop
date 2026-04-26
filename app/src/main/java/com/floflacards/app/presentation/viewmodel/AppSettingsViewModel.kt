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

package com.floflacards.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.floflacards.app.data.repository.SettingsRepository
import com.floflacards.app.data.model.AppTheme
import com.floflacards.app.data.model.FlashcardTheme
import com.floflacards.app.data.model.Language
import com.floflacards.app.domain.usecase.RetentionData
import com.floflacards.app.domain.usecase.StatisticsUseCase
import com.floflacards.app.util.PermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for AppSettingsScreen following MVVM architecture.
 * Handles theme preference management and other app settings.
 * 
 * Follows SOLID principles:
 * - Single Responsibility: Manages app settings state
 * - Dependency Inversion: Depends on SettingsRepository abstraction
 */
@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val settingsManager: SettingsRepository,
    private val permissionHelper: PermissionHelper,
    private val statisticsUseCase: StatisticsUseCase
) : ViewModel() {

    init {
        refreshActualRetention()
    }
    
    /**
     * Current app theme preference as StateFlow
     * CRITICAL: This controls app theme independently from device theme
     */
    val appTheme: StateFlow<AppTheme> = settingsManager.appTheme
    
    /**
     * Current flashcard theme preference as StateFlow
     * CRITICAL: This controls flashcard theme independently from both app and device theme
     */
    val flashcardTheme: StateFlow<FlashcardTheme> = settingsManager.flashcardTheme
    
    /**
     * Current app locale preference as StateFlow
     * CRITICAL: This controls app language independently from system locale
     */
    val appLocale: StateFlow<Language> = settingsManager.appLocale

    /** FSRS target retention (0.80–0.95). Higher = more reviews, less forgetting. */
    val targetRetention: StateFlow<Double> = settingsManager.targetRetention

    fun setTargetRetention(value: Double) {
        settingsManager.setTargetRetention(value)
    }

    /**
     * Aggregated retention across all of the user's reviews so far. Null until
     * the first load completes. The settings screen calls [refreshActualRetention]
     * on resume so the value reflects newly-rated cards without restarting the VM.
     */
    private val _actualRetention = MutableStateFlow<RetentionData?>(null)
    val actualRetention: StateFlow<RetentionData?> = _actualRetention.asStateFlow()

    fun refreshActualRetention() {
        viewModelScope.launch {
            runCatching { statisticsUseCase.getRetention() }
                .onSuccess { _actualRetention.value = it }
        }
    }

    /** Flashcard overlay opacity (0.1f..1.0f, 10% floor keeps the card visible). */
    val flashcardOpacity: StateFlow<Float> = settingsManager.flashcardOpacity

    fun setFlashcardOpacity(value: Float) {
        settingsManager.setFlashcardOpacity(value)
    }

    /** Snooze duration in minutes (discrete: 5, 10, 30, 60, 120, 360, 1440). */
    val snoozeDurationMinutes: StateFlow<Int> = settingsManager.snoozeDurationMinutes

    fun setSnoozeDurationMinutes(minutes: Int) {
        settingsManager.setSnoozeDurationMinutes(minutes)
    }
    
    /**
     * Updates the app theme preference
     * CRITICAL: This will immediately change the app theme
     */
    fun setAppTheme(theme: AppTheme) {
        settingsManager.setAppTheme(theme)
    }
    
    /**
     * Updates the flashcard theme preference
     * CRITICAL: This will immediately change the flashcard theme
     */
    fun setFlashcardTheme(theme: FlashcardTheme) {
        settingsManager.setFlashcardTheme(theme)
    }
    
    /**
     * Updates the app locale preference
     * CRITICAL: This will immediately change the app language
     */
    fun setAppLocale(language: Language) {
        settingsManager.setAppLocale(language)
    }
    
    /**
     * Checks if battery optimization is disabled.
     * Follows SRP by delegating to PermissionHelper.
     */
    fun isBatteryOptimizationDisabled(): Boolean {
        return permissionHelper.isBatteryOptimizationDisabled()
    }
    
    /**
     * Checks if user has skipped battery optimization during welcome flow.
     * Follows SRP by delegating to SettingsRepository.
     */
    fun isBatteryOptimizationSkipped(): Boolean {
        return settingsManager.isBatteryOptimizationSkipped()
    }
    
    /**
     * Requests battery optimization disable.
     * Follows SRP by delegating to PermissionHelper.
     */
    fun requestBatteryOptimizationDisable() {
        permissionHelper.requestBatteryOptimizationDisable()
    }
}
