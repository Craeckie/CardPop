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

package com.floflacards.app.util

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for IntervalConstants.
 * Tests validation logic for interval input.
 */
class IntervalConstantsTest {

    @Test
    fun isValidInterval_minimumValue_returnsTrue() {
        assertTrue(IntervalConstants.isValidInterval(1))
    }

    @Test
    fun isValidInterval_maximumValue_returnsTrue() {
        assertTrue(IntervalConstants.isValidInterval(1440))
    }

    @Test
    fun isValidInterval_typicalValue_returnsTrue() {
        assertTrue(IntervalConstants.isValidInterval(5))
        assertTrue(IntervalConstants.isValidInterval(10))
        assertTrue(IntervalConstants.isValidInterval(30))
    }

    @Test
    fun isValidInterval_zero_returnsFalse() {
        assertFalse(IntervalConstants.isValidInterval(0))
    }

    @Test
    fun isValidInterval_negativeValue_returnsFalse() {
        assertFalse(IntervalConstants.isValidInterval(-1))
        assertFalse(IntervalConstants.isValidInterval(-100))
    }

    @Test
    fun isValidInterval_aboveMaximum_returnsFalse() {
        assertFalse(IntervalConstants.isValidInterval(1441))
        assertFalse(IntervalConstants.isValidInterval(2000))
    }

    @Test
    fun parseInterval_validString_returnsInt() {
        assertEquals(5, IntervalConstants.parseInterval("5"))
        assertEquals(10, IntervalConstants.parseInterval("10"))
        assertEquals(1440, IntervalConstants.parseInterval("1440"))
    }

    @Test
    fun parseInterval_minimumValue_returnsInt() {
        assertEquals(1, IntervalConstants.parseInterval("1"))
    }

    @Test
    fun parseInterval_maximumValue_returnsInt() {
        assertEquals(1440, IntervalConstants.parseInterval("1440"))
    }

    @Test
    fun parseInterval_invalidString_returnsNull() {
        assertNull(IntervalConstants.parseInterval("abc"))
        assertNull(IntervalConstants.parseInterval(""))
        assertNull(IntervalConstants.parseInterval("5.5"))
    }

    @Test
    fun parseInterval_zero_returnsNull() {
        assertNull(IntervalConstants.parseInterval("0"))
    }

    @Test
    fun parseInterval_aboveMaximum_returnsNull() {
        assertNull(IntervalConstants.parseInterval("1441"))
        assertNull(IntervalConstants.parseInterval("9999"))
    }

    @Test
    fun parseInterval_negativeString_returnsNull() {
        assertNull(IntervalConstants.parseInterval("-5"))
    }

    @Test
    fun predefinedIntervals_containsExpectedValues() {
        val expected = listOf(1, 5, 10, 15, 30)
        assertEquals(expected, IntervalConstants.PREDEFINED_INTERVALS)
    }

    @Test
    fun predefinedIntervals_allAreValid() {
        IntervalConstants.PREDEFINED_INTERVALS.forEach { interval ->
            assertTrue("Interval $interval should be valid", 
                IntervalConstants.isValidInterval(interval))
        }
    }

    @Test
    fun defaultInterval_isValid() {
        assertTrue(IntervalConstants.isValidInterval(IntervalConstants.DEFAULT_INTERVAL_MINUTES))
    }

    @Test
    fun defaultInterval_hasExpectedValue() {
        assertEquals(5, IntervalConstants.DEFAULT_INTERVAL_MINUTES)
    }

    @Test
    fun minInterval_hasExpectedValue() {
        assertEquals(1, IntervalConstants.MIN_INTERVAL_MINUTES)
    }

    @Test
    fun maxInterval_hasExpectedValue() {
        assertEquals(1440, IntervalConstants.MAX_INTERVAL_MINUTES)
    }

    @Test
    fun maxInterval_equalsTwentyFourHours() {
        // 24 hours * 60 minutes = 1440 minutes
        assertEquals(24 * 60, IntervalConstants.MAX_INTERVAL_MINUTES)
    }
}
