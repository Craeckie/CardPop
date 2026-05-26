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

import com.cardpop.app.data.dao.ReviewLogDao
import com.cardpop.app.data.entity.ReviewLogEntity

class FakeReviewLogDao(val rows: MutableList<ReviewLogEntity> = mutableListOf()) : ReviewLogDao {

    private var nextId = (rows.maxOfOrNull { it.id } ?: 0L) + 1

    private fun isDuplicate(row: ReviewLogEntity): Boolean =
        rows.any {
            it.flashcardId == row.flashcardId &&
                it.reviewedAt == row.reviewedAt &&
                it.rating == row.rating &&
                it.stateBefore == row.stateBefore
        }

    override suspend fun insert(row: ReviewLogEntity): Long {
        if (isDuplicate(row)) return -1L
        val stored = row.copy(id = nextId++)
        rows.add(stored)
        return stored.id
    }

    override suspend fun insertAll(rows: List<ReviewLogEntity>): List<Long> =
        rows.map { insert(it) }

    override suspend fun getAllOrdered(): List<ReviewLogEntity> =
        rows.sortedWith(compareBy({ it.reviewedAt }, { it.id }))

    override suspend fun count(): Int = rows.size
}
