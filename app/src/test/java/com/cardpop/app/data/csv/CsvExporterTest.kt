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

import com.cardpop.app.data.entity.FlashcardEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Unit tests for CsvExporter.
 * Covers RFC 4180 compliance, BOM writing, category column support, and edge cases.
 */
class CsvExporterTest {

    private val exporter = CsvExporter()

    /** Strips the UTF-8 BOM character from the start of a string if present. */
    private fun String.stripBom(): String = this.removePrefix("\uFEFF")

    // -- Basic Export --

    @Test
    fun `export produces correct header and rows`() {
        val flashcards = listOf(
            createFlashcard(id = 1, question = "Q1", answer = "A1"),
            createFlashcard(id = 2, question = "Q2", answer = "A2")
        )

        val output = ByteArrayOutputStream()
        exporter.export(flashcards, output)

        val csv = output.toString(Charsets.UTF_8)
        val lines = csv.lines()

        assertEquals("question,answer", lines[0].stripBom())
        assertEquals("Q1,A1", lines[1])
        assertEquals("Q2,A2", lines[2])
    }

    @Test
    fun `exportToString produces same content as export`() {
        val flashcards = listOf(
            createFlashcard(id = 1, question = "Q1", answer = "A1")
        )

        val streamOutput = ByteArrayOutputStream()
        exporter.export(flashcards, streamOutput)
        val streamContent = streamOutput.toString(Charsets.UTF_8)

        val stringContent = exporter.exportToString(flashcards)

        // Both should include BOM, so they should match exactly
        assertEquals(streamContent, stringContent)
    }

    // -- BOM --

    @Test
    fun `export writes UTF-8 BOM at start`() {
        val flashcards = listOf(createFlashcard(id = 1, question = "Q", answer = "A"))

        val output = ByteArrayOutputStream()
        exporter.export(flashcards, output)
        val bytes = output.toByteArray()

        assertEquals(0xEF.toByte(), bytes[0])
        assertEquals(0xBB.toByte(), bytes[1])
        assertEquals(0xBF.toByte(), bytes[2])
    }

    // -- Field Escaping --

    @Test
    fun `fields with commas are quoted`() {
        val flashcards = listOf(
            createFlashcard(id = 1, question = "What is, well", answer = "Good, actually")
        )

        val output = ByteArrayOutputStream()
        exporter.export(flashcards, output)

        val csv = output.toString(Charsets.UTF_8).trimStart { it == '\uFEFF' }
        assertTrue(csv.contains("\"What is, well\""))
        assertTrue(csv.contains("\"Good, actually\""))
    }

    @Test
    fun `fields with quotes are escaped with double quotes`() {
        val flashcards = listOf(
            createFlashcard(id = 1, question = "Say \"hi\"", answer = "OK")
        )

        val output = ByteArrayOutputStream()
        exporter.export(flashcards, output)

        val csv = output.toString(Charsets.UTF_8).trimStart { it == '\uFEFF' }
        assertTrue(csv.contains("\"Say \"\"hi\"\"\""))
    }

    @Test
    fun `fields with newlines are quoted`() {
        val flashcards = listOf(
            createFlashcard(id = 1, question = "Line 1\nLine 2", answer = "Answer")
        )

        val output = ByteArrayOutputStream()
        exporter.export(flashcards, output)

        val csv = output.toString(Charsets.UTF_8).trimStart { it == '\uFEFF' }
        assertTrue(csv.contains("\"Line 1\nLine 2\""))
    }

    @Test
    fun `fields with carriage returns are quoted`() {
        val flashcards = listOf(
            createFlashcard(id = 1, question = "Line 1\rLine 2", answer = "Answer")
        )

        val output = ByteArrayOutputStream()
        exporter.export(flashcards, output)

        val csv = output.toString(Charsets.UTF_8).trimStart { it == '\uFEFF' }
        assertTrue(csv.contains("\"Line 1\rLine 2\""))
    }

    @Test
    fun `plain fields without special chars are not quoted`() {
        val flashcards = listOf(
            createFlashcard(id = 1, question = "Hello", answer = "World")
        )

        val output = ByteArrayOutputStream()
        exporter.export(flashcards, output)

        val csv = output.toString(Charsets.UTF_8).trimStart { it == '\uFEFF' }
        assertTrue(csv.contains("Hello,World"))
        assertFalse(csv.contains("\"Hello\""))
    }

    // -- Category Column --

    @Test
    fun `export with category includes category header and column`() {
        val flashcards = listOf(
            createFlashcard(id = 1, question = "Q1", answer = "A1", categoryId = 10),
            createFlashcard(id = 2, question = "Q2", answer = "A2", categoryId = 20)
        )

        val categoryMap = mapOf(10L to "Science", 20L to "Math")
        val config = CsvExporter.ExportConfig(
            includeCategory = true,
            categoryResolver = { flashcard -> categoryMap[flashcard.categoryId] }
        )

        val output = ByteArrayOutputStream()
        exporter.export(flashcards, output, config)

        val csv = output.toString(Charsets.UTF_8)
        val lines = csv.lines()

        assertEquals("question,answer,category", lines[0].stripBom())
        assertEquals("Q1,A1,Science", lines[1])
        assertEquals("Q2,A2,Math", lines[2])
    }

    @Test
    fun `exportToString with category includes category column`() {
        val flashcards = listOf(
            createFlashcard(id = 1, question = "Q1", answer = "A1", categoryId = 5)
        )

        val config = CsvExporter.ExportConfig(
            includeCategory = true,
            categoryResolver = { "History" }
        )

        val csv = exporter.exportToString(flashcards, config)
        val lines = csv.lines()

        // First line has BOM + header
        assertEquals("\uFEFFquestion,answer,category", lines[0])
        assertEquals("Q1,A1,History", lines[1])
    }

    @Test
    fun `export with category handles missing category gracefully`() {
        val flashcards = listOf(
            createFlashcard(id = 1, question = "Q1", answer = "A1", categoryId = 99)
        )

        val config = CsvExporter.ExportConfig(
            includeCategory = true,
            categoryResolver = { null }
        )

        val output = ByteArrayOutputStream()
        exporter.export(flashcards, output, config)

        val csv = output.toString(Charsets.UTF_8).trimStart { it == '\uFEFF' }
        val lines = csv.lines()

        assertEquals("question,answer,category", lines[0])
        assertEquals("Q1,A1,", lines[1])
    }

    // -- Edge Cases --

    @Test
    fun `empty list exports with only header`() {
        val flashcards = emptyList<FlashcardEntity>()

        val output = ByteArrayOutputStream()
        exporter.export(flashcards, output)

        val csv = output.toString(Charsets.UTF_8).trimStart { it == '\uFEFF' }
        assertEquals("question,answer\n", csv)
    }

    @Test
    fun `export with empty fields handles gracefully`() {
        val flashcards = listOf(
            createFlashcard(id = 1, question = "", answer = "")
        )

        val output = ByteArrayOutputStream()
        exporter.export(flashcards, output)

        val csv = output.toString(Charsets.UTF_8).trimStart { it == '\uFEFF' }
        val lines = csv.lines()

        assertEquals("question,answer", lines[0])
        assertEquals(",", lines[1])
    }

    // -- Helper --

    private fun createFlashcard(
        id: Long = 0,
        question: String,
        answer: String,
        categoryId: Long = 1
    ): FlashcardEntity {
        return FlashcardEntity(
            id = id,
            categoryId = categoryId,
            question = question,
            answer = answer,
            questionImagePath = null,
            answerImagePath = null,
            isEnabled = true
        )
    }
}
