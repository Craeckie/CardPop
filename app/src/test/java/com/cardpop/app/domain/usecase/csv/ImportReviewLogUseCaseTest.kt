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
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportReviewLogUseCaseTest {

    private fun makeUseCase(existing: List<ReviewLogEntity> = emptyList()): Pair<ImportReviewLogUseCase, FakeReviewLogDao> {
        val dao = FakeReviewLogDao(existing.toMutableList())
        return ImportReviewLogUseCase(dao) to dao
    }

    @Test
    fun `imports rows with header`() = runBlocking {
        val csv = "card_id,review_time,review_rating,review_state,review_duration\n42,1700000000000,3,0,0\n7,1700000001000,1,2,500\n"
        val (useCase, dao) = makeUseCase()
        val result = useCase(csv.byteInputStream())
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow())
        assertEquals(2, dao.rows.size)
        assertEquals(42L, dao.rows[0].flashcardId)
        assertEquals(1_700_000_000_000L, dao.rows[0].reviewedAt)
        assertEquals(3, dao.rows[0].rating)
        assertEquals(0, dao.rows[0].stateBefore)
    }

    @Test
    fun `imports rows without header when first field is numeric`() = runBlocking {
        val csv = "42,1700000000000,3,0,0\n7,1700000001000,1,2,500\n"
        val (useCase, dao) = makeUseCase()
        val result = useCase(csv.byteInputStream())
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow())
        assertEquals(2, dao.rows.size)
    }

    @Test
    fun `strips leading BOM`() = runBlocking {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val csv = "card_id,review_time,review_rating,review_state,review_duration\n1,1000,3,0,0\n"
        val (useCase, dao) = makeUseCase()
        val result = useCase((bom + csv.toByteArray()).inputStream())
        assertTrue(result.isSuccess)
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `idempotent reimport returns 0 new rows`() = runBlocking {
        val csv = "card_id,review_time,review_rating,review_state,review_duration\n42,1700000000000,3,0,0\n"
        val existing = listOf(
            ReviewLogEntity(id = 1, flashcardId = 42, reviewedAt = 1_700_000_000_000, rating = 3, stateBefore = 0)
        )
        val (useCase, _) = makeUseCase(existing)
        val result = useCase(csv.byteInputStream())
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow())
    }

    @Test
    fun `malformed line returns failure`() = runBlocking {
        val csv = "card_id,review_time,review_rating,review_state,review_duration\nBAD_LINE\n"
        val (useCase, _) = makeUseCase()
        val result = useCase(csv.byteInputStream())
        assertTrue(result.isFailure)
    }

    @Test
    fun `ignores review_duration column`() = runBlocking {
        val csv = "card_id,review_time,review_rating,review_state,review_duration\n5,2000,2,1,9999\n"
        val (useCase, dao) = makeUseCase()
        val result = useCase(csv.byteInputStream())
        assertTrue(result.isSuccess)
        assertEquals(1, dao.rows.size)
        assertEquals(5L, dao.rows[0].flashcardId)
        assertEquals(2000L, dao.rows[0].reviewedAt)
        assertEquals(2, dao.rows[0].rating)
        assertEquals(1, dao.rows[0].stateBefore)
    }

    @Test
    fun `empty file returns 0`() = runBlocking {
        val (useCase, _) = makeUseCase()
        val result = useCase("".byteInputStream())
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow())
    }
}
