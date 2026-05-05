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

package com.floflacards.app.presentation.component.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.floflacards.app.data.source.ReviewHistoryEntry

/**
 * Bar chart showing daily review counts over the last N days.
 * Hidden entirely when there is no review activity yet.
 */
@Composable
fun ReviewHistoryChart(history: List<ReviewHistoryEntry>) {
    if (history.all { it.reviews == 0 }) return

    val maxReviews = history.maxOf { it.reviews }.coerceAtLeast(1)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = getStatisticsSurface()),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "Daily Reviews",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = getStatisticsOnSurface()
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                history.forEach { entry ->
                    val heightFraction = if (entry.reviews > 0)
                        (entry.reviews.toFloat() / maxReviews).coerceIn(0.04f, 1f)
                    else 0f

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(heightFraction)
                                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                .background(
                                    if (entry.reviews > 0) AccentTeal
                                    else getStatisticsProgressBackground().copy(alpha = 0.3f)
                                )
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                history.firstOrNull()?.let { first ->
                    Text(
                        text = first.dateKey.takeLast(5), // "MM-dd"
                        fontSize = 9.sp,
                        color = getStatisticsOnSurfaceVariant()
                    )
                }
                history.lastOrNull()?.let { last ->
                    Text(
                        text = last.dateKey.takeLast(5),
                        fontSize = 9.sp,
                        color = getStatisticsOnSurfaceVariant()
                    )
                }
            }
        }
    }
}
