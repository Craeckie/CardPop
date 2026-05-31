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

package com.cardpop.app.domain.util

import com.cardpop.app.data.entity.FlashcardEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [EmptyStateFlashcard].
 *
 * The sentinel IDs are a small but critical invariant:
 *   -2L = empty-state placeholder  (EMPTY_STATE_ID)
 *   -1L = demo card  (created by OverlayService.startWithDemoFlashcard)
 *
 * Mixing them up would cause the demo-completion handler to be invoked on the
 * empty-state card or vice versa, so we pin the ID constants explicitly.
 */
class EmptyStateFlashcardTest {

    // -------------------------------------------------------------------------
    // create()
    // -------------------------------------------------------------------------

    @Test
    fun create_returns_card_with_EMPTY_STATE_ID() {
        val card = EmptyStateFlashcard.create()
        assertEquals(EmptyStateFlashcard.EMPTY_STATE_ID, card.id)
    }

    @Test
    fun EMPTY_STATE_ID_constant_is_minus_two() {
        assertEquals(-2L, EmptyStateFlashcard.EMPTY_STATE_ID)
    }

    // -------------------------------------------------------------------------
    // isEmptyState
    // -------------------------------------------------------------------------

    @Test
    fun isEmptyState_true_for_created_card() {
        assertTrue(EmptyStateFlashcard.isEmptyState(EmptyStateFlashcard.create()))
    }

    @Test
    fun isEmptyState_true_only_for_id_minus_2() {
        assertTrue(EmptyStateFlashcard.isEmptyState(cardWithId(-2L)))
    }

    @Test
    fun isEmptyState_false_for_demo_id_minus_1() {
        assertFalse(EmptyStateFlashcard.isEmptyState(cardWithId(-1L)))
    }

    @Test
    fun isEmptyState_false_for_positive_id() {
        assertFalse(EmptyStateFlashcard.isEmptyState(cardWithId(42L)))
    }

    @Test
    fun isEmptyState_false_for_id_zero() {
        // id=0 is Room's "not persisted" sentinel, not an empty-state card.
        assertFalse(EmptyStateFlashcard.isEmptyState(cardWithId(0L)))
    }

    // -------------------------------------------------------------------------
    // isSystemFlashcard
    // -------------------------------------------------------------------------

    @Test
    fun isSystemFlashcard_true_for_empty_state_id() {
        assertTrue(EmptyStateFlashcard.isSystemFlashcard(cardWithId(-2L)))
    }

    @Test
    fun isSystemFlashcard_true_for_demo_id_minus_1() {
        assertTrue(EmptyStateFlashcard.isSystemFlashcard(cardWithId(-1L)))
    }

    @Test
    fun isSystemFlashcard_false_for_id_zero() {
        assertFalse(EmptyStateFlashcard.isSystemFlashcard(cardWithId(0L)))
    }

    @Test
    fun isSystemFlashcard_false_for_positive_id() {
        assertFalse(EmptyStateFlashcard.isSystemFlashcard(cardWithId(1L)))
        assertFalse(EmptyStateFlashcard.isSystemFlashcard(cardWithId(Long.MAX_VALUE)))
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private fun cardWithId(id: Long) = FlashcardEntity(
        id = id,
        categoryId = 0L,
        question = "q",
        answer = "a"
    )
}
