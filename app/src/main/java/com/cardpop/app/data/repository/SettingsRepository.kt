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

package com.cardpop.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.cardpop.app.data.model.AppTheme
import com.cardpop.app.data.model.FlashcardFont
import com.cardpop.app.data.model.FlashcardTheme
import com.cardpop.app.data.model.Language
import com.cardpop.app.data.source.FlashcardUiPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.cardpop.app.util.IntervalConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val flashcardUiPreferences: FlashcardUiPreferences
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "floating_learning_settings",
        Context.MODE_PRIVATE
    )
    
    private val _intervalMinutes = MutableStateFlow(getIntervalMinutes())
    val intervalMinutes: StateFlow<Int> = _intervalMinutes.asStateFlow()
    
    // Learning state tracking to prevent OverlayService from restarting timer when stopped
    private val _isLearningActive = MutableStateFlow(getIsLearningActive())
    val isLearningActive: StateFlow<Boolean> = _isLearningActive.asStateFlow()
    
    // Theme preference tracking - app theme is independent from device theme
    private val _appTheme = MutableStateFlow(getAppTheme())
    val appTheme: StateFlow<AppTheme> = _appTheme.asStateFlow()
    
    // Flashcard theme preference tracking - independent from both app and device theme
    private val _flashcardTheme = MutableStateFlow(getFlashcardTheme())
    val flashcardTheme: StateFlow<FlashcardTheme> = _flashcardTheme.asStateFlow()

    // Flashcard font preference tracking - applies to question/answer text only
    private val _flashcardFont = MutableStateFlow(getFlashcardFont())
    val flashcardFont: StateFlow<FlashcardFont> = _flashcardFont.asStateFlow()

    // Display name of the user-imported custom font file (null = no custom font loaded)
    private val _customFontName = MutableStateFlow(prefs.getString(KEY_CUSTOM_FONT_NAME, null))
    val customFontName: StateFlow<String?> = _customFontName.asStateFlow()

    // Font size in sp for question text (10..40, default 17)
    private val _questionFontSize = MutableStateFlow(
        prefs.getFloat(KEY_QUESTION_FONT_SIZE, DEFAULT_FONT_SIZE)
    )
    val questionFontSize: StateFlow<Float> = _questionFontSize.asStateFlow()

    // Font size in sp for answer text (10..40, default 17)
    private val _answerFontSize = MutableStateFlow(
        prefs.getFloat(KEY_ANSWER_FONT_SIZE, DEFAULT_FONT_SIZE)
    )
    val answerFontSize: StateFlow<Float> = _answerFontSize.asStateFlow()
    
    // App locale preference tracking - allows user to override system locale
    private val _appLocale = MutableStateFlow(getAppLocale())
    val appLocale: StateFlow<Language> = _appLocale.asStateFlow()

    // FSRS target retention — observed by the settings slider
    private val _targetRetention = MutableStateFlow(getTargetRetention())
    val targetRetention: StateFlow<Double> = _targetRetention.asStateFlow()

    // App blocklist — overlay is suppressed while any of these packages is foreground
    private val _blocklist = MutableStateFlow(getBlocklist())
    val blocklist: StateFlow<Set<String>> = _blocklist.asStateFlow()

    // Flashcard overlay opacity (0.1f..1.0f). Persisted in FlashcardUiPreferences;
    // proxied here so the settings screen can observe/edit it the same way as
    // other app-wide preferences.
    private val _flashcardOpacity = MutableStateFlow(flashcardUiPreferences.getCurrentOpacity())
    val flashcardOpacity: StateFlow<Float> = _flashcardOpacity.asStateFlow()

    // Snooze duration in minutes — observed by the settings slider
    private val _snoozeDurationMinutes = MutableStateFlow(getSnoozeDurationMinutes())
    val snoozeDurationMinutes: StateFlow<Int> = _snoozeDurationMinutes.asStateFlow()

    // Epoch ms when the current snooze ends; 0 if not snoozing.
    private val _pausedUntilMs = MutableStateFlow(getPausedUntil())
    val pausedUntilMs: StateFlow<Long> = _pausedUntilMs.asStateFlow()

    companion object {
        private const val KEY_INTERVAL_MINUTES = "interval_minutes"
        private const val KEY_IS_LEARNING_ACTIVE = "is_learning_active"
        private const val KEY_HAS_SHOWN_FIRST_DEMO = "has_shown_first_demo"
        private const val KEY_IS_DEMO_RUNNING = "is_demo_running"
        private const val KEY_APP_THEME = "app_theme"
        private const val KEY_FLASHCARD_THEME = "flashcard_theme"
        private const val KEY_FLASHCARD_FONT = "flashcard_font"
        private const val KEY_CUSTOM_FONT_NAME = "custom_font_name"
        private const val KEY_QUESTION_FONT_SIZE = "question_font_size"
        private const val KEY_ANSWER_FONT_SIZE = "answer_font_size"
        private const val DEFAULT_FONT_SIZE = 17f
        private const val KEY_BATTERY_OPTIMIZATION_SKIPPED = "battery_optimization_skipped"
        private const val KEY_BATTERY_OPTIMIZATION_EVER_DISABLED = "battery_optimization_ever_disabled"
        private const val KEY_APP_LOCALE = "app_locale"
        private const val KEY_TARGET_RETENTION = "target_retention"
        private const val KEY_BLOCKLIST = "blocklist_packages"
        private const val KEY_SNOOZE_DURATION_MINUTES = "snooze_duration_minutes"
        private const val KEY_PAUSED_UNTIL = "paused_until"
        private const val DEFAULT_SNOOZE_DURATION_MINUTES = 30
        private const val DEFAULT_TARGET_RETENTION = 0.9f
        private const val MIN_TARGET_RETENTION = 0.80f
        private const val MAX_TARGET_RETENTION = 0.95f
    }

    fun getTargetRetention(): Double =
        prefs.getFloat(KEY_TARGET_RETENTION, DEFAULT_TARGET_RETENTION)
            .coerceIn(MIN_TARGET_RETENTION, MAX_TARGET_RETENTION)
            .toDouble()

    fun setTargetRetention(value: Double) {
        val clamped = value.toFloat().coerceIn(MIN_TARGET_RETENTION, MAX_TARGET_RETENTION)
        prefs.edit()
            .putFloat(KEY_TARGET_RETENTION, clamped)
            .apply()
        _targetRetention.value = clamped.toDouble()
    }

    fun getIntervalMinutes(): Int {
        return prefs.getInt(KEY_INTERVAL_MINUTES, IntervalConstants.DEFAULT_INTERVAL_MINUTES)
    }
    
    fun setIntervalMinutes(minutes: Int) {
        prefs.edit()
            .putInt(KEY_INTERVAL_MINUTES, minutes)
            .apply()
        _intervalMinutes.value = minutes
    }
    
    fun getIsLearningActive(): Boolean {
        return prefs.getBoolean(KEY_IS_LEARNING_ACTIVE, false)
    }
    
    fun setLearningActive(isActive: Boolean) {
        prefs.edit()
            .putBoolean(KEY_IS_LEARNING_ACTIVE, isActive)
            .apply()
        _isLearningActive.value = isActive
    }
    
    /**
     * Checks if the first-time demo has been shown.
     * Follows SRP by handling only demo state tracking.
     */
    fun hasShownFirstDemo(): Boolean {
        return prefs.getBoolean(KEY_HAS_SHOWN_FIRST_DEMO, false)
    }
    
    /**
     * Marks the first-time demo as shown.
     * Follows SRP by handling only demo state persistence.
     */
    fun setFirstDemoShown() {
        prefs.edit()
            .putBoolean(KEY_HAS_SHOWN_FIRST_DEMO, true)
            .apply()
    }
    
    /**
     * Checks if demo is currently running.
     * Used to prevent timer conflicts during demo.
     */
    fun isDemoRunning(): Boolean {
        return prefs.getBoolean(KEY_IS_DEMO_RUNNING, false)
    }
    
    /**
     * Sets the demo running state.
     * Used to isolate demo from timer system.
     */
    fun setDemoRunning(isRunning: Boolean) {
        prefs.edit()
            .putBoolean(KEY_IS_DEMO_RUNNING, isRunning)
            .apply()
    }
    
    /**
     * Gets the current app theme preference.
     * CRITICAL: App theme is independent from device theme.
     * Follows SRP by handling only theme preference retrieval.
     */
    fun getAppTheme(): AppTheme {
        val themeString = prefs.getString(KEY_APP_THEME, AppTheme.DEFAULT.name)
        return AppTheme.fromString(themeString ?: AppTheme.DEFAULT.name)
    }
    
    /**
     * Sets the app theme preference.
     * CRITICAL: This controls the app theme independently from device theme.
     * Follows SRP by handling only theme preference persistence.
     */
    fun setAppTheme(theme: AppTheme) {
        prefs.edit()
            .putString(KEY_APP_THEME, theme.name)
            .apply()
        _appTheme.value = theme
    }
    
    /**
     * Checks if user has skipped battery optimization during welcome flow.
     * Follows SRP by handling only battery optimization skip state retrieval.
     */
    fun isBatteryOptimizationSkipped(): Boolean {
        return prefs.getBoolean(KEY_BATTERY_OPTIMIZATION_SKIPPED, false)
    }
    
    /**
     * Sets the battery optimization skip preference.
     * Used when user chooses to skip battery optimization in welcome screen.
     * Follows SRP by handling only battery optimization skip state persistence.
     */
    fun setBatteryOptimizationSkipped(isSkipped: Boolean) {
        prefs.edit()
            .putBoolean(KEY_BATTERY_OPTIMIZATION_SKIPPED, isSkipped)
            .apply()
    }
    
    /**
     * Checks if user has ever successfully disabled battery optimization.
     * Different from skip - indicates user wants the feature but may need help if re-enabled.
     * Follows SRP by handling only battery optimization disable tracking.
     */
    fun hasBatteryOptimizationEverBeenDisabled(): Boolean {
        return prefs.getBoolean(KEY_BATTERY_OPTIMIZATION_EVER_DISABLED, false)
    }
    
    /**
     * Marks that user has successfully disabled battery optimization.
     * Used to track user intent for contextual help vs welcome screen.
     * Follows SRP by handling only battery optimization disable state persistence.
     */
    fun setBatteryOptimizationEverDisabled(everDisabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_BATTERY_OPTIMIZATION_EVER_DISABLED, everDisabled)
            .apply()
    }
    
    /**
     * Gets the current flashcard theme preference.
     * CRITICAL: Flashcard theme is independent from both app theme and device theme.
     * Follows SRP by handling only flashcard theme preference retrieval.
     */
    fun getFlashcardTheme(): FlashcardTheme {
        val themeString = prefs.getString(KEY_FLASHCARD_THEME, FlashcardTheme.DEFAULT_THEME.name)
        return FlashcardTheme.fromString(themeString ?: FlashcardTheme.DEFAULT_THEME.name)
    }
    
    /**
     * Sets the flashcard theme preference.
     * CRITICAL: This controls the flashcard theme independently from app and device theme.
     * Follows SRP by handling only flashcard theme preference persistence.
     */
    fun setFlashcardTheme(theme: FlashcardTheme) {
        prefs.edit()
            .putString(KEY_FLASHCARD_THEME, theme.name)
            .apply()
        _flashcardTheme.value = theme
    }

    fun getFlashcardFont(): FlashcardFont {
        val fontString = prefs.getString(KEY_FLASHCARD_FONT, FlashcardFont.DEFAULT_FONT.name)
        return FlashcardFont.fromString(fontString ?: FlashcardFont.DEFAULT_FONT.name)
    }

    fun setFlashcardFont(font: FlashcardFont) {
        prefs.edit()
            .putString(KEY_FLASHCARD_FONT, font.name)
            .apply()
        _flashcardFont.value = font
    }

    fun setCustomFontName(name: String?) {
        prefs.edit()
            .putString(KEY_CUSTOM_FONT_NAME, name)
            .apply()
        _customFontName.value = name
    }

    fun setQuestionFontSize(size: Float) {
        prefs.edit().putFloat(KEY_QUESTION_FONT_SIZE, size).apply()
        _questionFontSize.value = size
    }

    fun setAnswerFontSize(size: Float) {
        prefs.edit().putFloat(KEY_ANSWER_FONT_SIZE, size).apply()
        _answerFontSize.value = size
    }

    /**
     * Gets the current app locale preference.
     * Returns SYSTEM by default to respect user's device locale.
     * Follows SRP by handling only locale preference retrieval.
     */
    fun getAppLocale(): Language {
        val localeCode = prefs.getString(KEY_APP_LOCALE, Language.SYSTEM.code)
        return Language.fromCode(localeCode ?: Language.SYSTEM.code)
    }
    
    /**
     * Sets the app locale preference and applies it immediately.
     * CRITICAL: This overrides the system locale for the entire app.
     * Follows SRP by handling only locale preference persistence and application.
     */
    fun setAppLocale(language: Language) {
        prefs.edit()
            .putString(KEY_APP_LOCALE, language.code)
            .apply()
        _appLocale.value = language
        
        // Apply locale immediately using AppCompatDelegate
        val localeTag = Language.toLanguageTag(language.code)
        val localeList = if (localeTag.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList() // Use system default
        } else {
            LocaleListCompat.forLanguageTags(localeTag)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    fun getBlocklist(): Set<String> {
        // getStringSet returns a reference to the internal collection — copy so
        // later mutations don't corrupt SharedPreferences state.
        return prefs.getStringSet(KEY_BLOCKLIST, emptySet())?.toSet() ?: emptySet()
    }

    fun setBlocklist(packages: Set<String>) {
        prefs.edit()
            .putStringSet(KEY_BLOCKLIST, packages)
            .apply()
        _blocklist.value = packages
    }

    fun addToBlocklist(packageName: String) {
        setBlocklist(getBlocklist() + packageName)
    }

    fun removeFromBlocklist(packageName: String) {
        setBlocklist(getBlocklist() - packageName)
    }

    fun setFlashcardOpacity(opacity: Float) {
        flashcardUiPreferences.saveOpacity(opacity)
        _flashcardOpacity.value = flashcardUiPreferences.getCurrentOpacity()
    }

    fun getSnoozeDurationMinutes(): Int {
        return prefs.getInt(KEY_SNOOZE_DURATION_MINUTES, DEFAULT_SNOOZE_DURATION_MINUTES)
    }

    fun setSnoozeDurationMinutes(minutes: Int) {
        prefs.edit()
            .putInt(KEY_SNOOZE_DURATION_MINUTES, minutes)
            .apply()
        _snoozeDurationMinutes.value = minutes
    }

    fun getPausedUntil(): Long {
        return prefs.getLong(KEY_PAUSED_UNTIL, 0L)
    }

    fun setPausedUntil(epochMs: Long) {
        prefs.edit()
            .putLong(KEY_PAUSED_UNTIL, epochMs)
            .apply()
        _pausedUntilMs.value = epochMs
    }
}
