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
import org.junit.Test

/**
 * Tests for [FsrsCardState.fromValue].
 *
 * The DB migration (v7→v8) and every deserialization path rely on the correct
 * Int→FsrsCardState mapping.  An unknown value must fall back to [FsrsCardState.New]
 * so that corrupt or future DB rows degrade safely rather than crashing.
 */
class FsrsCardStateTest {

    @Test
    fun value_0_maps_to_New() {
        assertEquals(FsrsCardState.New, FsrsCardState.fromValue(0))
    }

    @Test
    fun value_1_maps_to_Learning() {
        assertEquals(FsrsCardState.Learning, FsrsCardState.fromValue(1))
    }

    @Test
    fun value_2_maps_to_Review() {
        assertEquals(FsrsCardState.Review, FsrsCardState.fromValue(2))
    }

    @Test
    fun value_3_maps_to_Relearning() {
        assertEquals(FsrsCardState.Relearning, FsrsCardState.fromValue(3))
    }

    @Test
    fun unknown_positive_value_falls_back_to_New() {
        assertEquals(FsrsCardState.New, FsrsCardState.fromValue(99))
    }

    @Test
    fun negative_value_falls_back_to_New() {
        assertEquals(FsrsCardState.New, FsrsCardState.fromValue(-1))
    }

    @Test
    fun enum_value_fields_are_correct() {
        assertEquals(0, FsrsCardState.New.value)
        assertEquals(1, FsrsCardState.Learning.value)
        assertEquals(2, FsrsCardState.Review.value)
        assertEquals(3, FsrsCardState.Relearning.value)
    }
}
