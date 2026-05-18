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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.cardpop.app.R
import com.cardpop.app.presentation.component.text.AutoSizeText

@Composable
fun ServiceStatusPill(
    isServiceActive: Boolean,
    isSnoozing: Boolean,
    nextFlashcardCountdown: Long,
    snoozeRemainingSeconds: Long,
    intervalMinutes: Int = 0,
    modifier: Modifier = Modifier
) {
    val containerColor: Color = when {
        isSnoozing -> MaterialTheme.colorScheme.tertiaryContainer
        isServiceActive -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor: Color = when {
        isSnoozing -> MaterialTheme.colorScheme.onTertiaryContainer
        isServiceActive -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val label = when {
        isSnoozing -> stringResource(R.string.status_snoozed)
        isServiceActive -> stringResource(R.string.status_active)
        else -> stringResource(R.string.status_inactive)
    }

    val detail: String? = when {
        isSnoozing -> stringResource(
            R.string.status_pill_resumes_in,
            formatMmSs(snoozeRemainingSeconds)
        )
        isServiceActive -> {
            val countdown = if (nextFlashcardCountdown > 0L) {
                stringResource(R.string.status_pill_next_in, formatMmSs(nextFlashcardCountdown))
            } else {
                stringResource(R.string.status_pill_preparing)
            }
            if (intervalMinutes > 0) {
                "$countdown · ${stringResource(R.string.status_pill_every_min, intervalMinutes)}"
            } else {
                countdown
            }
        }
        else -> null
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isSnoozing) {
                Icon(
                    imageVector = Icons.Default.Snooze,
                    contentDescription = stringResource(R.string.overlay_snooze_content_description),
                    tint = contentColor
                )
            } else {
                Text(
                    text = if (isServiceActive) "🔄" else "○",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = contentColor,
                modifier = Modifier.weight(1f)
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.85f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun ModernStatsRow(
    streak: Int,
    masteredCards: Int,
    totalCards: Int,
    onMasteredClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ModernStatTile(
            icon = "🔥",
            value = streak.toString(),
            label = stringResource(R.string.status_streak_days),
            isPositive = streak > 0,
            modifier = Modifier.weight(1f)
        )
        ModernStatTile(
            icon = if (masteredCards > 0) "⭐" else "📚",
            value = "$masteredCards/$totalCards",
            label = stringResource(R.string.stats_total_flashcards),
            isPositive = masteredCards > 0,
            onClick = onMasteredClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ModernStatTile(
    icon: String,
    value: String,
    label: String,
    isPositive: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val containerColor = when {
        isPositive -> getActiveStatusCardColor()
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        isPositive -> getActiveStatusCardContentColor()
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val cardModifier = modifier.height(100.dp)
    val cardShape = RoundedCornerShape(12.dp)
    val cardElevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    val cardColors = CardDefaults.cardColors(containerColor = containerColor)

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = cardModifier,
            shape = cardShape,
            elevation = cardElevation,
            colors = cardColors
        ) {
            ModernStatTileContent(icon, value, label, contentColor)
        }
    } else {
        Card(
            modifier = cardModifier,
            shape = cardShape,
            elevation = cardElevation,
            colors = cardColors
        ) {
            ModernStatTileContent(icon, value, label, contentColor)
        }
    }
}

@Composable
private fun ModernStatTileContent(
    icon: String,
    value: String,
    label: String,
    contentColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        AutoSizeText(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            minTextSize = 10.sp,
            modifier = Modifier
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = contentColor.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
    }
}

private fun formatMmSs(seconds: Long): String {
    val total = seconds.coerceAtLeast(0L)
    val mins = total / 60L
    val secs = total % 60L
    return String.format("%d:%02d", mins, secs)
}
