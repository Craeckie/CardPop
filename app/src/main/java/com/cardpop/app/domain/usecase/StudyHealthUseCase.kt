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

import com.cardpop.app.domain.model.StudyHealth
import com.cardpop.app.domain.model.StudyHealthStatus
import com.cardpop.app.domain.model.StudyTip
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interprets already-loaded statistics into a [StudyHealth] result.
 *
 * Pure Kotlin — no Android imports. All inputs come from data already computed in
 * [StatisticsViewModel.loadStatistics]; no new DB queries are needed.
 */
@Singleton
class StudyHealthUseCase @Inject constructor() {

    /**
     * All inputs are derivable from data already computed in [StatisticsViewModel].
     *
     * @param activeTotal            Cards in enabled categories (New + Learning + Review + Relearning).
     * @param newCount               Cards in New state (never reviewed).
     * @param youngCount             Cards in Learning/Relearning or low-stability Review (non-mature, non-new).
     * @param matureCount            Cards with stability ≥ 21 d and reps ≥ 3.
     * @param dueNowCount            Cards currently due.
     * @param totalReviews           Lifetime review count (good + hard + easy + wrong).
     * @param recallRate             (good + easy + hard) / totalReviews — matches FSRS "not-Again"
     *                               semantics so it lines up with [targetRetention]. 0f when no reviews.
     * @param targetRetention        User-configured FSRS target retention (0.80–0.95).
     * @param leechCount             Cards with lapses ≥ [LEECH_LAPSES].
     * @param reviewCardCount        Cards in FSRS Review state.
     * @param hardDifficultyCount    Review cards with FSRS difficulty > [HARD_DIFF_THRESHOLD].
     * @param lowStabilityReviewCount Review cards with stability < [LOW_STABILITY_DAYS] (b0to3 + b3to7 buckets).
     * @param cardsAddedLast7Days    Cards whose [FlashcardEntity.createdAt] is within the last 7 days.
     * @param zeroReviewDaysLast7    Days in the last 7 calendar days with zero reviews (from review history).
     */
    data class Input(
        val activeTotal: Int,
        val newCount: Int,
        val youngCount: Int,
        val matureCount: Int,
        val dueNowCount: Int,
        val totalReviews: Int,
        val recallRate: Float,
        val targetRetention: Double,
        val leechCount: Int,
        val reviewCardCount: Int,
        val hardDifficultyCount: Int,
        val lowStabilityReviewCount: Int,
        val cardsAddedLast7Days: Int,
        val zeroReviewDaysLast7: Int
    )

