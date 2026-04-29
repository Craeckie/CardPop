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

package com.floflacards.app.data.backup

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Main backup data structure containing all app data.
 * Uses JSON serialization with UUIDs for data integrity.
 */
@Serializable
data class BackupData(
    /**
     * v1: SM-2 fields (easinessFactor, reviewCount, cooldownUntil).
     * v2: FSRS fields (stability, difficulty, scheduledDays, reps, lapses, state, dueAt).
     * v3: app settings, flashcard overlay UI prefs, daily review history.
     * v1 backups are restored by mapping legacy fields to FSRS-New defaults.
     * v1/v2 backups deserialize cleanly because every v3 field is nullable.
     */
    val version: Int = 3,
    val backupId: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val categories: List<CategoryBackup>,
    val flashcards: List<FlashcardBackup>,
    val streakData: StreakBackup? = null, // Optional for backward compatibility
    val settings: SettingsBackup? = null, // v3+
    val flashcardUi: FlashcardUiBackup? = null, // v3+
    val reviewHistory: List<ReviewHistoryEntryBackup>? = null, // v3+
    val metadata: BackupMetadata
)

/**
 * Category backup data with UUID.
 */
@Serializable
data class CategoryBackup(
    val id: Long,
    val uuid: String = UUID.randomUUID().toString(),
    val name: String,
    val isEnabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Flashcard backup data with UUID and complete statistics.
 */
@Serializable
data class FlashcardBackup(
    val id: Long,
    val uuid: String = UUID.randomUUID().toString(),
    val categoryId: Long,
    val categoryUuid: String, // Reference to category UUID
    val question: String,
    val answer: String,
    val questionImagePath: String? = null, // Path to question image (nullable)
    val answerImagePath: String? = null, // Path to answer image (nullable)
    val isEnabled: Boolean,
    val correctCount: Int,
    val incorrectCount: Int,
    val hardCount: Int,
    val easyCount: Int = 0,
    // FSRS scheduling state (v2+). Defaults match FlashcardEntity FSRS-New defaults
    // so v1 backups deserialize cleanly — restore is then equivalent to "treat every
    // legacy card as a New card for FSRS to re-learn."
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val scheduledDays: Int = 0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val state: Int = 0,
    val lastReviewedAt: Long = 0,
    val dueAt: Long = 0,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Streak backup data for preserving user progress.
 */
@Serializable
data class StreakBackup(
    val currentStreak: Int,
    val highestStreak: Int,
    val lastActivityTimestamp: Long
)

/**
 * App settings backup (v3+). Only user-facing preferences that survive a
 * reinstall — transient runtime flags (learning-active, demo-running,
 * paused-until) and device-specific welcome flags (battery-optimization)
 * are intentionally omitted.
 */
@Serializable
data class SettingsBackup(
    val intervalMinutes: Int,
    val appTheme: String,
    val flashcardTheme: String,
    val appLocale: String,
    val targetRetention: Double,
    val blocklist: List<String> = emptyList(),
    val snoozeDurationMinutes: Int
)

/**
 * Overlay window position/size/opacity (v3+). Stored as percentages of screen
 * dimensions so the layout adapts when restoring onto a device with a
 * different screen size.
 */
@Serializable
data class FlashcardUiBackup(
    val positionXPercent: Float,
    val positionYPercent: Float,
    val widthPercent: Float,
    val heightPercent: Float,
    val opacity: Float
)

/**
 * One day's reviewed/mastered snapshot (v3+). `timestamp` is intentionally
 * omitted — it's reconstructed from `dateKey` in the device's local timezone
 * on restore.
 */
@Serializable
data class ReviewHistoryEntryBackup(
    val dateKey: String, // "yyyy-MM-dd"
    val reviews: Int,
    val masteredTotal: Int
)

/**
 * Backup metadata for validation and statistics.
 */
@Serializable
data class BackupMetadata(
    val appVersion: String = "1.0.0",
    val totalCategories: Int,
    val totalFlashcards: Int,
    val totalReviews: Int,
    val deviceInfo: String = android.os.Build.MODEL,
    val backupSource: String = "automatic"
)

/**
 * Result of backup restore operation.
 */
data class RestoreResult(
    val success: Boolean,
    val categoriesRestored: Int = 0,
    val flashcardsRestored: Int = 0,
    val error: String? = null
)

/**
 * Backup file information for UI display.
 */
data class BackupInfo(
    val exists: Boolean,
    val filePath: String,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val categoryCount: Int = 0,
    val flashcardCount: Int = 0,
    val totalReviews: Int = 0,
    val fileSize: Long = 0
)
