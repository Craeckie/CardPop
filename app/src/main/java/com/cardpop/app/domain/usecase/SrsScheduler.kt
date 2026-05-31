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
import com.cardpop.app.domain.fsrs.Fsrs
import com.cardpop.app.domain.fsrs.FsrsCard
import com.cardpop.app.domain.fsrs.FsrsCardState
import com.cardpop.app.domain.fsrs.FsrsParameters
import com.cardpop.app.domain.fsrs.FsrsRating
import com.cardpop.app.domain.model.FlashcardRating

/**
 * Pure, stateless projection of the FSRS-6 scheduler onto a [FlashcardEntity].
 *
 * This object contains all the scheduling math extracted from [SrsUseCase] so
 * that it can be unit-tested without any Android dependencies.  The use case
 * still owns the side-effecting persistence (DB write + backup + review history).
 *
 * Returns `null` for [FlashcardRating.CLOSED] — a no-op that leaves the card
 * untouched, matching passive-learning semantics.
 */
internal object SrsScheduler {

    private const val DAY_MS: Long = 24L * 60 * 60 * 1000

    /**
     * Projects [rating] onto [flashcard] at timestamp [now] using [requestRetention].
     *
     * @return the updated [FlashcardEntity], or `null` if [rating] is [FlashcardRating.CLOSED].
     */
    fun project(
        flashcard: FlashcardEntity,
        rating: FlashcardRating,
        now: Long,
        requestRetention: Double = FsrsParameters.DEFAULT_RETENTION
    ): FlashcardEntity? {
        val fsrsRating = rating.toFsrsRating() ?: return null

        val card = flashcard.toFsrsCard(now)
        val scheduler = Fsrs(requestRetention = requestRetention, params = FsrsParameters.DEFAULT)

        val grade = scheduler.calculate(card).first { it.rating == fsrsRating }
        val updatedFsrs = scheduler.apply(card, fsrsRating, now)
        val dueAt = now + grade.durationMillis

        return flashcard.copy(
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
}
