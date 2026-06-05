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

package com.cardpop.app.data.pleco

import com.cardpop.app.data.csv.CsvFlashcard
import com.cardpop.app.data.csv.CsvParseError
import com.cardpop.app.data.csv.CsvParseResult
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses Pleco flashcard XML exports (the `<plecoflash>` format) and produces
 * [CsvParseResult] objects compatible with the existing CSV/Anki import pipeline.
 *
 * Field mapping:
 * - front (`question`)  = simplified headword (`<headword charset="sc">`),
 *                         falling back to traditional if no simplified present.
 * - back  (`answer`)    = pinyin converted to tone marks + trimmed definition,
 *                         joined by a newline. Either part may be absent.
 * - category            = null (user picks a target category in the preview UI,
 *                         matching the current Anki import behaviour).
 *
 * Cards with no headword, or with neither pinyin nor definition, are recorded
 * as [CsvParseError] entries and excluded from [CsvParseResult.validCards].
 */
class PlecoXmlParser {

    /** Regex matching the start of CJK Unified Ideographs (Basic + Extension A). */
    private val CJK_PATTERN = Regex("[㐀-鿿]")

    /**
     * Parse a Pleco XML export from [inputStream].
     *
     * The stream is consumed fully. It does not need to be closed by the caller
     * (wrapping in DocumentBuilder handles it), but the caller may close it
     * afterwards as a best-effort courtesy.
     */
    fun parse(inputStream: InputStream): CsvParseResult {
        val validCards = mutableListOf<CsvFlashcard>()
        val errors = mutableListOf<CsvParseError>()

        val factory = DocumentBuilderFactory.newInstance().also {
            it.isNamespaceAware = false
            it.isExpandEntityReferences = true
        }
        val doc = factory.newDocumentBuilder().parse(inputStream)

        val cards = doc.getElementsByTagName("card")
        for (i in 0 until cards.length) {
            val card = cards.item(i) as? Element ?: continue
            val entry = card.firstChildElement("entry")

            // Require a simplified headword; fall back to traditional
            val headword = entry?.headword("sc") ?: entry?.headword("tc")
            if (headword.isNullOrBlank()) {
                errors.add(CsvParseError(i + 1, "", "Card has no headword"))
                continue
            }

            val pinyin = entry?.firstChildElement("pron")
                ?.textContent
                ?.let { PinyinConverter.toToneMarks(normalize(it)) }
                .orEmpty()

            val defn = entry?.firstChildElement("defn")
                ?.textContent
                ?.let { trimDefinition(normalize(it)) }
                .orEmpty()

            val answer = listOf(pinyin, defn).filter { it.isNotBlank() }.joinToString("\n")
            if (answer.isBlank()) {
                errors.add(CsvParseError(i + 1, headword, "Card has no pronunciation or definition"))
                continue
            }

            validCards.add(
                CsvFlashcard(
                    question = normalize(headword),
                    answer = answer,
                    category = null
                )
            )
        }

        return CsvParseResult(validCards, errors)
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Return the text of the first `<headword>` child element with the given
     * `charset` attribute value (e.g. "sc" or "tc").
     */
    private fun Element.headword(charset: String): String? {
        val children = childNodes
        for (j in 0 until children.length) {
            val child = children.item(j)
            if (child.nodeType == Node.ELEMENT_NODE &&
                child.nodeName == "headword" &&
                (child as Element).getAttribute("charset") == charset
            ) {
                return child.textContent.takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    /**
     * Return the first direct child [Element] with [tagName], or null.
     * Uses direct children only (not deep search) so lookups stay within the
     * current `<card>` or `<entry>` scope.
     */
    private fun Element.firstChildElement(tagName: String): Element? {
        val children = childNodes
        for (j in 0 until children.length) {
            val child = children.item(j)
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == tagName) {
                return child as Element
            }
        }
        return null
    }

    /** Collapse whitespace runs to a single space and trim. */
    private fun normalize(s: String): String = s.replace(Regex("\\s+"), " ").trim()

    /**
     * Trim a Pleco definition to its core meaning by cutting at the first CJK
     * character (which marks the start of an embedded example sentence).
     *
     * If the definition contains no CJK characters (e.g. a single-line English
     * gloss or a German/numbered entry), the full normalized text is kept.
     * If the text before the first CJK character is blank (rare — definition
     * starts directly with Hanzi), the full text is also kept.
     *
     * After the cut, trailing separator characters and dangling cross-reference
     * IDs (bare digit strings) are stripped.
     */
    private fun trimDefinition(raw: String): String {
        val match = CJK_PATTERN.find(raw) ?: return raw   // no Hanzi → keep all
        val head = raw.substring(0, match.range.first)
            .trim()
            .trimEnd('∼', ';', ',', '(', '·', '•', '-', ' ')
            .replace(Regex("\\s+\\d+$"), "")              // drop trailing cross-ref id
            .trim()
        return head.ifBlank { raw }                        // started with Hanzi → keep all
    }
}
