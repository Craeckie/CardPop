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

package com.cardpop.app.domain.fsrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the FSRS v6 port. Fuzz is disabled (enableFuzz = false) so that
 * `nextInterval` is a deterministic function of stability, which lets us assert
 * the structural invariants the FSRS spec guarantees:
 *   - Easy interval >= Good interval + 1 (when computed in days)
 *   - Hard interval <= Good interval
 *   - Difficulty stays bounded in [1, 10] across every state transition
 *   - Stability does not decrease on consecutive Good ratings without forgetting
 *
 * `numeric_pin_against_fsrs_v6_reference` pins exact values for four canonical
 * inputs computed from the FSRS-6 spec (cross-checked against py-fsrs). These
 * lock the algebra against accidental regressions in `linearDamping` and
 * `forgettingCurve` — both of which had bugs in the upstream FSRS-Kotlin port.
 */
class FsrsTest {

    private fun fsrs(): Fsrs = Fsrs(
        requestRetention = FsrsParameters.DEFAULT_RETENTION,
        params = FsrsParameters.DEFAULT,
        randomSeed = 42L,
        enableFuzz = false
    )

    private val now = 1_000_000_000_000L

    private fun gradeFor(card: FsrsCard, rating: FsrsRating): FsrsGrade =
        fsrs().calculate(card).first { it.rating == rating }

    @Test
    fun new_card_easy_goes_to_review_with_positive_stability() {
        val newCard = FsrsCard()
        val updated = fsrs().apply(newCard, FsrsRating.Easy, now)

        assertEquals(FsrsCardState.Review, updated.state)
        assertTrue("stability should be positive", updated.stability > 0.0)
        assertTrue("scheduledDays should be at least 1", updated.scheduledDays >= 1)
        assertEquals(1, updated.reps)
        assertEquals(0, updated.lapses)
        assertEquals(now, updated.lastReviewAt)
    }

    @Test
    fun new_card_again_stays_in_learning_with_short_duration() {
        val newCard = FsrsCard()
        val grade = gradeFor(newCard, FsrsRating.Again)
        val updated = fsrs().apply(newCard, FsrsRating.Again, now)

        assertEquals(FsrsCardState.Learning, updated.state)
        assertTrue(
            "Again duration on a New card should be a short fixed window (~3min)",
            grade.durationMillis in 1L..(15 * 60 * 1000L)
        )
        assertEquals(0, updated.lapses) // not a lapse: card was New, not Review
    }

    @Test
    fun review_card_good_extends_interval() {
        var card = FsrsCard()
        card = fsrs().apply(card, FsrsRating.Good, now)

        val intervals = mutableListOf(card.scheduledDays)
        // Simulate consecutive Good ratings, each occurring after the scheduled interval.
        repeat(5) {
            val elapsed = card.scheduledDays
            val sameStateInput = card.copy(elapsedDays = elapsed)
            card = fsrs().apply(sameStateInput, FsrsRating.Good, now)
            intervals += card.scheduledDays
        }

        // Each interval should be >= the previous one (monotonic non-decreasing).
        intervals.zipWithNext { a, b ->
            assertTrue("intervals should be non-decreasing: $a -> $b in $intervals", b >= a)
        }
        // And the last interval should be strictly greater than the first.
        assertTrue(
            "long-run interval should grow beyond the initial one: ${intervals.first()} -> ${intervals.last()}",
            intervals.last() > intervals.first()
        )
    }

    @Test
    fun review_card_again_triggers_relearning_and_shortens_interval() {
        // Build up a Review card with some history first.
        var card = FsrsCard()
        repeat(3) {
            card = fsrs().apply(card.copy(elapsedDays = card.scheduledDays), FsrsRating.Good, now)
        }
        assertEquals(FsrsCardState.Review, card.state)
        val priorInterval = card.scheduledDays

        val lapsed = fsrs().apply(card.copy(elapsedDays = card.scheduledDays), FsrsRating.Again, now)
        assertEquals(FsrsCardState.Relearning, lapsed.state)
        assertEquals(card.lapses + 1, lapsed.lapses)
        // Stability should drop after a lapse (nextForgetStability < previous stability).
        assertTrue(
            "stability should not increase after a lapse: ${card.stability} -> ${lapsed.stability}",
            lapsed.stability <= card.stability
        )
        // The new card's relearning grade is short-term, so the recorded scheduledDays
        // for the lapse path is computed from the forgotten stability and is much smaller
        // than the previous mature interval.
        assertTrue(
            "relearning interval should be shorter than the prior review interval ($priorInterval)",
            lapsed.scheduledDays <= priorInterval
        )
    }

