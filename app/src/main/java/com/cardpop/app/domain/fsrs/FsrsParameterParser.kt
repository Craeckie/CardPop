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

package com.cardpop.app.domain.fsrs

/**
 * Tolerant parser and formatter for FSRS-6 weight arrays.
 *
 * The external FSRS optimizer can emit parameters in several textual forms:
 *   - Bare comma-separated: `0.212, 1.293, 2.307, …`
 *   - Bracketed:            `[0.212, 1.293, 2.307, …]`
 *   - With `w:` prefix:     `w: [0.212, 1.293, …]`
 *   - With `w=` prefix:     `w = [0.212, 1.293, …]`
 *   - JSON object:          `{"w": [0.212, 1.293, …]}`
 *
 * Values may be negative (valid for FSRS-6 w17–w20 range) and use standard
 * decimal or scientific notation. Exactly [EXPECTED_COUNT] (21) values are
 * required; any other count is rejected with a descriptive error.
 *
 * No Android dependencies — fully testable on the JVM.
 */
object FsrsParameterParser {

    const val EXPECTED_COUNT = 21

    /**
     * Parses [input] into a list of [EXPECTED_COUNT] doubles.
     *
     * Returns [Result.failure] with an [IllegalArgumentException] whose message
     * describes the problem (wrong count, unparseable token) so it can be shown
     * directly in the UI.
     */
    fun parse(input: String): Result<List<Double>> = runCatching {
        var s = input.trim()
        // Strip UTF-8 BOM if present (U+FEFF)
        if (s.startsWith("\uFEFF")) s = s.substring(1)
        // Strip outer JSON object braces: {"w": [...]} → "w": [...]
        if (s.startsWith("{") && s.endsWith("}")) s = s.removePrefix("{").removeSuffix("}").trim()
        // Strip JSON/plain key prefix: "w":, w:, w =, 'w': etc.
        val prefixRegex = Regex("""^["']?w["']?\s*[:=]\s*""", RegexOption.IGNORE_CASE)
        s = prefixRegex.replace(s, "")
        // Strip surrounding square brackets
        s = s.removePrefix("[").removeSuffix("]").trim()
        // Split on commas and/or whitespace/newlines
        val tokens = s.split(Regex("[,\\s]+")).filter { it.isNotBlank() }
        if (tokens.size != EXPECTED_COUNT) {
            throw IllegalArgumentException(
                "Expected $EXPECTED_COUNT weights, got ${tokens.size}"
            )
        }
        tokens.mapIndexed { i, token ->
            token.toDoubleOrNull()
                ?: throw IllegalArgumentException(
                    "Cannot parse value at position ${i + 1}: '$token'"
                )
        }
    }

    /**
     * Formats [params] as a canonical comma-and-space-separated string suitable
     * for both display and round-trip parsing via [parse].
     */
    fun format(params: List<Double>): String = params.joinToString(", ")
}
