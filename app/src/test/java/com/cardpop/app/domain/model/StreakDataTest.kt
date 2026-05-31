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

package com.cardpop.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [StreakData].
 *
 * All timestamps are expressed as explicit UTC midnight boundaries derived from
 * `dayNumber * ONE_DAY_MS`, where `ONE_DAY_MS = 86_400_000L`. This matches the
 * day-bucketing logic in [StreakData]: `timestamp / 86_400_000` gives the
 * calendar-day number, and consecutive days differ by exactly 1.
 *
 * Using exact multiples of [ONE_DAY_MS] keeps the tests independent of the
 * local wall clock and avoids DST ambiguity (StreakData uses UTC buckets, not
 * local dates).
 */
class StreakDataTest {

    private val ONE_DAY_MS = 86_400_000L

    /** Timestamp at the start of "day 10000" (UTC midnight). */
    private val day0 = 10_000L * ONE_DAY_MS

    /** Day 10001 — the day after day0. */
    private val day1 = 10_001L * ONE_DAY_MS

    /** Day 10002 — two days after day0. */
    private val day2 = 10_002L * ONE_DAY_MS

    /** A timestamp mid-day on day 10001 (should still bucket into day 10001). */
    private val day1mid = day1 + 6 * 3600_000L

    // =========================================================================
    // updateStreakOnActivity
    // =========================================================================

    @Test
    fun first_activity_ever_sets_streak_to_1() {
        val streak = StreakData() // lastActivityTimestamp == 0
        val result = streak.updateStreakOnActivity(day0)
        assertEquals(1, result.currentStreak)
        assertEquals(day0, result.lastActivityTimestamp)
        assertEquals(1, result.highestStreak)
    }

    @Test
    fun same_day_activity_is_no_op() {
        val streak = StreakData(currentStreak = 3, lastActivityTimestamp = day0, highestStreak = 5)
        val result = streak.updateStreakOnActivity(day0mid())
        assertEquals(3, result.currentStreak)
        assertEquals(day0, result.lastActivityTimestamp) // unchanged
        assertEquals(5, result.highestStreak)
    }

    private fun day0mid() = day0 + 12 * 3600_000L

    @Test
    fun consecutive_day_increments_streak() {
        val streak = StreakData(currentStreak = 4, lastActivityTimestamp = day0, highestStreak = 4)
        val result = streak.updateStreakOnActivity(day1)
        assertEquals(5, result.currentStreak)
        assertEquals(day1, result.lastActivityTimestamp)
    }

    @Test
    fun consecutive_day_mid_day_timestamp_still_increments() {
        val streak = StreakData(currentStreak = 2, lastActivityTimestamp = day0, highestStreak = 2)
        val result = streak.updateStreakOnActivity(day1mid)
        assertEquals(3, result.currentStreak)
    }

    @Test
    fun gap_resets_streak_to_1() {
        val streak = StreakData(currentStreak = 7, lastActivityTimestamp = day0, highestStreak = 7)
        val result = streak.updateStreakOnActivity(day2) // skip day1 → gap
        assertEquals(1, result.currentStreak)
        assertEquals(day2, result.lastActivityTimestamp)
    }

    @Test
    fun gap_larger_than_two_days_resets_streak_to_1() {
        val streak = StreakData(currentStreak = 10, lastActivityTimestamp = day0, highestStreak = 10)
        val fiveDaysLater = day0 + 5 * ONE_DAY_MS
        val result = streak.updateStreakOnActivity(fiveDaysLater)
        assertEquals(1, result.currentStreak)
    }

    @Test
    fun highestStreak_updated_when_streak_exceeds_previous_high() {
        val streak = StreakData(currentStreak = 5, lastActivityTimestamp = day0, highestStreak = 5)
        val result = streak.updateStreakOnActivity(day1)
        assertEquals(6, result.highestStreak) // 6 > 5 → updated
    }

    @Test
    fun highestStreak_preserved_on_reset_if_previous_high_was_higher() {
        val streak = StreakData(currentStreak = 3, lastActivityTimestamp = day0, highestStreak = 10)
        val result = streak.updateStreakOnActivity(day2) // gap → reset to 1
        assertEquals(10, result.highestStreak) // 10 > 1 → keep 10
    }

    @Test
    fun highestStreak_remains_1_when_first_activity_sets_it() {
        val result = StreakData().updateStreakOnActivity(day0)
        assertEquals(1, result.highestStreak)
    }

    // =========================================================================
    // getCurrentValidStreak
    // =========================================================================

    @Test
    fun valid_streak_zero_when_never_active() {
        val streak = StreakData() // lastActivityTimestamp == 0
        assertEquals(0, streak.getCurrentValidStreak(day0))
    }

    @Test
    fun valid_streak_zero_when_currentStreak_is_zero() {
        val streak = StreakData(currentStreak = 0, lastActivityTimestamp = day0, highestStreak = 5)
        assertEquals(0, streak.getCurrentValidStreak(day0))
    }

    @Test
    fun valid_streak_returned_on_same_day() {
        val streak = StreakData(currentStreak = 3, lastActivityTimestamp = day0, highestStreak = 3)
        // Check a few hours later, still the same UTC day.
        assertEquals(3, streak.getCurrentValidStreak(day0 + 2 * 3600_000L))
    }

    @Test
    fun valid_streak_returned_on_next_day_boundary() {
        // Last activity was on day0; checking on day1 — daysSinceLastActivity == 1, still valid.
        val streak = StreakData(currentStreak = 5, lastActivityTimestamp = day0, highestStreak = 5)
        assertEquals(5, streak.getCurrentValidStreak(day1))
    }

    @Test
    fun valid_streak_zero_after_gap_of_two_days() {
        // Last activity on day0; checking on day2 — daysSinceLastActivity == 2 → broken.
        val streak = StreakData(currentStreak = 5, lastActivityTimestamp = day0, highestStreak = 5)
        assertEquals(0, streak.getCurrentValidStreak(day2))
    }

    @Test
    fun valid_streak_zero_after_large_gap() {
        val streak = StreakData(currentStreak = 30, lastActivityTimestamp = day0, highestStreak = 30)
        assertEquals(0, streak.getCurrentValidStreak(day0 + 10 * ONE_DAY_MS))
    }
}
