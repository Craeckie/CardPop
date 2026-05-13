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

package com.floflacards.app.data.model

/**
 * Font choice for flashcard question text.
 *
 * WENKAI bundles a subsetted LXGW WenKai Screen font (SIL OFL 1.1) so CJK
 * glyphs render with a natural, round 楷书 style instead of the OEM fallback.
 * The font applies to the question only; the answer always uses the system font.
 */
enum class FlashcardFont(val displayName: String, val sizeScale: Float, val letterSpacingEm: Float) {
    DEFAULT("Default", 1.0f, 0f),
    CHINESE("Chinese", 1.25f, 0.05f);

    companion object {
        val DEFAULT_FONT = DEFAULT

        fun fromString(value: String): FlashcardFont =
            when (value) {
                "SYSTEM" -> DEFAULT   // migrate old stored value
                "WENKAI" -> CHINESE   // migrate old stored value
                else -> entries.find { it.name == value } ?: DEFAULT_FONT
            }
    }
}
