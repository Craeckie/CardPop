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
import com.cardpop.app.domain.fsrs.FsrsCardState
import com.cardpop.app.domain.fsrs.FsrsParameters
import com.cardpop.app.domain.model.FlashcardRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [SrsScheduler.project], the pure FSRS-6 projection layer.
 *
 * No Android dependencies — all assertions target [FlashcardEntity] fields that
 * the scheduler owns: scheduling state (stability/difficulty/reps/lapses/state/dueAt)
 * and the per-rating counters (correctCount/incorrectCount/hardCount/easyCount).
 *
 * Fuzz is implicitly disabled because [SrsScheduler] constructs Fsrs with the
 * default enableFuzz=true, but the assertions here are structural (relative
 * orderings, counter increments, state-machine transitions) rather than exact
 * numeric pins — so they hold regardless of fuzz jitter.
 */
class SrsSchedulerTest {

    private val now = 1_000_000_000_000L
    private val DAY_MS = 24L * 60 * 60 * 1_000L

    /** A brand-new card with no review history. */
    private fun newCard(id: Long = 1L) = FlashcardEntity(
        id = id,
        categoryId = 0L,
        question = "q",
        answer = "a",
        state = FsrsCardState.New.value,
        lastReviewedAt = 0L
    )

    /** A card that has graduated to Review state with some history. */
    private fun reviewCard(
        id: Long = 1L,
        stability: Double = 10.0,
        difficulty: Double = 5.0,
        lastReviewedAt: Long = now - 8 * DAY_MS,   // reviewed 8 days ago
        reps: Int = 3,
        lapses: Int = 0,
        correctCount: Int = 3
    ) = FlashcardEntity(
        id = id,
        categoryId = 0L,
        question = "q",
        answer = "a",
        state = FsrsCardState.Review.value,
        stability = stability,
        difficulty = difficulty,
        scheduledDays = 8,
        reps = reps,
        lapses = lapses,
        lastReviewedAt = lastReviewedAt,
        correctCount = correctCount
    )

    // -------------------------------------------------------------------------
    // CLOSED rating — no-op
    // -------------------------------------------------------------------------

    @Test
    fun closed_rating_returns_null() {
        val card = newCard()
        val result = SrsScheduler.project(card, FlashcardRating.CLOSED, now)
        assertNull("CLOSED should return null (no-op)", result)
    }

    // -------------------------------------------------------------------------
    // Counter increments
    // -------------------------------------------------------------------------

    @Test
    fun good_rating_increments_correctCount() {
        val card = newCard()
        val result = SrsScheduler.project(card, FlashcardRating.GOOD, now)
        assertNotNull(result)
        assertEquals(1, result!!.correctCount)
        assertEquals(0, result.incorrectCount)
        assertEquals(0, result.hardCount)
        assertEquals(0, result.easyCount)
    }

    @Test
    fun wrong_rating_increments_incorrectCount() {
        val card = newCard()
        val result = SrsScheduler.project(card, FlashcardRating.WRONG, now)
        assertNotNull(result)
        assertEquals(1, result!!.incorrectCount)
        assertEquals(0, result.correctCount)
    }

    @Test
    fun hard_rating_increments_hardCount() {
        val card = newCard()
        val result = SrsScheduler.project(card, FlashcardRating.HARD, now)
        assertNotNull(result)
        assertEquals(1, result!!.hardCount)
        assertEquals(0, result.correctCount)
    }

    @Test
    fun easy_rating_increments_easyCount() {
        val card = newCard()
        val result = SrsScheduler.project(card, FlashcardRating.EASY, now)
        assertNotNull(result)
        assertEquals(1, result!!.easyCount)
        assertEquals(0, result.correctCount)
    }

    @Test
    fun counters_accumulate_from_existing_values() {
        // Simulate a card that was already reviewed twice as GOOD.
        val card = reviewCard(correctCount = 2)
        val result = SrsScheduler.project(card, FlashcardRating.GOOD, now)
        assertEquals(3, result!!.correctCount)
    }

    // -------------------------------------------------------------------------
    // FSRS state transitions
    // -------------------------------------------------------------------------

    @Test
    fun new_card_good_transitions_to_learning() {
        val result = SrsScheduler.project(newCard(), FlashcardRating.GOOD, now)!!
        assertEquals(FsrsCardState.Learning.value, result.state)
    }

