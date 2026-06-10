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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [FsrsParameterParser].
 *
 * All assertions are pure JVM — no Android dependencies needed.
 */
class FsrsParameterParserTest {

    // A valid 21-value array matching FsrsParameters.DEFAULT for round-trip checks.
    private val DEFAULT_21 = FsrsParameters.DEFAULT

    // Alternate valid weights used to verify non-default acceptance.
    private val ALT_21 = List(21) { i -> i * 0.1 }

    // ── parse: success cases ──────────────────────────────────────────────────

    @Test
    fun `parse bare comma-separated`() {
        val input = ALT_21.joinToString(", ")
        val result = FsrsParameterParser.parse(input)
        assertTrue(result.isSuccess)
        assertEquals(ALT_21, result.getOrThrow())
    }

    @Test
    fun `parse bracketed array`() {
        val input = "[${ALT_21.joinToString(", ")}]"
        val result = FsrsParameterParser.parse(input)
        assertTrue(result.isSuccess)
        assertEquals(ALT_21, result.getOrThrow())
    }

    @Test
    fun `parse w-colon prefix`() {
        val input = "w: [${ALT_21.joinToString(", ")}]"
        val result = FsrsParameterParser.parse(input)
        assertTrue(result.isSuccess)
        assertEquals(ALT_21, result.getOrThrow())
    }

    @Test
    fun `parse w-equals prefix`() {
        val input = "w = [${ALT_21.joinToString(", ")}]"
        val result = FsrsParameterParser.parse(input)
        assertTrue(result.isSuccess)
        assertEquals(ALT_21, result.getOrThrow())
    }

    @Test
    fun `parse json object with w key`() {
        val input = """{"w": [${ALT_21.joinToString(", ")}]}"""
        val result = FsrsParameterParser.parse(input)
        assertTrue(result.isSuccess)
        assertEquals(ALT_21, result.getOrThrow())
    }

    @Test
    fun `parse whitespace-separated (newlines)`() {
        val input = ALT_21.joinToString("\n")
        val result = FsrsParameterParser.parse(input)
        assertTrue(result.isSuccess)
        assertEquals(ALT_21, result.getOrThrow())
    }

    @Test
    fun `parse negative values`() {
        // FSRS-6 w20 can be negative (e.g. -0.54 in fsrs4anki examples).
        val withNeg = ALT_21.toMutableList().also { it[20] = -0.54 }
        val input = withNeg.joinToString(", ")
        val result = FsrsParameterParser.parse(input)
        assertTrue(result.isSuccess)
        assertEquals(-0.54, result.getOrThrow()[20], 1e-9)
    }

    @Test
    fun `parse leading whitespace and trailing whitespace`() {
        val input = "  ${ALT_21.joinToString(", ")}  "
        val result = FsrsParameterParser.parse(input)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `parse FSRS DEFAULT values round-trip`() {
        val formatted = FsrsParameterParser.format(DEFAULT_21)
        val parsed = FsrsParameterParser.parse(formatted)
        assertTrue(parsed.isSuccess)
        val values = parsed.getOrThrow()
        assertEquals(DEFAULT_21.size, values.size)
        DEFAULT_21.forEachIndexed { i, expected ->
            assertEquals("index $i", expected, values[i], 1e-9)
        }
    }

    // ── parse: failure cases ──────────────────────────────────────────────────

    @Test
    fun `parse too few values returns failure`() {
        val input = List(20) { 0.1 }.joinToString(", ")
        val result = FsrsParameterParser.parse(input)
        assertTrue(result.isFailure)
        val msg = result.exceptionOrNull()?.message ?: ""
        assertTrue("error should mention expected count", msg.contains("21"))
        assertTrue("error should mention actual count", msg.contains("20"))
    }

    @Test
    fun `parse too many values returns failure`() {
        val input = List(22) { 0.1 }.joinToString(", ")
        val result = FsrsParameterParser.parse(input)
        assertTrue(result.isFailure)
    }

    @Test
    fun `parse empty string returns failure`() {
        val result = FsrsParameterParser.parse("")
        assertTrue(result.isFailure)
    }

    @Test
    fun `parse non-numeric token returns failure`() {
        val tokens = ALT_21.toMutableList<Any>().also { it[5] = "abc" }
        val input = tokens.joinToString(", ")
        val result = FsrsParameterParser.parse(input)
        assertTrue(result.isFailure)
        val msg = result.exceptionOrNull()?.message ?: ""
        assertTrue("error should mention the bad token", msg.contains("abc"))
    }

    @Test
    fun `parse garbage input returns failure`() {
        val result = FsrsParameterParser.parse("this is not a weight array")
        assertTrue(result.isFailure)
    }

    // ── format ────────────────────────────────────────────────────────────────

    @Test
    fun `format produces comma-space separated string`() {
        val formatted = FsrsParameterParser.format(listOf(0.1, 0.2, 0.3))
        assertEquals("0.1, 0.2, 0.3", formatted)
    }

    @Test
    fun `format output is parseable by parse`() {
        val formatted = FsrsParameterParser.format(DEFAULT_21)
        val reparsed = FsrsParameterParser.parse(formatted)
        assertTrue(reparsed.isSuccess)
        assertEquals(FsrsParameterParser.EXPECTED_COUNT, reparsed.getOrThrow().size)
    }

    // ── integration: SrsScheduler uses custom params ──────────────────────────

    @Test
    fun `SrsScheduler project with custom params differs from default for a new card`() {
        // w[2] is the initial stability for a Good rating on a New card in FSRS-6.
        // Changing it substantially guarantees a different stability outcome.
        val customParams = DEFAULT_21.toMutableList().also { it[2] = 9.0 }

        val now = 1_000_000_000_000L
        val card = com.cardpop.app.data.entity.FlashcardEntity(
            id = 1L, categoryId = 0L,
            question = "Q", answer = "A", isEnabled = true
        )

        val resultDefault = com.cardpop.app.domain.usecase.SrsScheduler.project(
            flashcard = card,
            rating = com.cardpop.app.domain.model.FlashcardRating.GOOD,
            now = now,
            requestRetention = 0.9,
            params = DEFAULT_21
        )
        val resultCustom = com.cardpop.app.domain.usecase.SrsScheduler.project(
            flashcard = card,
            rating = com.cardpop.app.domain.model.FlashcardRating.GOOD,
            now = now,
            requestRetention = 0.9,
            params = customParams
        )

        assertNotNull(resultDefault)
        assertNotNull(resultCustom)
        // Stability should differ because w0 affects initial stability for a New card.
        assertTrue(
            "Custom params should produce different stability than defaults",
            resultDefault!!.stability != resultCustom!!.stability
        )
    }
}
