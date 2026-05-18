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

import com.cardpop.app.data.csv.CsvExporter
import com.cardpop.app.data.dao.FlashcardDao
import java.io.OutputStream
import javax.inject.Inject

/**
 * Use case for exporting flashcards from a single category to CSV format.
 */
class ExportCsvUseCase @Inject constructor(
    private val flashcardDao: FlashcardDao,
    private val csvExporter: CsvExporter
) {

    /**
     * Exports all flashcards from a category to the given OutputStream.
     *
     * @param categoryId The category to export
     * @param outputStream Where to write the CSV
     * @return The number of flashcards exported
     */
    suspend operator fun invoke(
        categoryId: Long,
        outputStream: OutputStream
    ): Result<Int> {
        return try {
            val flashcards = flashcardDao.getFlashcardsByCategorySync(categoryId)

            if (flashcards.isEmpty()) {
                return Result.success(0)
            }

            // Single category export — no category column needed
            val config = CsvExporter.ExportConfig(includeCategory = false)
            csvExporter.export(flashcards, outputStream, config)
            Result.success(flashcards.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Returns the CSV content as a string for preview or sharing.
     */
    suspend fun exportToString(categoryId: Long): Result<String> {
        return try {
            val flashcards = flashcardDao.getFlashcardsByCategorySync(categoryId)

            if (flashcards.isEmpty()) {
                return Result.failure(IllegalStateException("No flashcards found in category"))
            }

            val config = CsvExporter.ExportConfig(includeCategory = false)
            Result.success(csvExporter.exportToString(flashcards, config))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
