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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [NewCardGating.decide] — the pure new-card eligibility logic.
 */
class NewCardGatingTest {

    private fun decide(
        hour: Int = 9,
        today: Int = 0,
        limit: Int = 10,
        cutoffEnabled: Boolean = true,
        cutoffHour: Int = 18
    ) = NewCardGating.decide(hour, today, limit, cutoffEnabled, cutoffHour)

    @Test
    fun under_cap_before_cutoff_allows_and_prioritizes() {
        val d = decide(hour = 9, today = 3, limit = 10)
        assertTrue(d.allowNew)
        assertTrue(d.prioritizeNew)
    }

    @Test
    fun at_cap_blocks_new() {
        val d = decide(today = 10, limit = 10)
        assertFalse(d.allowNew)
        assertFalse(d.prioritizeNew)
    }

    @Test
    fun over_cap_blocks_new() {
        val d = decide(today = 25, limit = 10)
        assertFalse(d.allowNew)
    }

    @Test
    fun unlimited_cap_always_allows_regardless_of_count() {
        val d = decide(today = 9999, limit = -1)
        assertTrue(d.allowNew)
    }

    @Test
    fun zero_cap_never_allows() {
        val d = decide(today = 0, limit = 0)
        assertFalse(d.allowNew)
    }

    @Test
    fun after_cutoff_suppresses_new() {
        val d = decide(hour = 20, today = 0, limit = 10, cutoffHour = 18)
        assertFalse(d.allowNew)
        assertFalse(d.prioritizeNew)
    }

    @Test
    fun at_cutoff_hour_is_suppressed() {
        // hourOfDay < cutoffHour is the eligibility window, so hour == cutoff is out.
        val d = decide(hour = 18, cutoffHour = 18)
        assertFalse(d.allowNew)
    }

    @Test
    fun cutoff_disabled_allows_but_does_not_prioritize() {
        val d = decide(hour = 23, today = 0, limit = 10, cutoffEnabled = false)
        assertTrue("cutoff off => time never blocks new", d.allowNew)
        assertFalse("cutoff off => never front-load above reviews", d.prioritizeNew)
    }

    @Test
    fun cutoff_disabled_still_respects_cap() {
        val d = decide(today = 10, limit = 10, cutoffEnabled = false)
        assertFalse(d.allowNew)
    }
}
