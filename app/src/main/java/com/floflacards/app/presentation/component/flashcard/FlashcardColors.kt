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

package com.floflacards.app.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.floflacards.app.data.model.FlashcardTheme
import com.floflacards.app.presentation.theme.Palette

/**
 * Centralized flashcard color scheme following DRY principle.
 * Supports multiple themes: DEFAULT (current design), LIGHT, and DARK.
 * 
 * CRITICAL: Flashcard theme is independent from both app theme and device theme.
 * This eliminates hardcoded Color() values throughout the codebase and ensures
 * consistency across all flashcard components following SOLID principles.
 */
object FlashcardColors {
    
    /**
     * Theme color data class for clean organization
     */
    private data class ThemeColors(
        val darkBackground: Color,
        val headerBackground: Color,
        val questionBackground: Color,
        val questionText: Color,
        val answerBackground: Color,
        val answerText: Color,
        val buttonAccent: Color,
        val softWhite: Color
    )
    
    // DEFAULT theme - dark with brand blue answer card
    private val defaultTheme = ThemeColors(
        darkBackground = Palette.SurfaceDarkDeep,
        headerBackground = Palette.SurfaceDarkElev,
        questionBackground = Palette.QuestionBgDarkDefault,
        questionText = Palette.OnSurfaceDark,
        answerBackground = Palette.BrandBlue,
        answerText = Color.White,
        buttonAccent = Palette.BrandTeal,
        softWhite = Palette.OnSurfaceDark
    )

    // LIGHT theme - light surfaces with blue-container answer card
    private val lightTheme = ThemeColors(
        darkBackground = Palette.SurfaceLight,
        headerBackground = Palette.BrandBlueContainer,
        questionBackground = Palette.QuestionBgLight,
        questionText = Palette.OnSurfaceLight,
        answerBackground = Palette.BrandBlueContainer,
        answerText = Palette.BrandBlueOnContainer,
        buttonAccent = Palette.BrandTeal,
        softWhite = Palette.OnSurfaceLight
    )

    // BLACK theme - OLED black with brand blue answer card
    private val blackTheme = ThemeColors(
        darkBackground = Palette.SurfaceBlack,
        headerBackground = Palette.SurfaceBlackElev,
        questionBackground = Palette.QuestionBgBlack,
        questionText = Palette.OnSurfaceDark,
        answerBackground = Palette.BrandBlue,
        answerText = Color.White,
        buttonAccent = Palette.BrandTealLight,
        softWhite = Palette.OnSurfaceDark
    )

    // DARK theme - premium deep dark with blue-dark answer card
    private val darkTheme = ThemeColors(
        darkBackground = Palette.SurfaceDarkDeep,
        headerBackground = Palette.SurfaceDarkElev,
        questionBackground = Palette.QuestionBgDarkPremium,
        questionText = Palette.OnSurfaceDark,
        answerBackground = Palette.BrandBlueDark,
        answerText = Color.White,
        buttonAccent = Palette.BrandTealLight,
        softWhite = Palette.OnSurfaceDark
    )
    
    /**
     * Get theme-specific colors based on FlashcardTheme
     */
    private fun getThemeColors(theme: FlashcardTheme): ThemeColors = when (theme) {
        FlashcardTheme.DEFAULT -> defaultTheme
        FlashcardTheme.LIGHT -> lightTheme
        FlashcardTheme.DARK -> darkTheme
        FlashcardTheme.BLACK -> blackTheme
    }
    
    // Backward compatibility - maintain existing API using DEFAULT theme
    val DarkBackground get() = defaultTheme.darkBackground
    val HeaderBackground get() = defaultTheme.headerBackground
    val QuestionBackground get() = defaultTheme.questionBackground
    val QuestionText get() = defaultTheme.questionText
    val AnswerBackground get() = defaultTheme.answerBackground
    val AnswerText get() = defaultTheme.answerText
    val ButtonAccent get() = defaultTheme.buttonAccent
    val SoftWhite get() = defaultTheme.softWhite
    
    /**
     * Get question card colors for the overlay flashcards with theme support
     */
    @Composable
    fun getQuestionCardColors(
        theme: FlashcardTheme = FlashcardTheme.DEFAULT_THEME,
        isEnabled: Boolean = true
    ): androidx.compose.material3.CardColors {
        val themeColors = getThemeColors(theme)
        return androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (isEnabled) 
                themeColors.questionBackground 
            else 
                themeColors.questionBackground.copy(alpha = 0.5f)
        )
    }
    
    /**
     * Get answer card colors for the overlay flashcards with theme support
     */
    @Composable
    fun getAnswerCardColors(
        theme: FlashcardTheme = FlashcardTheme.DEFAULT_THEME,
        isEnabled: Boolean = true
    ): androidx.compose.material3.CardColors {
        val themeColors = getThemeColors(theme)
        return androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (isEnabled) 
                themeColors.answerBackground 
            else 
                themeColors.answerBackground.copy(alpha = 0.5f)
        )
    }
    
    fun getHeaderBackgroundColor(theme: FlashcardTheme = FlashcardTheme.DEFAULT_THEME): Color =
        getThemeColors(theme).headerBackground


    /**
     * Get text color based on enabled state for overlay flashcards with theme support
     */
    fun getTextColor(
        theme: FlashcardTheme = FlashcardTheme.DEFAULT_THEME,
        isEnabled: Boolean = true, 
        alpha: Float = 1f
    ): Color {
        val themeColors = getThemeColors(theme)
        val baseColor = themeColors.softWhite
        return if (isEnabled) baseColor.copy(alpha = alpha) else baseColor.copy(alpha = alpha * 0.6f)
    }
    
    /**
     * Get button colors for show answer button with theme support
     * Always uses white text for better contrast on colored button background
     */
    @Composable
    fun getShowAnswerButtonColors(
        theme: FlashcardTheme = FlashcardTheme.DEFAULT_THEME
    ): androidx.compose.material3.ButtonColors {
        val themeColors = getThemeColors(theme)
        return androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = themeColors.buttonAccent,
            contentColor = Color.White // Always white text for button contrast
        )
    }
    
    /**
     * Get theme-specific background color
     */
    fun getBackgroundColor(theme: FlashcardTheme): Color {
        val themeColors = getThemeColors(theme)
        return themeColors.darkBackground
    }
    
    /**
     * Get theme-specific question text color
     */
    fun getQuestionTextColor(theme: FlashcardTheme): Color {
        val themeColors = getThemeColors(theme)
        return themeColors.questionText
    }
    
    /**
     * Get theme-specific answer text color
     */
    fun getAnswerTextColor(theme: FlashcardTheme): Color {
        val themeColors = getThemeColors(theme)
        return themeColors.answerText
    }
}
