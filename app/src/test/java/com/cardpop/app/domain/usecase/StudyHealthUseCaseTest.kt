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

import com.cardpop.app.domain.model.StudyHealthStatus
import com.cardpop.app.domain.model.StudyTip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StudyHealthUseCaseTest {

    private lateinit var useCase: StudyHealthUseCase

    @Before
    fun setUp() {
        useCase = StudyHealthUseCase()
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    /**
     * A healthy baseline input with no issues. Individual tests override specific
     * fields to trigger the condition under test.
     */
    private fun healthyInput(
        activeTotal: Int = 100,
        newCount: Int = 10,
        youngCount: Int = 20,
        matureCount: Int = 70,
        dueNowCount: Int = 5,
        totalReviews: Int = 500,
        recallRate: Float = 0.92f,
        targetRetention: Double = 0.90,
        leechCount: Int = 0,
        reviewCardCount: Int = 70,
        hardDifficultyCount: Int = 10,
        lowStabilityReviewCount: Int = 10,
        cardsAddedLast7Days: Int = 5,
        zeroReviewDaysLast7: Int = 0
    ) = StudyHealthUseCase.Input(
        activeTotal = activeTotal,
        newCount = newCount,
        youngCount = youngCount,
        matureCount = matureCount,
        dueNowCount = dueNowCount,
        totalReviews = totalReviews,
        recallRate = recallRate,
        targetRetention = targetRetention,
        leechCount = leechCount,
        reviewCardCount = reviewCardCount,
        hardDifficultyCount = hardDifficultyCount,
        lowStabilityReviewCount = lowStabilityReviewCount,
        cardsAddedLast7Days = cardsAddedLast7Days,
        zeroReviewDaysLast7 = zeroReviewDaysLast7
    )

    // ── Edge: insufficient data ────────────────────────────────────────────────

    @Test
    fun `insufficient data returns GETTING_STARTED with KEEP_GOING`() {
        val result = useCase.evaluate(healthyInput(totalReviews = 9))
        assertEquals(StudyHealthStatus.GETTING_STARTED, result.status)
        assertEquals(listOf(StudyTip.KEEP_GOING), result.tips)
    }

    @Test
    fun `exactly at data threshold is not GETTING_STARTED`() {
        val result = useCase.evaluate(healthyInput(totalReviews = StudyHealthUseCase.MIN_REVIEWS_TO_JUDGE))
        assertTrue(result.status != StudyHealthStatus.GETTING_STARTED)
    }

    // ── Healthy / positive ─────────────────────────────────────────────────────

    @Test
    fun `healthy deck surfaces ADD_MORE_CARDS and ON_TRACK`() {
        val result = useCase.evaluate(healthyInput())
        assertEquals(StudyHealthStatus.ON_TRACK, result.status)
        assertTrue(StudyTip.ADD_MORE_CARDS in result.tips)
    }

    @Test
    fun `recall well above target surfaces RETENTION_HIGH`() {
        val result = useCase.evaluate(
            healthyInput(
                totalReviews = 50,
                recallRate = 0.98f,
                targetRetention = 0.90
            )
        )
        assertTrue(StudyTip.RETENTION_HIGH in result.tips)
    }

    @Test
    fun `recall at exactly target does not flag LOW_ACCURACY`() {
        val result = useCase.evaluate(
            healthyInput(totalReviews = 50, recallRate = 0.90f, targetRetention = 0.90)
        )
        assertTrue(StudyTip.LOW_ACCURACY !in result.tips)
    }

    // ── Severe: CATCH_UP_BACKLOG ───────────────────────────────────────────────

    @Test
    fun `heavy backlog triggers CATCH_UP_BACKLOG and NEEDS_ATTENTION`() {
        val result = useCase.evaluate(
            healthyInput(activeTotal = 100, dueNowCount = 50)  // 50% > BACKLOG_RATIO(40%)
        )
        assertEquals(StudyHealthStatus.NEEDS_ATTENTION, result.status)
        assertEquals(StudyTip.CATCH_UP_BACKLOG, result.tips.first())
    }

    @Test
    fun `backlog below absolute floor does not trigger even if ratio is high`() {
        // 5 due of 10 active = 50% ratio, but dueNowCount(5) < BACKLOG_MIN_ABS(15)
        val result = useCase.evaluate(
            healthyInput(activeTotal = 10, dueNowCount = 5, recallRate = 0.92f)
        )
        assertTrue(StudyTip.CATCH_UP_BACKLOG !in result.tips)
    }

    // ── Severe: LOW_ACCURACY ───────────────────────────────────────────────────

    @Test
    fun `recall well below target flags LOW_ACCURACY`() {
        val result = useCase.evaluate(
            healthyInput(totalReviews = 100, recallRate = 0.80f, targetRetention = 0.90)
        )
        assertEquals(StudyHealthStatus.NEEDS_ATTENTION, result.status)
        assertTrue(StudyTip.LOW_ACCURACY in result.tips)
    }

    @Test
    fun `low accuracy with too few reviews is not flagged`() {
        val result = useCase.evaluate(
            healthyInput(totalReviews = StudyHealthUseCase.MIN_REVIEWS_FOR_ACCURACY - 1,
                         recallRate = 0.70f, targetRetention = 0.90)
        )
        assertTrue(StudyTip.LOW_ACCURACY !in result.tips)
    }

    // ── Severe: LOW_STABILITY_CHURN ────────────────────────────────────────────

    @Test
    fun `high low-stability share triggers LOW_STABILITY_CHURN`() {
        // 70% of review cards in 0–7d buckets → > LOW_STABILITY_SHARE_THRESHOLD(60%)
        val result = useCase.evaluate(
            healthyInput(reviewCardCount = 100, lowStabilityReviewCount = 70)
        )
        assertEquals(StudyHealthStatus.NEEDS_ATTENTION, result.status)
        assertTrue(StudyTip.LOW_STABILITY_CHURN in result.tips)
    }

    @Test
    fun `low stability churn not triggered below review card minimum`() {
        val result = useCase.evaluate(
            healthyInput(
                reviewCardCount = StudyHealthUseCase.MIN_REVIEW_CARDS_FOR_STABILITY - 1,
                lowStabilityReviewCount = 18  // 95%+ but too few cards
            )
        )
        assertTrue(StudyTip.LOW_STABILITY_CHURN !in result.tips)
    }

    // ── Minor: HOLD_OFF_NEW_CARDS ─────────────────────────────────────────────

    @Test
    fun `maturation overload triggers HOLD_OFF_NEW_CARDS`() {
        // 70 unconsolidated of 100 active = 70% > UNCONSOLIDATED_RATIO_THRESHOLD(60%)
        val result = useCase.evaluate(
            healthyInput(
                activeTotal = 100,
                newCount = 40,
                youngCount = 30,
                matureCount = 30,
                dueNowCount = 5
            )
        )
        assertEquals(StudyHealthStatus.GOOD, result.status)
        assertTrue(StudyTip.HOLD_OFF_NEW_CARDS in result.tips)
    }

    // ── Minor: LEECHES ─────────────────────────────────────────────────────────

    @Test
    fun `enough leeches triggers LEECHES tip`() {
        val result = useCase.evaluate(
            healthyInput(leechCount = StudyHealthUseCase.LEECH_COUNT_THRESHOLD)
        )
        assertEquals(StudyHealthStatus.GOOD, result.status)
        assertTrue(StudyTip.LEECHES in result.tips)
    }

    @Test
    fun `leech count below threshold does not trigger tip`() {
        val result = useCase.evaluate(
            healthyInput(leechCount = StudyHealthUseCase.LEECH_COUNT_THRESHOLD - 1)
        )
        assertTrue(StudyTip.LEECHES !in result.tips)
    }

    @Test
    fun `leech count is passed through to StudyHealth`() {
        val result = useCase.evaluate(healthyInput(leechCount = 7))
        assertEquals(7, result.leechCount)
    }

    // ── Minor: DECK_TOO_HARD ──────────────────────────────────────────────────

    @Test
    fun `difficulty skew triggers DECK_TOO_HARD`() {
        // 50% hard > HARD_DIFF_SHARE_THRESHOLD(40%)
        val result = useCase.evaluate(
            healthyInput(reviewCardCount = 100, hardDifficultyCount = 50)
        )
        assertEquals(StudyHealthStatus.GOOD, result.status)
        assertTrue(StudyTip.DECK_TOO_HARD in result.tips)
    }

    // ── Minor: STUDY_DAILY ────────────────────────────────────────────────────

    @Test
    fun `three or more zero-review days triggers STUDY_DAILY`() {
        val result = useCase.evaluate(
            healthyInput(zeroReviewDaysLast7 = StudyHealthUseCase.ZERO_DAYS_THRESHOLD)
        )
        assertEquals(StudyHealthStatus.GOOD, result.status)
        assertTrue(StudyTip.STUDY_DAILY in result.tips)
    }

    @Test
    fun `fewer than threshold zero days does not trigger STUDY_DAILY`() {
        val result = useCase.evaluate(
            healthyInput(zeroReviewDaysLast7 = StudyHealthUseCase.ZERO_DAYS_THRESHOLD - 1)
        )
        assertTrue(StudyTip.STUDY_DAILY !in result.tips)
    }

    // ── Status rollup ──────────────────────────────────────────────────────────

    @Test
    fun `severe tip always drives NEEDS_ATTENTION even when minor tips also present`() {
        val result = useCase.evaluate(
            healthyInput(
                activeTotal = 100,
                dueNowCount = 50,              // severe: CATCH_UP_BACKLOG
                leechCount = StudyHealthUseCase.LEECH_COUNT_THRESHOLD  // minor: LEECHES
            )
        )
        assertEquals(StudyHealthStatus.NEEDS_ATTENTION, result.status)
    }

    @Test
    fun `only minor tips drive GOOD status`() {
        val result = useCase.evaluate(
            healthyInput(leechCount = StudyHealthUseCase.LEECH_COUNT_THRESHOLD)
        )
        assertEquals(StudyHealthStatus.GOOD, result.status)
    }

    // ── Tip ordering ──────────────────────────────────────────────────────────

    @Test
    fun `severe tips appear before minor tips`() {
        val result = useCase.evaluate(
            healthyInput(
                activeTotal = 100,
                dueNowCount = 50,              // severe: CATCH_UP_BACKLOG
                leechCount = StudyHealthUseCase.LEECH_COUNT_THRESHOLD  // minor: LEECHES
            )
        )
        val catchUpIdx = result.tips.indexOf(StudyTip.CATCH_UP_BACKLOG)
        val leechIdx   = result.tips.indexOf(StudyTip.LEECHES)
        assertTrue("Severe tip must appear before minor tip",
                   catchUpIdx >= 0 && leechIdx > catchUpIdx)
    }

    @Test
    fun `positive tips appear after minor tips`() {
        val result = useCase.evaluate(
            healthyInput(leechCount = StudyHealthUseCase.LEECH_COUNT_THRESHOLD)
        )
        val leechIdx     = result.tips.indexOf(StudyTip.LEECHES)
        val positiveIdx  = result.tips.indexOfFirst { it == StudyTip.ADD_MORE_CARDS || it == StudyTip.KEEP_GOING }
        if (positiveIdx >= 0) {
            assertTrue("Minor tip must appear before positive tip", leechIdx < positiveIdx)
        }
    }
}
