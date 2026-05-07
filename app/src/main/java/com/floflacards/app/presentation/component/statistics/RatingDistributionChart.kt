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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.floflacards.app.presentation.viewmodel.RatingDistribution

private data class RatingBar(val label: String, val count: Int, val color: Color)

@Composable
fun RatingDistributionChart(distribution: RatingDistribution) {
    val bars = listOf(
        RatingBar("Again", distribution.wrong, AccentRed),
        RatingBar("Hard",  distribution.hard,  AccentAmber),
        RatingBar("Good",  distribution.good,  AccentTeal),
        RatingBar("Easy",  distribution.easy,  AccentBlue),
    )
    val maxCount = bars.maxOf { it.count }.coerceAtLeast(1)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = getStatisticsSurface()),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "Rating Distribution",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = getStatisticsOnSurface(),
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                bars.forEach { bar ->
                    val fraction = bar.count.toFloat() / maxCount.toFloat()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(modifier = Modifier.weight((1f - fraction).coerceAtLeast(0.001f)))
                        Text(
                            text = bar.count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = getStatisticsOnSurface(),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .weight(fraction.coerceAtLeast(0.005f))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(bar.color),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                bars.forEach { bar ->
                    Text(
                        text = bar.label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = getStatisticsOnSurfaceVariant(),
                    )
                }
            }
        }
    }
}
