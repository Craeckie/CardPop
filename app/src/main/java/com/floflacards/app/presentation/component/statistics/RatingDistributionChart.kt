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

import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.floflacards.app.presentation.viewmodel.RatingDistribution
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.cartesianLayerPadding
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.ColumnCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker

private val RatingLabels = listOf("Again", "Hard", "Good", "Easy")

private val RatingBottomAxisFormatter = CartesianValueFormatter { _, x, _ ->
    RatingLabels.getOrElse(x.toInt()) { "" }
}

private val RatingMarkerFormatter = DefaultCartesianMarker.ValueFormatter { _, targets ->
    val column = (targets[0] as ColumnCartesianLayerMarkerTarget).columns[0]
    val ratingIndex = column.entry.x.toInt()
    val count = column.entry.y.toInt()
    SpannableStringBuilder().append(
        "${RatingLabels.getOrElse(ratingIndex) { "?" }}: $count",
        ForegroundColorSpan(column.color),
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )
}

@Composable
fun RatingDistributionChart(distribution: RatingDistribution) {
    val modelProducer = remember { CartesianChartModelProducer() }

    val ratingColors = listOf(AccentRed, AccentAmber, AccentTeal, AccentBlue)
    val counts = listOf(distribution.wrong, distribution.hard, distribution.good, distribution.easy)

    LaunchedEffect(distribution) {
        modelProducer.runTransaction {
            columnSeries {
                // One series per rating so each bar gets its own color via ColumnProvider.series()
                counts.forEachIndexed { index, count ->
                    series(x = listOf(index), y = listOf(count))
                }
            }
        }
    }

    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val labelComponent = rememberTextComponent(color = onSurfaceVariant)
    val marker = rememberDefaultCartesianMarker(
        label = rememberTextComponent(color = MaterialTheme.colorScheme.onSurface),
        valueFormatter = RatingMarkerFormatter,
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = getStatisticsSurface()),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "Rating Distribution",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = getStatisticsOnSurface()
            )
            Spacer(modifier = Modifier.height(8.dp))
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberColumnCartesianLayer(
                        columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                            ratingColors.map { color ->
                                rememberLineComponent(fill = fill(color), thickness = 32.dp)
                            }
                        ),
                    ),
                    startAxis = VerticalAxis.rememberStart(label = labelComponent),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        label = labelComponent,
                        itemPlacer = remember { HorizontalAxis.ItemPlacer.segmented() },
                        valueFormatter = RatingBottomAxisFormatter,
                    ),
                    marker = marker,
                    layerPadding = { cartesianLayerPadding(scalableStart = 16.dp, scalableEnd = 16.dp) },
                ),
                modelProducer = modelProducer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                scrollState = rememberVicoScrollState(scrollEnabled = false),
                zoomState = rememberVicoZoomState(zoomEnabled = false),
            )
        }
    }
}