    @Test
    fun relearning_card_good_returns_to_review() {
        var card = FsrsCard()
        repeat(3) {
            card = fsrs().apply(card.copy(elapsedDays = card.scheduledDays), FsrsRating.Good, now)
        }
        val lapsed = fsrs().apply(card.copy(elapsedDays = card.scheduledDays), FsrsRating.Again, now)
        assertEquals(FsrsCardState.Relearning, lapsed.state)

        val recovered = fsrs().apply(lapsed, FsrsRating.Good, now)
        assertEquals(FsrsCardState.Review, recovered.state)
        assertTrue("post-recovery stability should be positive", recovered.stability > 0.0)
    }

    @Test
    fun easy_interval_always_gte_good_interval_plus_one() {
        // Test across New, Learning, and Review states.
        val cards = listOf(
            FsrsCard(), // New
            FsrsCard(state = FsrsCardState.Learning, stability = 1.5, difficulty = 5.0),
            FsrsCard(state = FsrsCardState.Review, stability = 10.0, difficulty = 5.0, elapsedDays = 8),
            FsrsCard(state = FsrsCardState.Review, stability = 50.0, difficulty = 7.0, elapsedDays = 40),
            FsrsCard(state = FsrsCardState.Relearning, stability = 0.5, difficulty = 6.0)
        )

        for (card in cards) {
            val grades = fsrs().calculate(card)
            val good = grades.first { it.rating == FsrsRating.Good }
            val easy = grades.first { it.rating == FsrsRating.Easy }
            // For Learning/Relearning/Review, the algorithm enforces ivlEasy >= ivlGood + 1.
            // For New, ivlGood is reported as 0 (short-term, not in days) so this still holds.
            assertTrue(
                "ivlEasy(${easy.scheduledDays}) should be >= ivlGood(${good.scheduledDays}) + 1 for state ${card.state}",
                easy.scheduledDays >= good.scheduledDays + 1
            )
        }
    }

    @Test
    fun hard_interval_lte_good_interval() {
        // Hard <= Good is enforced in Review state (Learning has no Hard interval).
        val cards = listOf(
            FsrsCard(state = FsrsCardState.Review, stability = 10.0, difficulty = 5.0, elapsedDays = 8),
            FsrsCard(state = FsrsCardState.Review, stability = 50.0, difficulty = 7.0, elapsedDays = 40),
            FsrsCard(state = FsrsCardState.Review, stability = 200.0, difficulty = 3.0, elapsedDays = 180)
        )

        for (card in cards) {
            val grades = fsrs().calculate(card)
            val hard = grades.first { it.rating == FsrsRating.Hard }
            val good = grades.first { it.rating == FsrsRating.Good }
            assertTrue(
                "ivlHard(${hard.scheduledDays}) should be <= ivlGood(${good.scheduledDays}) for stability=${card.stability}",
                hard.scheduledDays <= good.scheduledDays
            )
        }
    }

    @Test
    fun difficulty_bounded_1_to_10_across_all_transitions() {
        val ratings = FsrsRating.entries.toList()
        // Walk a long random-ish sequence of ratings and verify difficulty never escapes [1, 10].
        var card = FsrsCard()
        val seq = (0 until 200).map { ratings[it % ratings.size] }
        for (rating in seq) {
            val nextElapsed = if (card.state == FsrsCardState.Review) card.scheduledDays else 0
            card = fsrs().apply(card.copy(elapsedDays = nextElapsed), rating, now)
            assertTrue(
                "difficulty out of bounds: ${card.difficulty} after rating=$rating in state=${card.state}",
                card.difficulty in 1.0..10.0
            )
        }
    }

