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

package com.cardpop.app.domain.usecase.csv

import com.cardpop.app.data.entity.ReviewLogEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class ExportReviewLogUseCaseTest {

    private fun makeUseCase(rows: List<ReviewLogEntity>) =
        ExportReviewLogUseCase(FakeReviewLogDao(rows.toMutableList()))

    @Test
    fun `exports correct header`() = runBlocking {
        val out = ByteArrayOutputStream()
        makeUseCase(emptyList())(out)
        val csv = out.toString(Charsets.UTF_8.name())
        assertTrue(csv.startsWith("card_id,review_time,review_rating,review_state,review_duration\n"))
    }

    @Test
    fun `exports no data rows when empty`() = runBlocking {
        val out = ByteArrayOutputStream()
        val result = makeUseCase(emptyList())(out)
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow())
        val lines = out.toString(Charsets.UTF_8.name()).lines().filter { it.isNotBlank() }
        assertEquals(1, lines.size) // header only
    }

    @Test
    fun `exports correct row values`() = runBlocking {
        val rows = listOf(
            ReviewLogEntity(id = 1, flashcardId = 42, reviewedAt = 1_700_000_000_000, rating = 3, stateBefore = 0),
            ReviewLogEntity(id = 2, flashcardId = 7, reviewedAt = 1_700_000_001_000, rating = 1, stateBefore = 2)
        )
        val out = ByteArrayOutputStream()
        val result = makeUseCase(rows)(out)
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow())
        val lines = out.toString(Charsets.UTF_8.name()).lines().filter { it.isNotBlank() }
        assertEquals(3, lines.size) // header + 2 data rows
        assertEquals("42,1700000000000,3,0,0", lines[1])
        assertEquals("7,1700000001000,1,2,0", lines[2])
    }

    @Test
    fun `exports review_duration as 0`() = runBlocking {
        val rows = listOf(
            ReviewLogEntity(id = 1, flashcardId = 1, reviewedAt = 1000, rating = 4, stateBefore = 1)
        )
        val out = ByteArrayOutputStream()
        makeUseCase(rows)(out)
        val dataLine = out.toString(Charsets.UTF_8.name()).lines()[1]
        assertTrue(dataLine.endsWith(",0"))
    }

    @Test
    fun `output has no BOM`() = runBlocking {
        val out = ByteArrayOutputStream()
        makeUseCase(emptyList())(out)
        val bytes = out.toByteArray()
        // UTF-8 BOM is EF BB BF
        assertFalse(bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte())
    }
}
