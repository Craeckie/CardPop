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

package com.floflacards.app.domain.usecase.csv

import com.floflacards.app.data.csv.CsvExporter
import com.floflacards.app.data.dao.CategoryDao
import com.floflacards.app.data.dao.FlashcardDao
import com.floflacards.app.data.entity.FlashcardEntity
import java.io.OutputStream
import javax.inject.Inject

/**
 * Use case for exporting ALL categories to a single CSV file.
 *
 * Format: `question,answer,category` — includes the category column so that
 * the exported file can be re-imported with category information preserved.
 */
class BulkExportCsvUseCase @Inject constructor(
    private val flashcardDao: FlashcardDao,
    private val categoryDao: CategoryDao,
    private val csvExporter: CsvExporter
) {

    suspend operator fun invoke(outputStream: OutputStream): Result<Int> {
        return try {
            val categories = categoryDao.getAllCategoriesForBackup()

            if (categories.isEmpty()) {
                return Result.success(0)
            }

            val allFlashcards = flashcardDao.getAllFlashcardsForStatistics()

            if (allFlashcards.isEmpty()) {
                return Result.success(0)
            }

            // Build a category ID -> name lookup map
            val categoryMap = categories.associate { it.id to it.name }

            // Create a resolver that maps FlashcardEntity to its category name
            val categoryResolver: (FlashcardEntity) -> String? = { flashcard ->
                categoryMap[flashcard.categoryId]
            }

            val config = CsvExporter.ExportConfig(
                includeCategory = true,
                categoryResolver = categoryResolver
            )

            csvExporter.export(allFlashcards, outputStream, config)
            Result.success(allFlashcards.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
