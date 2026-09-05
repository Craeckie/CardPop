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

/**
 * The kinds of file the import screen can be handed.
 *
 * [UNSUPPORTED_XML] and [UNSUPPORTED_BINARY] exist so the UI can say what is wrong
 * instead of falling back to the CSV parser, which happily turns arbitrary text into
 * plausible-looking but meaningless flashcards.
 */
enum class ImportFormat {
    ANKI,
    PLECO_XML,
    CSV,
    UNSUPPORTED_XML,
    UNSUPPORTED_BINARY
}

/**
 * Decides which importer a file belongs to by looking at its leading bytes.
 *
 * File names are deliberately not consulted: the SAF picker hands us a provider
 * document id rather than a display name for several common providers, and users
 * rename exports. Content sniffing is the only detection that survives both.
 */
object ImportFormatDetector {

    /** How many leading bytes [detect] needs. Callers must not pass more than this. */
    const val HEADER_BYTES: Int = 8192

    /** Local file header of a ZIP archive — an `.apkg` is a zip. */
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    fun detect(header: ByteArray): ImportFormat {
        if (header.size >= ZIP_MAGIC.size && ZIP_MAGIC.indices.all { header[it] == ZIP_MAGIC[it] }) {
            return ImportFormat.ANKI
        }

        val text = String(header, Charsets.UTF_8).removePrefix("\uFEFF").trimStart()
        if (text.startsWith("<")) {
            return if (text.contains("<plecoflash")) ImportFormat.PLECO_XML else ImportFormat.UNSUPPORTED_XML
        }

        if (header.any { it == 0.toByte() }) return ImportFormat.UNSUPPORTED_BINARY

        return ImportFormat.CSV
    }
}