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

package com.floflacards.app.data.csv

import com.floflacards.app.data.entity.FlashcardEntity
import java.io.OutputStream
import java.io.OutputStreamWriter

/**
 * Exports flashcards to CSV format following RFC 4180.
 *
 * Supports two modes:
 * - Simple mode (default): `question,answer`
 * - Extended mode: `question,answer,category` (for bulk exports)
 *
 * Produces UTF-8 CSV with BOM for Excel compatibility.
 */
class CsvExporter {

    companion object {
        const val HEADER_QUESTION = "question"
        const val HEADER_ANSWER = "answer"
        const val HEADER_CATEGORY = "category"
        const val MIME_TYPE = "text/csv"
        const val FILE_EXTENSION = "csv"

        // Characters that require field quoting per RFC 4180
        private val QUOTE_TRIGGER_CHARS = setOf(',', '"', '\n', '\r')

        // UTF-8 BOM character as a string for use in StringBuilder
        private const val UTF8_BOM_CHAR = "\uFEFF"
    }

    /**
     * Configuration for what columns to include in the export.
     */
    data class ExportConfig(
        val includeCategory: Boolean = false,
        val categoryResolver: ((FlashcardEntity) -> String?)? = null
    )

    // UTF-8 BOM bytes written at the start of every export
    private val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    /**
     * Exports flashcards to a CSV OutputStream.
     * Includes header row. Uses UTF-8 with BOM.
     *
     * @param flashcards The flashcards to export
     * @param outputStream Where to write the CSV
     * @param config Optional configuration for extended export modes
     */
    fun export(
        flashcards: List<FlashcardEntity>,
        outputStream: OutputStream,
        config: ExportConfig = ExportConfig()
    ) {
        // Write UTF-8 BOM for Excel compatibility
        outputStream.write(utf8Bom)

        OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
            writeHeader(writer, config)
            writeRows(writer, flashcards, config)
            writer.flush()
        }
    }

    /**
     * Exports flashcards to a CSV string.
     * Includes UTF-8 BOM for consistency with the stream-based export.
     * Useful for preview or sharing small exports.
     */
    fun exportToString(
        flashcards: List<FlashcardEntity>,
        config: ExportConfig = ExportConfig()
    ): String {
        return buildString {
            // Write UTF-8 BOM for consistency with stream export
            append(UTF8_BOM_CHAR)
            appendHeader(config)
            appendRows(flashcards, config)
        }
    }

    // -- Internal Write Methods --

    private fun writeHeader(writer: OutputStreamWriter, config: ExportConfig) {
        writer.append(HEADER_QUESTION)
        writer.append(',')
        writer.append(HEADER_ANSWER)

        if (config.includeCategory) {
            writer.append(',')
            writer.append(HEADER_CATEGORY)
        }

        writer.append('\n')
    }

    private fun writeRows(
        writer: OutputStreamWriter,
        flashcards: List<FlashcardEntity>,
        config: ExportConfig
    ) {
        for (flashcard in flashcards) {
            writer.append(escapeCsvField(flashcard.question))
            writer.append(',')
            writer.append(escapeCsvField(flashcard.answer))

            if (config.includeCategory) {
                val category = config.categoryResolver?.invoke(flashcard).orEmpty()
                writer.append(',')
                writer.append(escapeCsvField(category))
            }

            writer.append('\n')
        }
    }

    // -- Internal String Building Methods --

    private fun StringBuilder.appendHeader(config: ExportConfig) {
        append(HEADER_QUESTION).append(',').append(HEADER_ANSWER)

        if (config.includeCategory) {
            append(',').append(HEADER_CATEGORY)
        }

        append('\n')
    }

    private fun StringBuilder.appendRows(
        flashcards: List<FlashcardEntity>,
        config: ExportConfig
    ) {
        for (flashcard in flashcards) {
            append(escapeCsvField(flashcard.question))
            append(',')
            append(escapeCsvField(flashcard.answer))

            if (config.includeCategory) {
                val category = config.categoryResolver?.invoke(flashcard).orEmpty()
                append(',')
                append(escapeCsvField(category))
            }

            append('\n')
        }
    }

    // -- Field Escaping --

    /**
     * Escapes a field for CSV output following RFC 4180.
     * Wraps in quotes if the field contains commas, quotes, newlines, or carriage returns.
     * Internal quotes are doubled ("").
     */
    private fun escapeCsvField(field: String): String {
        if (field.isEmpty()) return field

        val needsQuoting = field.any { it in QUOTE_TRIGGER_CHARS }

        return if (needsQuoting) {
            val escaped = field.replace("\"", "\"\"")
            "\"$escaped\""
        } else {
            field
        }
    }
}
