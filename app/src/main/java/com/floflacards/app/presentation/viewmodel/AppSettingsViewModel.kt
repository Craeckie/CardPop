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

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.floflacards.app.data.repository.FlashcardRepository
import com.floflacards.app.data.repository.SettingsRepository
import com.floflacards.app.data.model.AppTheme
import com.floflacards.app.data.model.FlashcardFont
import com.floflacards.app.data.model.FlashcardTheme
import com.floflacards.app.data.model.Language
import com.floflacards.app.domain.usecase.RetentionData
import com.floflacards.app.domain.usecase.StatisticsUseCase
import com.floflacards.app.service.OverlayService
import com.floflacards.app.util.IntervalConstants
import com.floflacards.app.util.PermissionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
    @ApplicationContext private val appContext: Context,
    private val repository: FlashcardRepository,
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

    /** Current flashcard font (applies to question/answer text only). */
    val flashcardFont: StateFlow<FlashcardFont> = settingsManager.flashcardFont

    /** Display name of the user-imported font file, or null if none loaded. */
    val customFontName: StateFlow<String?> = settingsManager.customFontName

    /** Font size in sp for question text (10..40). */
    val questionFontSize: StateFlow<Float> = settingsManager.questionFontSize

    /** Font size in sp for answer text (10..40). */
    val answerFontSize: StateFlow<Float> = settingsManager.answerFontSize

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

    /** Flashcard interval in minutes (from PREDEFINED_INTERVALS). */
    val intervalMinutes: StateFlow<Int> = settingsManager.intervalMinutes

    fun setIntervalMinutes(minutes: Int) {
        if (IntervalConstants.isValidInterval(minutes)) settingsManager.setIntervalMinutes(minutes)
    }

    fun showSingleFlashcardNow() {
        viewModelScope.launch {
            if (!permissionHelper.hasOverlayPermission()) return@launch
            val flashcard = repository.getNextAvailableFlashcard()
            OverlayService.startWithFlashcard(appContext, flashcard)
        }
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

    fun setFlashcardFont(font: FlashcardFont) {
        settingsManager.setFlashcardFont(font)
    }

    fun setCustomFont(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val dest = File(appContext.filesDir, "custom_font.ttf")
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                    val name = resolveDisplayName(uri) ?: dest.name
                    settingsManager.setCustomFontName(name)
                    settingsManager.setFlashcardFont(FlashcardFont.CUSTOM)
                } catch (e: Exception) {
                    Log.e("AppSettingsViewModel", "Failed to load custom font", e)
                }
            }
        }
    }

    fun setQuestionFontSize(size: Float) = settingsManager.setQuestionFontSize(size)
    fun setAnswerFontSize(size: Float) = settingsManager.setAnswerFontSize(size)

    fun removeCustomFont() {
        File(appContext.filesDir, "custom_font.ttf").delete()
        settingsManager.setCustomFontName(null)
        settingsManager.setFlashcardFont(FlashcardFont.DEFAULT)
    }

    private fun resolveDisplayName(uri: Uri): String? =
        appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (col >= 0 && cursor.moveToFirst()) cursor.getString(col) else null
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
