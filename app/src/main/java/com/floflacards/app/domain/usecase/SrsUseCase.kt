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

import com.floflacards.app.data.entity.FlashcardEntity
import com.floflacards.app.data.repository.FlashcardRepository
import com.floflacards.app.data.repository.SettingsRepository
import com.floflacards.app.data.source.ReviewHistoryPreferences
import com.floflacards.app.domain.fsrs.Fsrs
import com.floflacards.app.domain.fsrs.FsrsCard
import com.floflacards.app.domain.fsrs.FsrsCardState
import com.floflacards.app.domain.fsrs.FsrsParameters
import com.floflacards.app.domain.fsrs.FsrsRating
import com.floflacards.app.domain.model.FlashcardRating
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SrsUseCase @Inject constructor(
    private val repository: FlashcardRepository,
    private val settingsManager: SettingsRepository,
    private val reviewHistory: ReviewHistoryPreferences
) {
    private fun fsrs(): Fsrs = Fsrs(
        requestRetention = settingsManager.getTargetRetention(),
        params = FsrsParameters.DEFAULT
    )

    /**
     * Applies a user rating to a flashcard via the FSRS-6 scheduler and persists
     * the new state. CLOSED ratings (overlay dismissed without rating) leave the
     * card untouched so passive learning never accidentally promotes a card.
     */
    suspend fun updateFlashcardRating(
        flashcard: FlashcardEntity,
        rating: FlashcardRating
    ): Result<FlashcardEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val fsrsRating = rating.toFsrsRating()
                ?: return@runCatching flashcard

            val now = System.currentTimeMillis()
            val card = flashcard.toFsrsCard(now)
            val scheduler = fsrs()

            // calculate() gives us per-rating durations (3/5/10-min for short-term
            // states, day-based for Review). apply() projects the chosen grade onto
            // a new FsrsCard with state-machine transitions and rep/lapse counters.
            val grade = scheduler.calculate(card).first { it.rating == fsrsRating }
            val updatedFsrs = scheduler.apply(card, fsrsRating, now)
            val dueAt = computeDueAt(now, updatedFsrs.state, grade.durationMillis)

            val updatedFlashcard = flashcard.copy(
                stability = updatedFsrs.stability,
                difficulty = updatedFsrs.difficulty,
                scheduledDays = updatedFsrs.scheduledDays,
                reps = updatedFsrs.reps,
                lapses = updatedFsrs.lapses,
                state = updatedFsrs.state.value,
                lastReviewedAt = now,
                dueAt = dueAt,
                correctCount = if (rating == FlashcardRating.GOOD) flashcard.correctCount + 1 else flashcard.correctCount,
                incorrectCount = if (rating == FlashcardRating.WRONG) flashcard.incorrectCount + 1 else flashcard.incorrectCount,
                hardCount = if (rating == FlashcardRating.HARD) flashcard.hardCount + 1 else flashcard.hardCount,
                easyCount = if (rating == FlashcardRating.EASY) flashcard.easyCount + 1 else flashcard.easyCount,
                updatedAt = now
            )

            repository.updateFlashcard(updatedFlashcard)

            // Record this review in the daily history so the Statistics chart
            // can plot reviews-per-day and the running mastered total. Snapshot
            // is taken AFTER the update so today's mastered count reflects any
            // card that just crossed the threshold.
            reviewHistory.recordReview(
                masteredTotal = repository.getMasteredCount(),
                now = now
            )

            updatedFlashcard
        }
    }

    /**
     * For Review-state cards we schedule by FSRS days. For Learning/Relearning
     * we use the short-term duration the scheduler already computed for this
     * rating (3/5/10 min on Again/Hard/Good) — keeping a single source of truth
     * inside Fsrs.calculate() rather than scattering magic numbers.
     */
    private fun computeDueAt(now: Long, state: FsrsCardState, durationMillis: Long): Long =
        when (state) {
            FsrsCardState.Review -> now + durationMillis
            else -> now + durationMillis
        }

    private fun FlashcardRating.toFsrsRating(): FsrsRating? = when (this) {
        FlashcardRating.WRONG -> FsrsRating.Again
        FlashcardRating.HARD -> FsrsRating.Hard
        FlashcardRating.GOOD -> FsrsRating.Good
        FlashcardRating.EASY -> FsrsRating.Easy
        FlashcardRating.CLOSED -> null
    }

    private fun FlashcardEntity.toFsrsCard(now: Long): FsrsCard {
        val elapsedDays = if (lastReviewedAt == 0L) 0
        else ((now - lastReviewedAt) / DAY_MS).toInt().coerceAtLeast(0)
        return FsrsCard(
            stability = stability,
            difficulty = difficulty,
            scheduledDays = scheduledDays,
            elapsedDays = elapsedDays,
            reps = reps,
            lapses = lapses,
            state = FsrsCardState.fromValue(state),
            lastReviewAt = lastReviewedAt
        )
    }

    private companion object {
        const val DAY_MS: Long = 24L * 60 * 60 * 1000
    }
}
