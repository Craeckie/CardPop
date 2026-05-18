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

package com.cardpop.app.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.cardpop.app.data.model.AppTheme

private val DarkColorScheme = darkColorScheme(
    primary = Palette.BrandBlueLight,
    onPrimary = Palette.BrandBlueOnContainer,
    primaryContainer = Palette.BrandBlueDark,
    onPrimaryContainer = Palette.BrandBlueContainer,
    secondary = Palette.BrandTealLight,
    onSecondary = Palette.OnBrandTealDark,
    secondaryContainer = Palette.BrandTealDark,
    onSecondaryContainer = Palette.BrandTealContainer,
    tertiary = Palette.BrandBlue,
    onTertiary = Color.White,
    background = Palette.SurfaceDarkDeep,
    onBackground = Palette.OnSurfaceDark,
    surface = Palette.SurfaceDarkElev,
    onSurface = Palette.OnSurfaceDark,
)

private val BlackColorScheme = darkColorScheme(
    primary = Palette.BrandBlueLight,
    onPrimary = Palette.BrandBlueOnContainer,
    primaryContainer = Palette.BrandBlueDark,
    onPrimaryContainer = Palette.BrandBlueContainer,
    secondary = Palette.BrandTealLight,
    onSecondary = Palette.OnBrandTealDark,
    secondaryContainer = Palette.BrandTealDark,
    onSecondaryContainer = Palette.BrandTealContainer,
    tertiary = Palette.BrandBlue,
    onTertiary = Color.White,
    background = Palette.SurfaceBlack,
    onBackground = Palette.OnSurfaceDark,
    surface = Palette.SurfaceBlackElev,
    onSurface = Palette.OnSurfaceDark,
)

private val LightColorScheme = lightColorScheme(
    primary = Palette.BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Palette.BrandBlueContainer,
    onPrimaryContainer = Palette.BrandBlueOnContainer,
    secondary = Palette.BrandTeal,
    onSecondary = Color.White,
    secondaryContainer = Palette.BrandTealContainer,
    onSecondaryContainer = Palette.BrandTealOnContainer,
    tertiary = Palette.BrandBlueDark,
    onTertiary = Color.White,
    background = Palette.SurfaceLight,
    onBackground = Palette.OnSurfaceLight,
    surface = Palette.SurfaceLight,
    onSurface = Palette.OnSurfaceLight,
)

@Composable
fun FloatingLearningTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    content: @Composable () -> Unit
) {
    val colorScheme = when (appTheme) {
        AppTheme.LIGHT -> LightColorScheme
        AppTheme.DARK -> DarkColorScheme
        AppTheme.BLACK -> BlackColorScheme
        AppTheme.SYSTEM -> {
            // Only use system theme when explicitly set to SYSTEM
            if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