    @Test
    fun stability_monotonic_nondecreasing_on_consecutive_good_without_forgetting() {
        // "Without forgetting" means we always review on the day the card is due,
        // i.e. elapsedDays = scheduledDays so retrievability stays at requestRetention.
        var card = FsrsCard()
        card = fsrs().apply(card, FsrsRating.Good, now) // graduate from New to Learning
        card = fsrs().apply(card.copy(elapsedDays = 0), FsrsRating.Good, now) // Learning -> Review
        assertEquals(FsrsCardState.Review, card.state)

        var prev = card.stability
        repeat(10) {
            card = fsrs().apply(card.copy(elapsedDays = card.scheduledDays), FsrsRating.Good, now)
            assertTrue(
                "stability should not decrease on Good-on-time: $prev -> ${card.stability}",
                card.stability >= prev
            )
            prev = card.stability
        }
        // After 10 successful reviews, stability should have grown meaningfully.
        assertNotEquals(card.stability, 0.0)
    }

    @Test
    fun calculate_returns_one_grade_per_rating_in_canonical_order() {
        val grades = fsrs().calculate(FsrsCard())
        assertEquals(4, grades.size)
        assertEquals(FsrsRating.Again, grades[0].rating)
        assertEquals(FsrsRating.Hard, grades[1].rating)
        assertEquals(FsrsRating.Good, grades[2].rating)
        assertEquals(FsrsRating.Easy, grades[3].rating)
    }

    @Test
    fun numeric_pin_against_fsrs_v6_reference() {
        // Reference values derived from the FSRS-6 spec with default 21 weights and
        // requestRetention = 0.9. Tolerance = 0.05 (a few round2 steps) absorbs any
        // small drift from intermediate rounding; the buggy variants we're guarding
        // against diverge by 100+ on stability and ~3 on difficulty. Fuzz disabled.
        val tol = 0.05

        // Case 1: New + Good -> Learning, D = w_4 - exp(2·w_5) + 1, S = max(w_2, 0.1)
        val newGood = fsrs().apply(FsrsCard(), FsrsRating.Good, now)
        assertEquals(FsrsCardState.Learning, newGood.state)
        assertEquals(2.12, newGood.difficulty, tol)
        assertEquals(2.31, newGood.stability, tol)
        assertEquals(0, newGood.scheduledDays)

        // Case 2: New + Easy -> Review, D coerced to 1.0, S = w_3, ivlEasy = 1
        val newEasy = fsrs().apply(FsrsCard(), FsrsRating.Easy, now)
        assertEquals(FsrsCardState.Review, newEasy.state)
        assertEquals(1.0, newEasy.difficulty, tol)
        assertEquals(8.30, newEasy.stability, tol)
        assertEquals(1, newEasy.scheduledDays)

        // Case 3: Review s=10 d=5 e=8 + Good. With FSRS-6 power-law forgetting curve,
        // R ≈ 0.9146 (the FSRS-5 exp form would give R ≈ 0.4493 and stability ≈ 156).
        val reviewCard = FsrsCard(
            state = FsrsCardState.Review,
            stability = 10.0,
            difficulty = 5.0,
            elapsedDays = 8
        )
        val reviewGood = fsrs().apply(reviewCard, FsrsRating.Good, now)
        assertEquals(FsrsCardState.Review, reviewGood.state)
        assertEquals(5.0, reviewGood.difficulty, tol) // delta=0 for Good
        assertEquals(28.71, reviewGood.stability, tol)
        assertEquals(29, reviewGood.scheduledDays)

        // Case 4: Review s=10 d=5 e=8 + Hard. The damping formula is the diagnostic:
        // correct (10-D)·delta/9 -> D' ≈ 6.67; buggy delta·(10-D/9) -> D' clamps at ~9.99.
        val reviewHard = fsrs().apply(reviewCard, FsrsRating.Hard, now)
        assertEquals(FsrsCardState.Review, reviewHard.state)
        assertEquals(6.67, reviewHard.difficulty, tol)
        assertEquals(21.25, reviewHard.stability, tol)
        assertEquals(21, reviewHard.scheduledDays)
    }

    @Test
    fun deterministic_with_fixed_seed_and_fuzz_enabled() {
        val card = FsrsCard(state = FsrsCardState.Review, stability = 50.0, difficulty = 5.0, elapsedDays = 40)
        val a = Fsrs(randomSeed = 42L, enableFuzz = true).calculate(card)
        val b = Fsrs(randomSeed = 42L, enableFuzz = true).calculate(card)
        assertEquals(a.map { it.scheduledDays }, b.map { it.scheduledDays })
    }
}
