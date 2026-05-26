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
import java.io.OutputStream
import java.io.OutputStreamWriter
import javax.inject.Inject

class ExportReviewLogUseCase @Inject constructor(
    private val reviewLogDao: ReviewLogDao
) {
    suspend operator fun invoke(out: OutputStream): Result<Int> = runCatching {
        val rows = reviewLogDao.getAllOrdered()
        // No BOM — a leading BOM breaks the card_id header for pandas/csv consumers.
        OutputStreamWriter(out, Charsets.UTF_8).use { writer ->
            writer.write("card_id,review_time,review_rating,review_state,review_duration\n")
            rows.forEach { row ->
                writer.write("${row.flashcardId},${row.reviewedAt},${row.rating},${row.stateBefore},0\n")
            }
        }
        rows.size
    }
}
