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
 *
 * ----------------------------------------------------------------------
 * Adapted from open-spaced-repetition/FSRS-Kotlin (MIT License) by Arteh,
 * which itself implements the Free Spaced Repetition Scheduler v6
 * specification from the fsrs4anki project. Algebra is preserved verbatim
 * from the upstream reference; the only adaptations are removal of Android
 * resource coupling (R.color.*, @ColorRes), removal of Room annotations,
 * replacement of LocalDateTime with Long epoch-millis, and an injectable
 * random seed so unit tests can be deterministic.
 *
 * Upstream: https://github.com/open-spaced-repetition/FSRS-Kotlin
 * Spec:     https://github.com/open-spaced-repetition/fsrs4anki/wiki
 * ----------------------------------------------------------------------
 */

package com.cardpop.app.domain.fsrs

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

class Fsrs(
    private val requestRetention: Double = FsrsParameters.DEFAULT_RETENTION,
    private val params: List<Double> = FsrsParameters.DEFAULT,
    private val randomSeed: Long? = null,
    private val enableFuzz: Boolean = true
) {

    private data class InitState(val difficulty: Double = 0.0, val stability: Double = 0.0)

    private val decay = -params[20]
    private val factor = 0.9.pow(1.0 / decay) - 1
    private val random: Random = randomSeed?.let { Random(it) } ?: Random(System.currentTimeMillis())

    fun calculate(card: FsrsCard): List<FsrsGrade> {
        val stateAgain: InitState
        val stateHard: InitState
        val stateGood: InitState
        val stateEasy: InitState

        var durationHard = HARD_SHORT_TERM_MS
        val durationAgain: Long = AGAIN_SHORT_TERM_MS
        val durationGood: Long
        val durationEasy: Long

        var ivlAgain = 0
        var ivlHard = 0
        var ivlGood = 0
        var ivlEasy = 0

        val txtAgain: String
        val txtHard: String
        val txtGood: String
        val txtEasy: String

        when (card.state) {
            FsrsCardState.New -> {
                stateAgain = initState(FsrsRating.Again)
                stateHard = initState(FsrsRating.Hard)
                stateGood = initState(FsrsRating.Good)
                stateEasy = initState(FsrsRating.Easy)

                ivlEasy = 1

                txtAgain = "< 3 Min"
                txtHard = "5 Min"
                txtGood = "10 Min"
                txtEasy = "1 day"

                durationGood = GOOD_SHORT_TERM_MS
                durationEasy = ivlEasy * DAY_MS
            }

            FsrsCardState.Learning, FsrsCardState.Relearning -> {
                if (card.difficulty == 0.0) {
                    stateAgain = initState(FsrsRating.Again)
                    stateHard = initState(FsrsRating.Hard)
                    stateGood = initState(FsrsRating.Good)
                    stateEasy = initState(FsrsRating.Easy)
                } else {
                    val lastD = card.difficulty
                    val lastS = card.stability

                    stateAgain = InitState(
                        difficulty = nextDifficulty(lastD, FsrsRating.Again),
                        stability = nextShortTermStability(lastS, FsrsRating.Again)
                    )
                    stateHard = InitState(
                        difficulty = nextDifficulty(lastD, FsrsRating.Hard),
                        stability = nextShortTermStability(lastS, FsrsRating.Hard)
                    )
                    stateGood = InitState(
                        difficulty = nextDifficulty(lastD, FsrsRating.Good),
                        stability = nextShortTermStability(lastS, FsrsRating.Good)
                    )
                    stateEasy = InitState(
                        difficulty = nextDifficulty(lastD, FsrsRating.Easy),
                        stability = nextShortTermStability(lastS, FsrsRating.Easy)
                    )
                }

                ivlGood = nextInterval(stateGood.stability)
                ivlEasy = nextInterval(stateEasy.stability)
                ivlEasy = max(ivlEasy, ivlGood + 1)

                txtAgain = "< 3 Min"
                txtHard = "10 Min"
                txtGood = convertDays(ivlGood)
                txtEasy = convertDays(ivlEasy)

                durationHard = GOOD_SHORT_TERM_MS
                durationGood = ivlGood * DAY_MS
                durationEasy = ivlEasy * DAY_MS
            }

            FsrsCardState.Review -> {
                val interval = card.elapsedDays
                val lastD = card.difficulty
                val lastS = card.stability

                val retrievability = forgettingCurve(interval.toDouble(), lastS)

                stateAgain = InitState(
                    difficulty = nextDifficulty(lastD, FsrsRating.Again),
                    stability = nextForgetStability(lastD, lastS, retrievability)
                )
                stateHard = InitState(
                    difficulty = nextDifficulty(lastD, FsrsRating.Hard),
                    stability = nextRecallStability(lastD, lastS, retrievability, FsrsRating.Hard)
                )
                stateGood = InitState(
                    difficulty = nextDifficulty(lastD, FsrsRating.Good),
                    stability = nextRecallStability(lastD, lastS, retrievability, FsrsRating.Good)
                )
                stateEasy = InitState(
                    difficulty = nextDifficulty(lastD, FsrsRating.Easy),
                    stability = nextRecallStability(lastD, lastS, retrievability, FsrsRating.Easy)
                )

                ivlHard = nextInterval(stateHard.stability)
                ivlGood = nextInterval(stateGood.stability)
                ivlEasy = nextInterval(stateEasy.stability)

                ivlHard = min(ivlHard, ivlGood)
                ivlGood = max(ivlGood, ivlHard + 1)
                ivlEasy = max(ivlEasy, ivlGood + 1)

                ivlAgain = card.scheduledDays

                txtAgain = "< 3 Min"
                txtHard = convertDays(ivlHard)
                txtGood = convertDays(ivlGood)
                txtEasy = convertDays(ivlEasy)

                durationHard = ivlHard * DAY_MS
                durationGood = ivlGood * DAY_MS
                durationEasy = ivlEasy * DAY_MS
            }
        }

        return listOf(
            FsrsGrade(
                rating = FsrsRating.Again,
                stability = stateAgain.stability,
                difficulty = stateAgain.difficulty,
                scheduledDays = ivlAgain,
                durationMillis = durationAgain,
                txt = txtAgain
            ),
            FsrsGrade(
                rating = FsrsRating.Hard,
                stability = stateHard.stability,
                difficulty = stateHard.difficulty,
                scheduledDays = ivlHard,
                durationMillis = durationHard,
                txt = txtHard
            ),
            FsrsGrade(
                rating = FsrsRating.Good,
                stability = stateGood.stability,
                difficulty = stateGood.difficulty,
                scheduledDays = ivlGood,
                durationMillis = durationGood,
                txt = txtGood
            ),
            FsrsGrade(
                rating = FsrsRating.Easy,
                stability = stateEasy.stability,
                difficulty = stateEasy.difficulty,
                scheduledDays = ivlEasy,
                durationMillis = durationEasy,
                txt = txtEasy
            )
        )
    }

    fun apply(card: FsrsCard, rating: FsrsRating, now: Long = System.currentTimeMillis()): FsrsCard {
        val grade = calculate(card).first { it.rating == rating }
        val newState = transition(card.state, rating)
        val isLapse = card.state == FsrsCardState.Review && rating == FsrsRating.Again
        return FsrsCard(
            stability = grade.stability,
            difficulty = grade.difficulty,
            scheduledDays = grade.scheduledDays,
            elapsedDays = 0,
            reps = card.reps + 1,
            lapses = card.lapses + (if (isLapse) 1 else 0),
            state = newState,
            lastReviewAt = now
        )
    }

    private fun transition(current: FsrsCardState, rating: FsrsRating): FsrsCardState =
        when (current) {
            FsrsCardState.New -> when (rating) {
                FsrsRating.Easy -> FsrsCardState.Review
                else -> FsrsCardState.Learning
            }

            FsrsCardState.Learning -> when (rating) {
                FsrsRating.Good, FsrsRating.Easy -> FsrsCardState.Review
                else -> FsrsCardState.Learning
            }

            FsrsCardState.Review -> when (rating) {
                FsrsRating.Again -> FsrsCardState.Relearning
                else -> FsrsCardState.Review
            }

            FsrsCardState.Relearning -> when (rating) {
                FsrsRating.Good, FsrsRating.Easy -> FsrsCardState.Review
                else -> FsrsCardState.Relearning
            }
        }

    private fun convertDays(days: Int): String =
        when {
            days > 365 -> "${"%.1f".format(days / 365.0)} year"
            days > 30 -> "${"%.1f".format(days / 30.0)} month"
            else -> "$days day"
        }

    private fun applyFuzz(
        interval: Double,
        fuzzFactor: Double,
        scheduledDays: Int = 0
    ): Double {
        if (!enableFuzz || interval < 2.5) return interval

        val ivl = interval.roundToInt()
        var minIvl = max(2, (ivl * 0.95 - 1).roundToInt())
        val maxIvl = (ivl * 1.05 + 1).roundToInt()

        if (ivl > scheduledDays)
            minIvl = max(minIvl, scheduledDays + 1)

        return floor(fuzzFactor * (maxIvl - minIvl + 1) + minIvl)
    }

    private fun forgettingCurve(interval: Double, stability: Double): Double =
        if (stability <= 0.0) 1.0 else (1.0 + factor * interval / stability).pow(decay)

    private fun generateFuzzFactor(): Double = random.nextDouble()

    private fun initDifficulty(rating: FsrsRating): Double {
        val base = params[4]
        val exponent = params[5] * (rating.value - 1)
        val raw = base - exp(exponent) + 1
        return round2(raw.coerceIn(1.0, 10.0))
    }

    private fun initStability(rating: FsrsRating): Double {
        val index = rating.value - 1
        val value = params.getOrElse(index) { 0.1 }
        return round2(max(value, 0.1))
    }

    private fun initState(rating: FsrsRating): InitState =
        InitState(
            difficulty = initDifficulty(rating),
            stability = initStability(rating)
        )

    private fun linearDamping(delta: Double, oldD: Double): Double =
        (10 - oldD) * delta / 9

    private fun meanReversion(initD: Double, nextD: Double): Double =
        params[7] * initD + (1 - params[7]) * nextD

    private fun nextInterval(
        stability: Double,
        maxInterval: Int = 36500,
        lastInterval: Int = 0
    ): Int {
        val fuzzFactor = generateFuzzFactor()
        val rawInterval = stability / factor * (requestRetention.pow(1 / decay) - 1)
        val fuzzed = applyFuzz(rawInterval, fuzzFactor, scheduledDays = lastInterval)
        return fuzzed.roundToInt().coerceIn(1, maxInterval)
    }

    private fun nextDifficulty(currentD: Double, rating: FsrsRating): Double {
        val deltaD = -params[6] * (rating.value - 3)
        val damped = linearDamping(deltaD, currentD)
        val nextD = currentD + damped
        val reverted = meanReversion(initDifficulty(FsrsRating.Easy), nextD)
        return round2(reverted.coerceIn(1.0, 10.0))
    }

    private fun nextShortTermStability(currentS: Double, rating: FsrsRating): Double {
        var sinc = exp(params[17] * (rating.value - 3 + params[18])) * currentS.pow(-params[19])
        if (rating.value >= 3) {
            sinc = max(sinc, 1.0)
        }
        return round2(abs(currentS * sinc))
    }

    private fun nextForgetStability(
        difficulty: Double,
        stability: Double,
        retrievability: Double
    ): Double {
        val sMin = stability / exp(params[17] * params[18])

        val result = params[11] *
            difficulty.pow(-params[12]) *
            ((stability + 1).pow(params[13]) - 1) *
            exp((1 - retrievability) * params[14])

        return round2(min(result, sMin))
    }

    private fun nextRecallStability(d: Double, s: Double, r: Double, rating: FsrsRating): Double {
        val hardPenalty = if (rating == FsrsRating.Hard) params[15] else 1.0
        val easyBonus = if (rating == FsrsRating.Easy) params[16] else 1.0

        val growth = exp(params[8]) *
            (11 - d) *
            s.pow(-params[9]) *
            (exp((1 - r) * params[10]) - 1) *
            hardPenalty *
            easyBonus

        return round2(s * (1 + growth))
    }

    private fun round2(value: Double): Double =
        kotlin.math.round(value * 100.0) / 100.0

    companion object {
        // Short-term scheduling durations for the Learning / Relearning phases.
        // These are the same defaults used by FSRS-Kotlin's calculate() output for
        // ratings that don't graduate the card to Review. Tunable but deliberately
        // not user-facing for now; kept in one place so the SrsUseCase can rely on
        // a single source of truth.
        private const val AGAIN_SHORT_TERM_MS: Long = 3 * 60 * 1000L
        private const val HARD_SHORT_TERM_MS: Long = 5 * 60 * 1000L
        private const val GOOD_SHORT_TERM_MS: Long = 10 * 60 * 1000L
        private const val DAY_MS: Long = 24L * 60 * 60 * 1000
    }
}
