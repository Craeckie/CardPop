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

package com.cardpop.app.domain.usecase

import com.cardpop.app.data.entity.FlashcardEntity
import com.cardpop.app.data.repository.FlashcardRepository
import javax.inject.Inject
import javax.inject.Singleton

data class SimpleStatistics(
    val totalCards: Int,
    val studiedCards: Int,
    val masteredCards: Int,
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

/**
 * Pure, stateless arithmetic extracted from [StatisticsUseCase] so it can be
 * unit-tested without any Android dependencies (no Context, no Repository).
 */
internal object StatisticsCalculator {

    /**
     * Computes [SimpleStatistics] from a list of cards and the current streak.
     *
     * Mastered heuristic: `stability >= 21.0 && reps >= 3` (same as StatisticsViewModel).
     * Accuracy: Good + Easy = 1.0 credit; Hard = 0.5 credit; Wrong = 0.
     */
    fun computeStatistics(cards: List<FlashcardEntity>, streakDays: Int): SimpleStatistics {
        val totalCards = cards.size
        val studiedCards = cards.count { it.reps > 0 }
        val masteredCards = cards.count { it.stability >= 21.0 && it.reps >= 3 }

        val totalGood = cards.sumOf { it.correctCount }
        val totalEasy = cards.sumOf { it.easyCount }
        val totalHard = cards.sumOf { it.hardCount }
        val totalWrong = cards.sumOf { it.incorrectCount }
        val totalAttempts = totalGood + totalEasy + totalHard + totalWrong
        val accuracyRate = if (totalAttempts > 0) {
            (totalGood + totalEasy + totalHard * 0.5f) / totalAttempts.toFloat()
        } else 0f

        return SimpleStatistics(
            totalCards = totalCards,
            studiedCards = studiedCards,
            masteredCards = masteredCards,
            accuracyRate = accuracyRate,
            streakDays = streakDays
        )
    }

    /**
     * Computes [RetentionData] from a list of cards.
     *
     * Remembered = correctCount + easyCount + hardCount; Forgotten = incorrectCount.
     */
    fun computeRetention(cards: List<FlashcardEntity>): RetentionData {
        val remembered = cards.sumOf { it.correctCount + it.easyCount + it.hardCount }
        val forgotten = cards.sumOf { it.incorrectCount }
        val total = remembered + forgotten
        val rate = if (total > 0) remembered.toFloat() / total.toFloat() else 0f
        return RetentionData(rate = rate, totalReviews = total)
    }
}

@Singleton
class StatisticsUseCase @Inject constructor(
    private val repository: FlashcardRepository,
    private val simpleStreakUseCase: SimpleStreakUseCase
) {

    suspend fun getSimpleStatistics(): Result<SimpleStatistics> {
        return try {
            val enabledFlashcards = repository.getAllFlashcards()
            val streakDays = simpleStreakUseCase.getCurrentStreakData().currentStreak
            Result.success(StatisticsCalculator.computeStatistics(enabledFlashcards, streakDays))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRetention(): RetentionData {
        val cards = repository.getAllFlashcardsForStatistics()
        return StatisticsCalculator.computeRetention(cards)
    }
}
