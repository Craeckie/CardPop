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

/**
 * Converts Pleco-style numbered pinyin (e.g. "lian4jie1") to tone-mark diacritics ("liánjiē").
 *
 * Rules:
 * - Tone digits 1–4 place a diacritic on the tone-bearing vowel of the preceding syllable.
 * - Tone 5 (or 0) = neutral tone — no diacritic.
 * - Separable-word markers "//" are stripped.
 * - Multi-word input (space-separated) is handled word-by-word.
 * - 'v' and 'u:' are normalised to 'ü' before marking.
 * - Diacritic placement: a/e first; then the o in "ou"; then the last vowel.
 */
object PinyinConverter {

    /** Tone-mark characters indexed [tone-1] (0=tone1 … 3=tone4) for each vowel. */
    private val TONE_MARKS: Map<Char, String> = mapOf(
        'a' to "āáǎà",
        'e' to "ēéěè",
        'i' to "īíǐì",
        'o' to "ōóǒò",
        'u' to "ūúǔù",
        'ü' to "ǖǘǚǜ"
    )

    /**
     * Convert numbered pinyin to tone-mark pinyin.
     *
     * Examples:
     *   "lian4jie1"          → "liánjiē"
     *   "li3lun4shang5"      → "lǐlùnshang"
     *   "fa1//yan2"          → "fāyán"
     *   "qian2duan1 kuang4jia4" → "qiánduān kuàngjià"
     */
    fun toToneMarks(numbered: String): String {
        if (numbered.isBlank()) return ""
        return numbered.split(' ')
            .joinToString(" ") { word -> convertWord(word.replace("//", "")) }
            .trim()
    }

    /** Convert a single whitespace-free, separator-free word. */
    private fun convertWord(word: String): String {
        val result = StringBuilder()
        val syllable = StringBuilder()
        for (ch in word) {
            if (ch.isDigit()) {
                result.append(applyTone(syllable.toString(), ch - '0'))
                syllable.clear()
            } else {
                syllable.append(ch)
            }
        }
        // Trailing letters with no tone digit (e.g. "de5" already handled; bare "de")
        result.append(syllable)
        return result.toString()
    }

    /** Apply tone [1..4] to [raw] syllable; return unchanged for neutral (0 or 5). */
    private fun applyTone(raw: String, tone: Int): String {
        // Normalise v / u: → ü, preserving case
        val syl = raw
            .replace("u:", "ü").replace("U:", "Ü")
            .replace('v', 'ü').replace('V', 'Ü')
        if (tone !in 1..4) return syl   // neutral tone — no diacritic
        val idx = markIndex(syl) ?: return syl
        val target = syl[idx]
        val marks = TONE_MARKS[target.lowercaseChar()] ?: return syl
        val marked = marks[tone - 1]
        val finalCh = if (target.isUpperCase()) marked.uppercaseChar() else marked
        return syl.substring(0, idx) + finalCh + syl.substring(idx + 1)
    }

    /**
     * Find the index of the vowel that should carry the tone mark.
     * Priority: a/e first; then the o in "ou"; then the last vowel.
     */
    private fun markIndex(syl: String): Int? {
        val lower = syl.lowercase()
        lower.indexOf('a').let { if (it >= 0) return it }
        lower.indexOf('e').let { if (it >= 0) return it }
        // "ou" → mark the o
        lower.indexOf("ou").let { if (it >= 0) return it }
        return lower.indexOfLast { it in "aeiouü" }.takeIf { it >= 0 }
    }
}
