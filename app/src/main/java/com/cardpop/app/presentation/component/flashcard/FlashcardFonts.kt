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

package com.cardpop.app.presentation.component.flashcard

import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import com.cardpop.app.data.model.FlashcardFont
import java.io.File

object FlashcardFonts {
    private var cachedFile: String? = null
    private var cachedFamily: FontFamily? = null

    fun resolve(font: FlashcardFont, customFontFile: File? = null): FontFamily = when (font) {
        FlashcardFont.DEFAULT -> FontFamily.Default
        FlashcardFont.CUSTOM -> loadCustom(customFontFile)
    }

    @Suppress("DEPRECATION")
    private fun loadCustom(file: File?): FontFamily {
        if (file == null || !file.exists()) return FontFamily.Default
        if (file.absolutePath == cachedFile && cachedFamily != null) return cachedFamily!!
        val family = FontFamily(Typeface.createFromFile(file))
        cachedFile = file.absolutePath
        cachedFamily = family
        return family
    }
}
