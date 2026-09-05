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
 * - back  (`answer`)    = pinyin converted to tone marks + every sense of the
 *                         definition (examples dropped), joined by newlines.
 *                         Either part may be absent.
 * - category            = null (user picks a target category in the preview UI,
 *                         matching the current Anki import behaviour).
 *
 * Cards with no headword, or with neither pinyin nor definition, are recorded
 * as [CsvParseError] entries and excluded from [CsvParseResult.validCards].
 */
class PlecoXmlParser {

    /**
     * Runs of CJK ideographs and CJK/fullwidth punctuation. Inside a Pleco definition
     * these are always embedded example sentences, never part of the gloss.
     */
    private val CJK_RUN = Regex("[\\u3000-\\u303F\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uFF00-\\uFFEF]+")

    /**
     * Lowercase part-of-speech labels Pleco uses to introduce a new sense. Matched
     * case-sensitively so a capitalised "Noun" inside an example translation does not
     * masquerade as a sense. Multi-word labels come first so they win the alternation.
     */
    private val POS_MARKER = Regex(
        "\\b(?:proper noun|measure word|auxiliary verb|place name|abbreviation|" +
            "onomatopoeia|interjection|conjunction|preposition|adjective|adverb|" +
            "pronoun|numeral|particle|surname|prefix|suffix|phrase|idiom|noun|verb)\\b"
    )

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
                ?.let { parseDefinition(it) }
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
     * Turn a raw `<defn>` into the card's definition text.
     *
     * Each source line is normalised and reduced to its senses; the senses of all lines
     * are joined with newlines. Line breaks Pleco put in the definition (typical of the
     * bulleted German dictionaries) therefore survive, while the whitespace inside a
     * line is collapsed.
     */
    private fun parseDefinition(raw: String): String =
        raw.lineSequence()
            .map { normalize(it) }
            .filter { it.isNotBlank() }
            .flatMap { extractSenses(it).asSequence() }
            .joinToString("\n")

    /**
     * Split one line into its senses.
     *
     * Pleco interleaves senses and examples: `noun permission 得到家长的允许 dédào …
     * gain permission from one’s parents verb permit; allow 不允许… `. Splitting on CJK
     * runs leaves the first sense as the leading segment, and every later segment as
     * `<example pinyin> <example translation>` optionally followed by the next sense —
     * which always begins at a part-of-speech label. So the last [POS_MARKER] match in
     * a later segment marks where that segment's sense starts; segments with no match
     * are pure example text and are dropped.
     *
     * If nothing survives — a definition that opens with Hanzi — the whole line is kept.
     */
    private fun extractSenses(line: String): List<String> {
        val segments = line.split(CJK_RUN)
        val senses = mutableListOf<String>()

        cleanSense(segments.first())?.let { senses.add(it) }

        for (segment in segments.drop(1)) {
            val marker = POS_MARKER.findAll(segment).lastOrNull() ?: continue
            if (segment.substring(marker.range.last + 1).isBlank()) continue
            cleanSense(segment.substring(marker.range.first))?.let { senses.add(it) }
        }

        return senses.ifEmpty { listOf(line) }
    }

    /**
     * Strip the separator characters and dangling cross-reference ids left behind when
     * a sense is cut out of a longer definition. Returns null if nothing is left.
     */
    private fun cleanSense(raw: String): String? {
        val cleaned = raw.trim()
            .trimEnd('∼', ';', ',', '(', '·', '•', '-', ' ')
            .replace(Regex("\\s+\\d+$"), "")
            .trim()
        return cleaned.ifBlank { null }
    }
}
