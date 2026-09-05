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

package com.cardpop.app.data.importer

import com.cardpop.app.data.anki.AnkiParser
import com.cardpop.app.data.csv.CsvParseResult
import com.cardpop.app.data.csv.CsvParser
import com.cardpop.app.data.pleco.PlecoXmlParser
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream

/** Result of handing a file to [ImportFileParser]. */
sealed interface ImportParseOutcome {
    /** The file was recognised and parsed. [result] may still contain per-card errors. */
    data class Parsed(val format: ImportFormat, val result: CsvParseResult) : ImportParseOutcome

    /** The file is not something we can import. [format] says why. */
    data class Unsupported(val format: ImportFormat) : ImportParseOutcome
}

/**
 * Single entry point for import parsing: sniffs the leading bytes, rewinds the stream,
 * and hands it to the parser for the detected format.
 *
 * The stream is buffered and marked before sniffing so the chosen parser still sees the
 * file from byte zero. Callers own closing the stream they pass in.
 */
class ImportFileParser(
    private val ankiParser: AnkiParser,
    private val plecoXmlParser: PlecoXmlParser,
    private val csvParser: CsvParser
) {

    fun parse(input: InputStream, cacheDir: File): ImportParseOutcome {
        val buffered = BufferedInputStream(input)
        buffered.mark(ImportFormatDetector.HEADER_BYTES)
        val header = buffered.readHeader(ImportFormatDetector.HEADER_BYTES)
        buffered.reset()

        return when (val format = ImportFormatDetector.detect(header)) {
            ImportFormat.ANKI ->
                ImportParseOutcome.Parsed(format, ankiParser.parse(buffered, cacheDir))
            ImportFormat.PLECO_XML ->
                ImportParseOutcome.Parsed(format, plecoXmlParser.parse(buffered))
            ImportFormat.CSV ->
                ImportParseOutcome.Parsed(format, csvParser.parse(buffered))
            ImportFormat.UNSUPPORTED_XML, ImportFormat.UNSUPPORTED_BINARY ->
                ImportParseOutcome.Unsupported(format)
        }
    }

    /**
     * Read up to [max] bytes. Written as a manual loop on purpose:
     * `InputStream.readNBytes` requires API 33 and this app targets minSdk 24.
     */
    private fun InputStream.readHeader(max: Int): ByteArray {
        val buffer = ByteArray(max)
        var filled = 0
        while (filled < max) {
            val read = read(buffer, filled, max - filled)
            if (read <= 0) break
            filled += read
        }
        return buffer.copyOf(filled)
    }
}
