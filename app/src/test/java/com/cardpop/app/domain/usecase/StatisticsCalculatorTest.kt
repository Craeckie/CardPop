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
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [StatisticsCalculator] — the pure statistics arithmetic layer.
 *
 * No Android dependencies; all inputs are plain [FlashcardEntity] lists built
 * inline. Floating-point comparisons use a tolerance of 0.001f.
 */
class StatisticsCalculatorTest {

    private val tol = 0.001f

    private fun card(
        reps: Int = 0,
        stability: Double = 0.0,
        correctCount: Int = 0,
        incorrectCount: Int = 0,
        hardCount: Int = 0,
        easyCount: Int = 0
    ) = FlashcardEntity(
        categoryId = 0L,
        question = "q",
        answer = "a",
        reps = reps,
        stability = stability,
        correctCount = correctCount,
        incorrectCount = incorrectCount,
        hardCount = hardCount,
        easyCount = easyCount
    )

    // -------------------------------------------------------------------------
    // computeStatistics — totalCards / studiedCards / masteredCards
    // -------------------------------------------------------------------------

    @Test
    fun totalCards_is_size_of_list() {
        val stats = StatisticsCalculator.computeStatistics(
            cards = listOf(card(), card(), card()),
            streakDays = 0
        )
        assertEquals(3, stats.totalCards)
    }

    @Test
    fun studiedCards_counts_only_cards_with_reps_gt_zero() {
        val cards = listOf(card(reps = 0), card(reps = 1), card(reps = 5))
        val stats = StatisticsCalculator.computeStatistics(cards, streakDays = 0)
        assertEquals(2, stats.studiedCards) // reps=1 and reps=5
    }

    @Test
    fun masteredCards_requires_stability_gte_21_and_reps_gte_3() {
        val cards = listOf(
            card(stability = 20.9, reps = 3),   // stability too low
            card(stability = 21.0, reps = 2),   // reps too low
            card(stability = 21.0, reps = 3),   // exactly mastered
            card(stability = 30.0, reps = 5),   // mastered
            card(stability = 0.0,  reps = 0)    // new card
        )
        val stats = StatisticsCalculator.computeStatistics(cards, streakDays = 0)
        assertEquals(2, stats.masteredCards)
    }

    @Test
    fun mastered_boundary_stability_21_0_exactly_qualifies() {
        val cards = listOf(card(stability = 21.0, reps = 3))
        assertEquals(1, StatisticsCalculator.computeStatistics(cards, 0).masteredCards)
    }

    @Test
    fun mastered_boundary_stability_below_21_does_not_qualify() {
        // Use a value just below the threshold (epsilon below)
        val cards = listOf(card(stability = 20.99, reps = 3))
        assertEquals(0, StatisticsCalculator.computeStatistics(cards, 0).masteredCards)
    }

    // -------------------------------------------------------------------------
    // computeStatistics — streakDays passthrough
    // -------------------------------------------------------------------------

    @Test
    fun streakDays_is_passed_through_unchanged() {
        val stats = StatisticsCalculator.computeStatistics(emptyList(), streakDays = 7)
        assertEquals(7, stats.streakDays)
    }

    // -------------------------------------------------------------------------
    // computeStatistics — studiedPercentage divide-by-zero guard
    // -------------------------------------------------------------------------

    @Test
    fun studiedPercentage_is_zero_when_no_cards() {
        val stats = StatisticsCalculator.computeStatistics(emptyList(), streakDays = 0)
        assertEquals(0, stats.studiedPercentage)
    }

    @Test
    fun studiedPercentage_rounds_down() {
        // 1 studied out of 3 → 33%
        val cards = listOf(card(reps = 1), card(reps = 0), card(reps = 0))
        assertEquals(33, StatisticsCalculator.computeStatistics(cards, 0).studiedPercentage)
    }

    // -------------------------------------------------------------------------
    // computeStatistics — accuracy weighting
    // -------------------------------------------------------------------------

    @Test
    fun accuracy_is_zero_when_no_attempts() {
        val cards = listOf(card()) // all counters at 0
        assertEquals(0f, StatisticsCalculator.computeStatistics(cards, 0).accuracyRate, tol)
    }

    @Test
    fun accuracy_all_good_is_1_0() {
        val cards = listOf(card(correctCount = 10))
        assertEquals(1.0f, StatisticsCalculator.computeStatistics(cards, 0).accuracyRate, tol)
    }

    @Test
    fun accuracy_all_wrong_is_0_0() {
        val cards = listOf(card(incorrectCount = 5))
        assertEquals(0.0f, StatisticsCalculator.computeStatistics(cards, 0).accuracyRate, tol)
    }

    @Test
    fun accuracy_hard_counts_as_half_credit() {
        // 0 good, 0 easy, 10 hard, 0 wrong → (0 + 0 + 5) / 10 = 0.5
        val cards = listOf(card(hardCount = 10))
        assertEquals(0.5f, StatisticsCalculator.computeStatistics(cards, 0).accuracyRate, tol)
    }

    @Test
    fun accuracy_mixed_weighting() {
        // 2 good, 2 easy, 2 hard, 2 wrong → (2 + 2 + 1) / 8 = 0.625
        val cards = listOf(card(correctCount = 2, easyCount = 2, hardCount = 2, incorrectCount = 2))
        assertEquals(0.625f, StatisticsCalculator.computeStatistics(cards, 0).accuracyRate, tol)
    }

    @Test
    fun accuracy_aggregated_across_multiple_cards() {
        // card A: 3 good; card B: 3 wrong → 3/6 = 0.5
        val cards = listOf(card(correctCount = 3), card(incorrectCount = 3))
        assertEquals(0.5f, StatisticsCalculator.computeStatistics(cards, 0).accuracyRate, tol)
    }

    // -------------------------------------------------------------------------
    // computeRetention
    // -------------------------------------------------------------------------

    @Test
    fun retention_is_zero_when_no_reviews() {
        val result = StatisticsCalculator.computeRetention(emptyList())
        assertEquals(0f, result.rate, tol)
        assertEquals(0, result.totalReviews)
    }

    @Test
    fun retention_totalReviews_is_sum_of_all_ratings() {
        // 2 good + 1 easy + 1 hard + 1 wrong = 5 total
        val cards = listOf(card(correctCount = 2, easyCount = 1, hardCount = 1, incorrectCount = 1))
        assertEquals(5, StatisticsCalculator.computeRetention(cards).totalReviews)
    }

    @Test
    fun retention_rate_correct_with_mixed_ratings() {
        // remembered = 2+1+1 = 4; forgotten = 1; total = 5 → rate = 4/5 = 0.8
        val cards = listOf(card(correctCount = 2, easyCount = 1, hardCount = 1, incorrectCount = 1))
        assertEquals(0.8f, StatisticsCalculator.computeRetention(cards).rate, tol)
    }

    @Test
    fun retention_all_forgotten_is_0_0() {
        val cards = listOf(card(incorrectCount = 5))
        assertEquals(0.0f, StatisticsCalculator.computeRetention(cards).rate, tol)
    }

    @Test
    fun retention_all_remembered_is_1_0() {
        val cards = listOf(card(correctCount = 5))
        assertEquals(1.0f, StatisticsCalculator.computeRetention(cards).rate, tol)
    }
}
