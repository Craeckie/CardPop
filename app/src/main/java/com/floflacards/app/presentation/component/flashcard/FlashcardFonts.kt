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

package com.floflacards.app.presentation.component.flashcard

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.floflacards.app.R
import com.floflacards.app.data.model.FlashcardFont

/**
 * Maps a [FlashcardFont] preference to a Compose [FontFamily]. Bundled
 * font resources are resolved lazily so the OTF is parsed only when first
 * selected.
 */
object FlashcardFonts {
    private val wenkaiScreen: FontFamily by lazy {
        FontFamily(Font(R.font.lxgw_wenkai))
    }

    fun resolve(font: FlashcardFont): FontFamily = when (font) {
        FlashcardFont.SYSTEM -> FontFamily.Default
        FlashcardFont.WENKAI -> wenkaiScreen
    }
}
