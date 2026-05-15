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

package com.floflacards.app.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Single source of truth for every brand color in the app.
 *
 * Derived from the launcher logo gradient: teal (#1A9E8F) → blue (#1565C0).
 * `Theme.kt` and `FlashcardColors.kt` read from here — no other Color(0x...)
 * literals should appear in `presentation/theme/` or `FlashcardColors.kt`.
 * To re-skin the app, change these constants.
 */
object Palette {

    // Brand — teal (primary, from logo top)
    val BrandTeal = Color(0xFF1A9E8F)
    val BrandTealLight = Color(0xFF4CC6B5)
    val BrandTealDark = Color(0xFF0E7367)
    val BrandTealContainer = Color(0xFFB8EBE3)
    val BrandTealOnContainer = Color(0xFF002820)
    val OnBrandTealDark = Color(0xFF003731)

    // Brand — blue (secondary, from logo bottom)
    val BrandBlue = Color(0xFF1565C0)
    val BrandBlueLight = Color(0xFF5A95F5)
    val BrandBlueDark = Color(0xFF0D4691)
    val BrandBlueContainer = Color(0xFFD6E3FB)
    val BrandBlueOnContainer = Color(0xFF001947)

    // Neutrals (warm-leaning to harmonize with teal)
    val SurfaceLight = Color(0xFFFBFCFC)
    val SurfaceDarkDeep = Color(0xFF0E1413)
    val SurfaceDarkElev = Color(0xFF1A2221)
    val SurfaceBlack = Color(0xFF000000)
    val SurfaceBlackElev = Color(0xFF0A0E0E)

    val OnSurfaceLight = Color(0xFF1A1C1B)
    val OnSurfaceDark = Color(0xFFE3E6E5)
    val OnSurfaceMuted = Color(0xFFB9C0BF)

    // Tinted question backgrounds (teal-undertone neutrals for flashcards)
    val QuestionBgLight = Color(0xFFEFF6F5)
    val QuestionBgDarkDefault = Color(0xFF1F2A28)
    val QuestionBgDarkPremium = Color(0xFF22302E)
    val QuestionBgBlack = Color(0xFF0E1413)
}
