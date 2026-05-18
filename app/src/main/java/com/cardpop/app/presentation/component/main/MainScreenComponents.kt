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

package com.cardpop.app.presentation.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.cardpop.app.presentation.component.getLightModeOnlyBorder
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import com.cardpop.app.R
import com.cardpop.app.presentation.component.text.AutoSizeText

/**
 * Main screen UI components extracted from MainActivity.
 * Follows Single Responsibility Principle and promotes reusability.
 * Each component has a focused purpose and clean interface.
 */

/**
 * Modern header section with app branding and description.
 * Provides consistent visual identity across the app.
 * Fixed for proper light mode visibility.
 */
@Composable
fun ModernHeaderSection(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = getHeaderContainerColor()
        ),
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = getLightModeOnlyBorder(true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = R.mipmap.ic_launcher_round,
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.home_header_title),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = getHeaderContentColor(),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = stringResource(R.string.home_header_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = getHeaderContentColor().copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Status dashboard: a stats row of Streak + Mastered on top, with the service
 * status pill below it. The pill carries the next-flashcard / snooze countdown
 * inline, so the previous standalone countdown cards are no longer needed.
 */
@Composable
fun StatusDashboard(
    isServiceActive: Boolean,
    masteredCards: Int,
    totalCards: Int,
    nextFlashcardCountdown: Long,
    streak: Int = 0,
    isSnoozing: Boolean = false,
    snoozeRemainingSeconds: Long = 0L,
    intervalMinutes: Int = 0,
    onMasteredClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ModernStatsRow(
            streak = streak,
            masteredCards = masteredCards,
            totalCards = totalCards,
            onMasteredClick = onMasteredClick,
            modifier = Modifier.fillMaxWidth()
        )
        ServiceStatusPill(
            isServiceActive = isServiceActive,
            isSnoozing = isSnoozing,
            nextFlashcardCountdown = nextFlashcardCountdown,
            snoozeRemainingSeconds = snoozeRemainingSeconds,
            intervalMinutes = intervalMinutes,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Modern action card with consistent styling and accessibility.
 * Provides primary and secondary variants for visual hierarchy.
 */
@Composable
fun ModernActionCard(
    title: String,
    subtitle: String,
    icon: String,
    onClick: () -> Unit,
    isPrimary: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .height(120.dp)
            .semantics {
                this.contentDescription = contentDescription
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isPrimary) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Icon
            Text(
                text = icon,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            
            // Title
            AutoSizeText(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = if (isPrimary) 
                    MaterialTheme.colorScheme.onPrimaryContainer 
                else 
                    MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center,
                maxLines = 1,
                minTextSize = 12.sp,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Subtitle
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (isPrimary) 
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) 
                else 
                    MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Responsive action card that adapts to different screen sizes.
 * Follows DRY principle by reusing existing card patterns with adaptive sizing.
 */
@Composable
fun ResponsiveActionCard(
    title: String,
    subtitle: String,
    icon: String,
    onClick: () -> Unit,
    isPrimary: Boolean,
    contentDescription: String,
    cardHeight: Dp,
    isCompact: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .height(cardHeight)
            .semantics {
                this.contentDescription = contentDescription
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isPrimary) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isCompact) 12.dp else 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon - adaptive size
            Text(
                text = icon,
                style = if (isCompact) 
                    MaterialTheme.typography.titleLarge 
                else 
                    MaterialTheme.typography.headlineMedium
            )
            
            // Title - adaptive typography with auto-sizing
            AutoSizeText(
                text = title,
                style = if (isCompact) 
                    MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold) 
                else 
                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isPrimary) 
                    MaterialTheme.colorScheme.onPrimaryContainer 
                else 
                    MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center,
                maxLines = 1,
                minTextSize = if (isCompact) 10.sp else 12.sp,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Subtitle: shown whenever non-empty so dynamic info (e.g. card counts)
            // stays visible at every breakpoint. Decorative empty subtitles collapse.
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isPrimary)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Responsive settings card with dark styling that adapts to different screen sizes.
 * Follows app's design consistency while being adaptive.
 */
@Composable
fun ResponsiveSettingsCard(
    onClick: () -> Unit,
    cardHeight: Dp,
    isCompact: Boolean,
    modifier: Modifier = Modifier
) {
    val settingsDescription = stringResource(R.string.home_settings_description)
    
    Card(
        onClick = onClick,
        modifier = modifier
            .height(cardHeight)
            .semantics {
                contentDescription = settingsDescription
            },
        colors = CardDefaults.cardColors(
            containerColor = getSettingsCardColor() // Theme-aware settings card color
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isCompact) 12.dp else 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon - adaptive size
            Text(
                text = "⚙️",
                style = if (isCompact) 
                    MaterialTheme.typography.titleLarge 
                else 
                    MaterialTheme.typography.headlineMedium
            )
            
            // Title - adaptive typography with auto-sizing
            AutoSizeText(
                text = stringResource(R.string.home_settings_title),
                style = if (isCompact) 
                    MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold) 
                else 
                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                textAlign = TextAlign.Center,
                maxLines = 1,
                minTextSize = if (isCompact) 10.sp else 12.sp,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Subtitle - adaptive and optional for very compact screens
            if (!isCompact) {
                Text(
                    text = stringResource(R.string.settings_preferences),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

