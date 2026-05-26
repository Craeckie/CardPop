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
import java.io.InputStream
import javax.inject.Inject

class ImportReviewLogUseCase @Inject constructor(
    private val reviewLogDao: ReviewLogDao
) {
    suspend operator fun invoke(input: InputStream): Result<Int> = runCatching {
        var text = input.bufferedReader(Charsets.UTF_8).readText()
        if (text.startsWith("﻿")) text = text.substring(1)

        val lines = text.lines().filter { it.isNotBlank() }
        val dataLines = if (lines.isNotEmpty() && !lines[0].first().isDigit()) {
            lines.drop(1)
        } else {
            lines
        }

        val rows = mutableListOf<ReviewLogEntity>()
        for (line in dataLines) {
            val parts = line.split(",")
            if (parts.size < 4) {
                return Result.failure(IllegalArgumentException("Malformed line: $line"))
            }
            val flashcardId = parts[0].trim().toLongOrNull()
                ?: return Result.failure(IllegalArgumentException("Invalid card_id: ${parts[0]}"))
            val reviewedAt = parts[1].trim().toLongOrNull()
                ?: return Result.failure(IllegalArgumentException("Invalid review_time: ${parts[1]}"))
            val rating = parts[2].trim().toIntOrNull()
                ?: return Result.failure(IllegalArgumentException("Invalid review_rating: ${parts[2]}"))
            val stateBefore = parts[3].trim().toIntOrNull()
                ?: return Result.failure(IllegalArgumentException("Invalid review_state: ${parts[3]}"))
            rows.add(
                ReviewLogEntity(
                    flashcardId = flashcardId,
                    reviewedAt = reviewedAt,
                    rating = rating,
                    stateBefore = stateBefore
                )
            )
        }

        val rowIds = reviewLogDao.insertAll(rows)
        rowIds.count { it != -1L }
    }
}
