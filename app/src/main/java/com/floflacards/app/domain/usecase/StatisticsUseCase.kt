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

package com.floflacards.app.domain.usecase

import com.floflacards.app.data.repository.FlashcardRepository
import javax.inject.Inject
import javax.inject.Singleton

data class SimpleStatistics(
    val totalCards: Int,
    val studiedCards: Int,
    val accuracyRate: Float,
    val streakDays: Int
) {
    val studiedPercentage: Int = if (totalCards > 0) (studiedCards * 100) / totalCards else 0
}

/**
 * Aggregate retention computed from per-rating counters across all cards.
 * Anki-style: a rating counts as "remembered" iff it was ≥ Hard (i.e., not
 * Wrong/Again). This is the metric directly comparable to the FSRS target
 * retention slider in app settings.
 *
 * `totalReviews` lets the UI suppress the readout when there isn't enough data
 * to be meaningful (e.g., < 10 reviews).
 */
data class RetentionData(
    val rate: Float,
    val totalReviews: Int
)

// StreakCalculator object removed - replaced with SimpleStreakUseCase for better UX
// Old complex historical calculation replaced with simple, predictable streak tracking

@Singleton
class StatisticsUseCase @Inject constructor(
    private val repository: FlashcardRepository,
    private val simpleStreakUseCase: SimpleStreakUseCase
) {
    
    suspend fun getSimpleStatistics(): Result<SimpleStatistics> {
        return try {
            // Get enabled flashcards for regular statistics (total cards, studied cards, etc.)
            val enabledFlashcards = repository.getAllFlashcards()
            
            val totalCards = enabledFlashcards.size
            val studiedCards = enabledFlashcards.count { it.reps > 0 }
            
            // Treat Good + Easy as correct; Hard counts as half-credit; Wrong is zero.
            // Same weighting as the per-card success rate in StatisticsViewModel.
            val totalGood = enabledFlashcards.sumOf { it.correctCount }
            val totalEasy = enabledFlashcards.sumOf { it.easyCount }
            val totalHard = enabledFlashcards.sumOf { it.hardCount }
            val totalWrong = enabledFlashcards.sumOf { it.incorrectCount }
            val totalAttempts = totalGood + totalEasy + totalHard + totalWrong
            val accuracyRate = if (totalAttempts > 0) {
                (totalGood + totalEasy + totalHard * 0.5f) / totalAttempts.toFloat()
            } else 0f
            
            // Use new simple streak system instead of complex historical calculation
            val currentStreakData = simpleStreakUseCase.getCurrentStreakData()
            val streakDays = currentStreakData.currentStreak
            
            val stats = SimpleStatistics(
                totalCards = totalCards,
                studiedCards = studiedCards,
                accuracyRate = accuracyRate,
                streakDays = streakDays
            )
            
            Result.success(stats)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRetention(): RetentionData {
        val cards = repository.getAllFlashcardsForStatistics()
        val remembered = cards.sumOf { it.correctCount + it.easyCount + it.hardCount }
        val forgotten = cards.sumOf { it.incorrectCount }
        val total = remembered + forgotten
        val rate = if (total > 0) remembered.toFloat() / total.toFloat() else 0f
        return RetentionData(rate = rate, totalReviews = total)
    }
}