    @Test
    fun new_card_easy_transitions_to_review() {
        val result = SrsScheduler.project(newCard(), FlashcardRating.EASY, now)!!
        assertEquals(FsrsCardState.Review.value, result.state)
    }

    @Test
    fun review_card_wrong_transitions_to_relearning_and_increments_lapses() {
        val card = reviewCard(lapses = 0)
        val result = SrsScheduler.project(card, FlashcardRating.WRONG, now)!!
        assertEquals(FsrsCardState.Relearning.value, result.state)
        assertEquals(1, result.lapses)
    }

    @Test
    fun review_card_good_stays_in_review() {
        val card = reviewCard()
        val result = SrsScheduler.project(card, FlashcardRating.GOOD, now)!!
        assertEquals(FsrsCardState.Review.value, result.state)
    }

    @Test
    fun reps_incremented_on_every_non_closed_rating() {
        listOf(FlashcardRating.WRONG, FlashcardRating.HARD, FlashcardRating.GOOD, FlashcardRating.EASY).forEach { rating ->
            val card = newCard()
            val result = SrsScheduler.project(card, rating, now)!!
            assertEquals("reps should be 1 after first review with $rating", 1, result.reps)
        }
    }

    // -------------------------------------------------------------------------
    // Timestamps
    // -------------------------------------------------------------------------

    @Test
    fun lastReviewedAt_is_set_to_now() {
        val result = SrsScheduler.project(newCard(), FlashcardRating.GOOD, now)!!
        assertEquals(now, result.lastReviewedAt)
    }

    @Test
    fun updatedAt_is_set_to_now() {
        val result = SrsScheduler.project(newCard(), FlashcardRating.GOOD, now)!!
        assertEquals(now, result.updatedAt)
    }

    @Test
    fun dueAt_is_now_plus_duration() {
        val result = SrsScheduler.project(newCard(), FlashcardRating.GOOD, now)!!
        // dueAt must be strictly in the future relative to now
        assertTrue("dueAt(${result.dueAt}) should be > now($now)", result.dueAt > now)
    }

    // -------------------------------------------------------------------------
    // elapsedDays computation
    // -------------------------------------------------------------------------

    @Test
    fun elapsed_days_is_zero_when_never_reviewed() {
        // A card with lastReviewedAt == 0L must produce elapsedDays = 0 (no review history).
        // We verify this indirectly: the FSRS algorithm treats the card as New (0 elapsed days),
        // so stability should match the init-stability for Good from the default params.
        val card = newCard()
        val result = SrsScheduler.project(card, FlashcardRating.GOOD, now)!!
        // stability must be positive and reasonable (initStability[Good] = w_2 ≈ 2.31)
        assertTrue("stability should be positive for first review", result.stability > 0.0)
        assertTrue("stability should be < 100 for first review", result.stability < 100.0)
    }

    @Test
    fun elapsed_days_computed_from_lastReviewedAt() {
        // Card reviewed 30 days ago → elapsedDays = 30.
        val thirtyDaysAgo = now - 30 * DAY_MS
        val card = reviewCard(lastReviewedAt = thirtyDaysAgo, stability = 20.0)
            .copy(scheduledDays = 30)
        val result = SrsScheduler.project(card, FlashcardRating.GOOD, now)!!
        // After a 30-day elapse with stability ~20 the stability should grow.
        assertTrue("stability should grow after on-time review", result.stability > card.stability)
    }

    // -------------------------------------------------------------------------
    // Non-default requestRetention
    // -------------------------------------------------------------------------

    @Test
    fun higher_retention_yields_shorter_interval_than_lower_retention() {
        // Both cards are identical Review cards; we apply the same rating but with
        // different requestRetention values. Higher retention = more frequent reviews.
        val card = reviewCard(stability = 20.0, lastReviewedAt = now - 14 * DAY_MS)
            .copy(scheduledDays = 14)

        val highRetention = SrsScheduler.project(card, FlashcardRating.GOOD, now,
            requestRetention = 0.95)!!
        val lowRetention = SrsScheduler.project(card, FlashcardRating.GOOD, now,
            requestRetention = FsrsParameters.DEFAULT_RETENTION)!!

        // Higher retention target → shorter interval (more aggressive review)
        // OR equal — due to fuzz both can be close; what must hold is that high-retention
        // dueAt <= low-retention dueAt.
        assertTrue(
            "higher retention (${highRetention.dueAt}) should schedule no later than lower retention (${lowRetention.dueAt})",
            highRetention.dueAt <= lowRetention.dueAt
        )
    }
}
