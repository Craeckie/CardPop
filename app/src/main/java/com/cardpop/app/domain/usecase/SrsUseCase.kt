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

import com.cardpop.app.data.dao.ReviewLogDao
import com.cardpop.app.data.entity.FlashcardEntity
import com.cardpop.app.data.entity.ReviewLogEntity
import com.cardpop.app.data.repository.FlashcardRepository
import com.cardpop.app.data.repository.SettingsRepository
import com.cardpop.app.data.source.NewCardPreferences
import com.cardpop.app.data.source.ReviewHistoryPreferences
import com.cardpop.app.domain.fsrs.FsrsCardState
import com.cardpop.app.domain.fsrs.FsrsRating
import com.cardpop.app.domain.model.FlashcardRating
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SrsUseCase @Inject constructor(
    private val repository: FlashcardRepository,
    private val settingsManager: SettingsRepository,
    private val reviewHistory: ReviewHistoryPreferences,
    private val newCardPrefs: NewCardPreferences,
    private val reviewLogDao: ReviewLogDao
) {
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
            val now = System.currentTimeMillis()

            // NonCancellable: the caller launches this on the process-lifetime application
            // scope (so the overlay closing no longer cancels it), but we still guard the
            // whole critical section so a future cancellation can't interrupt it mid-write.
            // The re-fetch lives inside the guard too — re-fetching by id avoids stale-data
            // races (the entity arrives via Intent extras), and keeping it here makes
            // re-fetch → project → updateFlashcard → reviewLogDao.insert → recordReview one
            // atomic unit. Returns the (re-fetched) card unchanged for CLOSED (no-op).
            withContext(NonCancellable) {
                val current = repository.getFlashcardById(flashcard.id) ?: flashcard

                // Pure scheduling math lives in SrsScheduler so it can be unit-tested
                // without Android dependencies. Returns null for CLOSED (no-op).
                val updatedFlashcard = SrsScheduler.project(
                    flashcard = current,
                    rating = rating,
                    now = now,
                    requestRetention = settingsManager.getTargetRetention()
                ) ?: return@withContext current

                val fsrsRating = rating.toFsrsRating()!! // null case already handled above
                repository.updateFlashcard(updatedFlashcard)
                if (current.id > 0) {
                    reviewLogDao.insert(
                        ReviewLogEntity(
                            flashcardId = current.id,
                            reviewedAt = now,
                            rating = fsrsRating.value,
                            stateBefore = current.state
                        )
                    )
                }
                reviewHistory.recordReview(
                    masteredTotal = repository.getMasteredCount(),
                    now = now
                )
                // Count a brand-new card the moment it leaves the New state, so the
                // overlay's daily new-card cap reflects real introductions. CLOSED
                // ratings return early above and never reach here.
                if (current.id > 0 && current.state == FsrsCardState.New.value) {
                    newCardPrefs.increment(now)
                }

                updatedFlashcard
            }
        }.also { result ->
            // runCatching must not swallow CancellationException — re-throw so the
            // coroutine machinery can propagate structured cancellation correctly.
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        }
    }

    private fun FlashcardRating.toFsrsRating(): FsrsRating? = when (this) {
        FlashcardRating.WRONG -> FsrsRating.Again
        FlashcardRating.HARD -> FsrsRating.Hard
        FlashcardRating.GOOD -> FsrsRating.Good
        FlashcardRating.EASY -> FsrsRating.Easy
        FlashcardRating.CLOSED -> null
    }
}
