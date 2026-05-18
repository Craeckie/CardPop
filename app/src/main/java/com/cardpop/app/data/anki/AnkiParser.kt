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

package com.cardpop.app.data.anki

import android.database.sqlite.SQLiteDatabase
import com.cardpop.app.data.csv.CsvFlashcard
import com.cardpop.app.data.csv.CsvParseError
import com.cardpop.app.data.csv.CsvParseResult
import org.json.JSONArray
import org.json.JSONObject
import com.github.luben.zstd.ZstdInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Parses Anki .apkg files and produces [CsvParseResult] objects
 * compatible with the existing CSV import pipeline.
 *
 * An .apkg file is a zip archive containing a SQLite database
 * (collection.anki2 or collection.anki21) with notes whose fields
 * are separated by \x1f. The first two fields of each note are
 * used as question and answer. HTML markup in fields is stripped.
 */
class AnkiParser {

    private data class AnkiModel(
        val name: String,
        val fieldNames: List<String>
    )

    /**
     * Parses an .apkg file from an [InputStream].
     *
     * @param inputStream the .apkg file content
     * @param cacheDir a writable directory for temporary extraction (e.g. context.cacheDir)
     * @param categoryOverride if non-null, all cards use this category instead of the Anki model name
     */
    fun parse(
        inputStream: InputStream,
        cacheDir: File,
        categoryOverride: String? = null
    ): CsvParseResult {
        val tmpDir = File(cacheDir, "anki_import_${System.currentTimeMillis()}")
        tmpDir.mkdirs()

        return try {
            extractZip(inputStream, tmpDir)

            val dbFile = findDatabase(tmpDir)
                ?: return CsvParseResult(
                    validCards = emptyList(),
                    errors = listOf(
                        CsvParseError(0, "", "No Anki database found in .apkg file")
                    )
                )

            readNotes(dbFile, categoryOverride)
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    // -- Zip extraction --

    private fun extractZip(inputStream: InputStream, destDir: File) {
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                // Guard against zip-slip: resolved path must be inside destDir
                val destFile = File(destDir, entry.name).canonicalFile
                if (!destFile.path.startsWith(destDir.canonicalPath + File.separator) &&
                    destFile.canonicalPath != destDir.canonicalPath
                ) {
                    throw SecurityException("Zip entry outside target directory: ${entry.name}")
                }

                if (entry.isDirectory) {
                    destFile.mkdirs()
                } else {
                    destFile.parentFile?.mkdirs()
                    destFile.outputStream().use { out -> zip.copyTo(out) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    // -- Database location --

    private fun findDatabase(dir: File): File? {
        // anki21b = newest (Zstandard-compressed), anki21 = newer, anki2 = oldest
        val compressed = File(dir, "collection.anki21b")
        if (compressed.exists()) {
            return decompressZstd(compressed)
        }
        for (name in listOf("collection.anki21", "collection.anki2")) {
            val file = File(dir, name)
            if (file.exists()) return file
        }
        return null
    }

    private fun decompressZstd(compressed: File): File {
        val decompressed = File(compressed.parentFile, "collection.anki21")
        compressed.inputStream().use { raw ->
            ZstdInputStream(raw).use { zstd ->
                decompressed.outputStream().use { out ->
                    zstd.copyTo(out)
                }
            }
        }
        return decompressed
    }

    // -- SQLite reading --

    private fun readNotes(dbFile: File, categoryOverride: String?): CsvParseResult {
        val db = SQLiteDatabase.openDatabase(
            dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
        )
        return try {
            val models = readModels(db)
            convertNotes(db, models, categoryOverride)
        } finally {
            db.close()
        }
    }

    /**
     * Reads Anki note-type models.
     * Newest Anki versions store model names in `notetypes` and field names in a separate `fields` table.
     * Older versions store field names as JSON in `notetypes.flds`.
     * Oldest versions store everything as JSON inside the `col` table.
     */
    private fun readModels(db: SQLiteDatabase): Map<Long, AnkiModel> {
        val models = mutableMapOf<Long, AnkiModel>()

        try {
            // Read model names from notetypes table
            db.rawQuery("SELECT id, name FROM notetypes", null).use { cursor ->
                while (cursor.moveToNext()) {
                    models[cursor.getLong(0)] = AnkiModel(cursor.getString(1), emptyList())
                }
            }

            // Try reading field names from separate fields table (newest format)
            val fieldsByModel = readFieldsTable(db)
            if (fieldsByModel != null) {
                for ((mid, fieldNames) in fieldsByModel) {
                    val existing = models[mid]
                    if (existing != null) {
                        models[mid] = existing.copy(fieldNames = fieldNames)
                    }
                }
            } else {
                // Try reading flds JSON column from notetypes (older format)
                readFieldsFromNotetypesFlds(db, models)
            }
        } catch (_: Exception) {
            // Oldest format: models JSON blob in col table
            readModelsFromColTable(db, models)
        }

        return models
    }

    /**
     * Reads field names from the separate `fields` table (newest Anki format).
     * Returns null if the table doesn't exist.
     */
    private fun readFieldsTable(db: SQLiteDatabase): Map<Long, List<String>>? {
        return try {
            val fields = mutableListOf<Triple<Long, Int, String>>()
            // Avoid ORDER BY on fields table — it may use the 'unicase' collation
            // which is not available in Android's SQLite. Sort in Kotlin instead.
            db.rawQuery("SELECT ntid, ord, name FROM fields", null).use { cursor ->
                while (cursor.moveToNext()) {
                    fields.add(Triple(cursor.getLong(0), cursor.getInt(1), cursor.getString(2)))
                }
            }
            fields.sortWith(compareBy({ it.first }, { it.second }))
            fields.groupBy({ it.first }, { it.third })
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Reads field names from the `flds` JSON column in the notetypes table (older format).
     */
    private fun readFieldsFromNotetypesFlds(db: SQLiteDatabase, models: MutableMap<Long, AnkiModel>) {
        try {
            db.rawQuery("SELECT id, flds FROM notetypes", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val mid = cursor.getLong(0)
                    val fldsJson = cursor.getString(1) ?: continue
                    val fieldNames = try {
                        val arr = JSONArray(fldsJson)
                        (0 until arr.length()).map { i ->
                            arr.getJSONObject(i).optString("name", "Field $i")
                        }
                    } catch (_: Exception) {
                        continue
                    }
                    val existing = models[mid]
                    if (existing != null) {
                        models[mid] = existing.copy(fieldNames = fieldNames)
                    }
                }
            }
        } catch (_: Exception) {
            // flds column doesn't exist — field names remain empty
        }
    }

    /**
     * Reads models from the `col` table JSON blob (oldest Anki format).
     */
    private fun readModelsFromColTable(db: SQLiteDatabase, models: MutableMap<Long, AnkiModel>) {
        try {
            db.rawQuery("SELECT models FROM col", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    val modelsJson = JSONObject(cursor.getString(0))
                    for (midStr in modelsJson.keys()) {
                        val m = modelsJson.getJSONObject(midStr)
                        val flds = m.optJSONArray("flds")
                        val fieldNames = if (flds != null) {
                            (0 until flds.length()).map { i ->
                                flds.getJSONObject(i).getString("name")
                            }
                        } else {
                            emptyList()
                        }
                        models[midStr.toLong()] = AnkiModel(
                            name = m.optString("name", ""),
                            fieldNames = fieldNames
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // No model info available — cards will use fallback category
        }
    }

    /**
     * Builds a field-name-to-index map for a model, enabling name-based field lookup.
     * Returns null if the model has no field names.
     */
    private fun buildFieldIndex(model: AnkiModel?): Map<String, Int>? {
        if (model == null || model.fieldNames.isEmpty()) return null
        return model.fieldNames.mapIndexed { index, name -> name to index }.toMap()
    }

    /**
     * Looks up a field value by name, strips HTML, and returns it.
     * Returns null if the field doesn't exist or is blank after stripping.
     */
    private fun getField(fields: List<String>, fieldIndex: Map<String, Int>, name: String): String? {
        val idx = fieldIndex[name] ?: return null
        if (idx >= fields.size) return null
        val value = stripHtml(fields[idx])
        return value.takeIf { it.isNotBlank() }
    }

    /**
     * Extracts question and answer from fields using the model's field names.
     *
     * Question: "Simplified" field.
     * Answer: combination of "Pinyin.1", "Meaning", "SentenceSimplified",
     *         "SentencePinyin.1", "SentenceMeaning" — separated by newlines.
     *
     * Falls back to fields[0]/fields[1] for models without these named fields
     * (e.g. the standard "Basic" model with "Front"/"Back").
     */
    private fun extractQuestionAnswer(
        fields: List<String>,
        fieldIndex: Map<String, Int>?
    ): Pair<String, String>? {
        if (fieldIndex == null) {
            // No field names — use positional fallback
            if (fields.size < 2) return null
            val q = stripHtml(fields[0])
            val a = stripHtml(fields[1])
            return if (q.isNotBlank() && a.isNotBlank()) q to a else null
        }

        // Try named field "Simplified" for question, fall back to "Front", then index 0
        val question = getField(fields, fieldIndex, "Simplified")
            ?: getField(fields, fieldIndex, "Front")
            ?: stripHtml(fields[0]).takeIf { it.isNotBlank() }
            ?: return null

        // Try combining named answer fields
        val answerParts = ANSWER_FIELD_NAMES.mapNotNull { name ->
            getField(fields, fieldIndex, name)
        }

        val answer = if (answerParts.isNotEmpty()) {
            answerParts.joinToString("\n")
        } else {
            // Fall back to "Back" or index 1
            getField(fields, fieldIndex, "Back")
                ?: fields.getOrNull(1)?.let { stripHtml(it) }?.takeIf { it.isNotBlank() }
        }

        return if (answer != null) question to answer else null
    }

    private fun convertNotes(
        db: SQLiteDatabase,
        models: Map<Long, AnkiModel>,
        categoryOverride: String?
    ): CsvParseResult {
        val validCards = mutableListOf<CsvFlashcard>()
        val errors = mutableListOf<CsvParseError>()
        var rowNumber = 0

        // Pre-build field index maps per model
        val fieldIndexes = models.mapValues { (_, model) -> buildFieldIndex(model) }

        db.rawQuery("SELECT mid, flds FROM notes", null).use { cursor ->
            while (cursor.moveToNext()) {
                rowNumber++
                val mid = cursor.getLong(0)
                val fldsStr = cursor.getString(1)

                val fields = fldsStr.split(ANKI_FIELD_SEPARATOR)

                if (fields.size < 2) {
                    errors.add(
                        CsvParseError(rowNumber, fldsStr.take(100), "Note has fewer than 2 fields")
                    )
                    continue
                }

                val fieldIndex = fieldIndexes[mid]
                val qa = extractQuestionAnswer(fields, fieldIndex)

                if (qa == null) {
                    errors.add(
                        CsvParseError(rowNumber, fldsStr.take(100), "Could not extract question/answer from fields")
                    )
                    continue
                }

                val (question, answer) = qa

                val category = categoryOverride
                    ?: models[mid]?.name?.takeIf { it.isNotBlank() }
                    ?: "Imported"

                validCards.add(CsvFlashcard(question = question, answer = answer, category = category))
            }
        }

        return CsvParseResult(validCards, errors)
    }

    companion object {
        private const val ANKI_FIELD_SEPARATOR = "\u001f"

        private val ANSWER_FIELD_NAMES = listOf(
            "Pinyin.1",
            "Meaning",
            "SentenceSimplified",
            "SentencePinyin.1",
            "SentenceMeaning"
        )

        private val BR_REGEX = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
        private val DIV_OPEN_REGEX = Regex("<div[^>]*>", RegexOption.IGNORE_CASE)
        private val TAG_REGEX = Regex("<[^>]+>")
        private val MULTI_NEWLINE_REGEX = Regex("\\n{3,}")
        private val NUMERIC_ENTITY_REGEX = Regex("&#(\\d+);")
        private val HEX_ENTITY_REGEX = Regex("&#x([0-9a-fA-F]+);")

        /**
         * Strips HTML tags and decodes entities, matching the Python script's strip_html().
         */
        fun stripHtml(text: String): String {
            var result = BR_REGEX.replace(text, "\n")
            result = DIV_OPEN_REGEX.replace(result, "\n")
            result = TAG_REGEX.replace(result, "")
            result = unescapeHtmlEntities(result)
            result = MULTI_NEWLINE_REGEX.replace(result, "\n\n")
            return result.trim()
        }

        private fun unescapeHtmlEntities(text: String): String {
            var result = text
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")

            result = NUMERIC_ENTITY_REGEX.replace(result) {
                val codePoint = it.groupValues[1].toIntOrNull()
                if (codePoint != null && Character.isValidCodePoint(codePoint)) {
                    String(Character.toChars(codePoint))
                } else {
                    it.value
                }
            }

            result = HEX_ENTITY_REGEX.replace(result) {
                val codePoint = it.groupValues[1].toIntOrNull(16)
                if (codePoint != null && Character.isValidCodePoint(codePoint)) {
                    String(Character.toChars(codePoint))
                } else {
                    it.value
                }
            }

            return result
        }
    }
}