    fun evaluate(input: Input): StudyHealth {
        // Not enough data yet — show an encouraging prompt, not a judgement
        if (input.totalReviews < MIN_REVIEWS_TO_JUDGE) {
            return StudyHealth(
                status = StudyHealthStatus.GETTING_STARTED,
                tips   = listOf(StudyTip.KEEP_GOING)
            )
        }

        val severe   = mutableListOf<StudyTip>()
        val minor    = mutableListOf<StudyTip>()
        val positive = mutableListOf<StudyTip>()

        // ── Severe checks (drive NEEDS_ATTENTION) ─────────────────────────────

        // Heavy backlog: ratio-based with a small absolute floor so "2 of 3 due"
        // on a brand-new deck doesn't trigger it.
        if (input.dueNowCount >= BACKLOG_MIN_ABS
                && input.activeTotal > 0
                && input.dueNowCount.toFloat() / input.activeTotal > BACKLOG_RATIO) {
            severe += StudyTip.CATCH_UP_BACKLOG
        }

        // Accuracy well below target; wait for enough reviews to avoid noise.
        // recallRate is (good+easy+hard)/total — same semantics as FSRS targetRetention.
        if (input.totalReviews >= MIN_REVIEWS_FOR_ACCURACY
                && input.recallRate < input.targetRetention - ACCURACY_TOLERANCE) {
            severe += StudyTip.LOW_ACCURACY
        }

        // Low-stability churn: most graduated cards have very short stability → re-learning,
        // not retaining. Distinct from accuracy: you can have decent accuracy while churning.
        if (input.reviewCardCount >= MIN_REVIEW_CARDS_FOR_STABILITY
                && input.lowStabilityReviewCount.toFloat() / input.reviewCardCount > LOW_STABILITY_SHARE_THRESHOLD) {
            severe += StudyTip.LOW_STABILITY_CHURN
        }

        // ── Minor checks (drive GOOD) ──────────────────────────────────────────

        // Unconsolidated share: (New + Young) / active — better proxy for "can I add more?"
        // than raw intake count, because it measures what fraction of cards haven't matured yet.
        val unconsolidatedShare = if (input.activeTotal >= MIN_ACTIVE_FOR_MATURATION)
            (input.newCount + input.youngCount).toFloat() / input.activeTotal
        else 0f

        if (unconsolidatedShare > UNCONSOLIDATED_RATIO_THRESHOLD) {
            minor += StudyTip.HOLD_OFF_NEW_CARDS
        }

        // Leeches: a handful of problem cards that keep failing and eat review time.
        if (input.leechCount >= LEECH_COUNT_THRESHOLD) {
            minor += StudyTip.LEECHES
        }

        // Difficulty skew: many cards are at the hard end of the FSRS scale
        // (difficulty 7–10 = hard/very-hard; 1–4 = easy).
        if (input.reviewCardCount >= MIN_REVIEW_CARDS_FOR_DIFFICULTY
                && input.hardDifficultyCount.toFloat() / input.reviewCardCount > HARD_DIFF_SHARE_THRESHOLD) {
            minor += StudyTip.DECK_TOO_HARD
        }

        // Inconsistent study pattern: gaps in the last 7 days hurt spacing.
        if (input.zeroReviewDaysLast7 >= ZERO_DAYS_THRESHOLD) {
            minor += StudyTip.STUDY_DAILY
        }

        // ── Positive checks ────────────────────────────────────────────────────

        // Spare capacity: recall well above target → over-studying / intervals tight.
        // Room to add more cards or raise targetRetention.
        if (input.totalReviews >= MIN_REVIEWS_FOR_ACCURACY
                && input.recallRate > input.targetRetention + SPARE_CAPACITY_MARGIN) {
            positive += StudyTip.RETENTION_HIGH
        }

        // Best time to add new cards: backlog light, accuracy ≥ target, deck not overloaded,
        // and haven't just dumped a large batch.
        val backlogLight  = input.activeTotal == 0
                || input.dueNowCount.toFloat() / input.activeTotal < BACKLOG_LIGHT_RATIO
        val accuracyOk    = input.recallRate >= input.targetRetention
        val notOverloaded = unconsolidatedShare <= UNCONSOLIDATED_RATIO_THRESHOLD
        val intakeOk      = input.activeTotal == 0
                || input.cardsAddedLast7Days.toFloat() / input.activeTotal <= NEW_INTAKE_RATIO
        if (backlogLight && accuracyOk && notOverloaded && intakeOk) {
            positive += StudyTip.ADD_MORE_CARDS
        }

        if (positive.isEmpty()) positive += StudyTip.KEEP_GOING

        // ── Status rollup ──────────────────────────────────────────────────────
        val status = when {
            severe.isNotEmpty() -> StudyHealthStatus.NEEDS_ATTENTION
            minor.isNotEmpty()  -> StudyHealthStatus.GOOD
            else                -> StudyHealthStatus.ON_TRACK
        }

        return StudyHealth(
            status     = status,
            tips       = severe + minor + positive,
            leechCount = input.leechCount
        )
    }

    companion object {
        // Data sufficiency
        const val MIN_REVIEWS_TO_JUDGE = 10
        const val MIN_REVIEWS_FOR_ACCURACY = 30

        // Backlog thresholds
        const val BACKLOG_MIN_ABS = 15        // absolute floor — avoid "2 of 3 cards due" false-positive
        const val BACKLOG_RATIO = 0.4f        // severe: ≥40% of active cards overdue
        const val BACKLOG_LIGHT_RATIO = 0.2f  // positive gate: <20% overdue = light

        // Accuracy
        const val ACCURACY_TOLERANCE = 0.05   // how far below target before flagging

        // Stability churn
        const val MIN_REVIEW_CARDS_FOR_STABILITY = 20
        const val LOW_STABILITY_SHARE_THRESHOLD = 0.6f // >60% of review cards in 0–7d buckets

        // Maturation / intake
        const val MIN_ACTIVE_FOR_MATURATION = 20
        const val UNCONSOLIDATED_RATIO_THRESHOLD = 0.6f // >60% of active cards unconsolidated
        const val NEW_INTAKE_RATIO = 0.5f               // cards added last 7d vs active total

        // Leeches — also used by the ViewModel to drive the leech filter
        const val LEECH_LAPSES = 4            // minimum lapses to classify a card as a leech
        const val LEECH_COUNT_THRESHOLD = 5   // minimum leech count to surface the tip

        // Difficulty skew
        const val MIN_REVIEW_CARDS_FOR_DIFFICULTY = 20
        const val HARD_DIFF_THRESHOLD = 7.0   // FSRS difficulty > 7 = hard (scale: 1=easy, 10=hard)
        const val HARD_DIFF_SHARE_THRESHOLD = 0.4f // >40% of review cards are hard

        // Spare capacity
        const val SPARE_CAPACITY_MARGIN = 0.07 // how far above target before suggesting more cards

        // Study consistency
        const val ZERO_DAYS_THRESHOLD = 3     // ≥3 zero-review days in last 7 triggers the tip
    }
}
