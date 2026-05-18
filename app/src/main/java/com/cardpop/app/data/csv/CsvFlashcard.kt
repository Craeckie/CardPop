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

package com.cardpop.app.data.csv

/**
 * Represents a single flashcard row parsed from a CSV file.
 *
 * Supports question, answer, and an optional category field.
 * Images are not included in CSV format (use JSON backup for full backup with images).
 */
data class CsvFlashcard(
    val question: String,
    val answer: String,
    val category: String? = null
)

/**
 * Result of parsing a CSV file.
 * Separates valid rows from errors so the UI can show partial imports.
 */
data class CsvParseResult(
    val validCards: List<CsvFlashcard>,
    val errors: List<CsvParseError>
)

/**
 * Represents a single row error during CSV parsing.
 */
data class CsvParseError(
    val rowNumber: Int,
    val rawLine: String,
    val reason: String
)

/**
 * Column mapping configuration for flexible CSV import.
 * Maps CSV column indices to flashcard fields.
 */
data class CsvColumnMapping(
    val questionColumn: Int,
    val answerColumn: Int,
    val categoryColumn: Int? = null
)

/**
 * Detected delimiter for a CSV/TSV file.
 */
enum class CsvDelimiter {
    COMMA,
    TAB
}

/**
 * Result of an import operation.
 */
data class CsvImportResult(
    val successCount: Int,
    val skippedCount: Int,
    val errorCount: Int,
    val errors: List<String> = emptyList()
)
