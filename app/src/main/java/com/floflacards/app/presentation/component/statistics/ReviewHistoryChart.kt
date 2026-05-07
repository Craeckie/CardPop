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
import com.floflacards.app.data.source.ReviewHistoryEntry
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.cartesianLayerPadding
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
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
import com.patrykandpatrick.vico.core.common.data.ExtraStore

private val HistoryDateKey = ExtraStore.Key<List<String>>()

private val HistoryBottomAxisFormatter = CartesianValueFormatter { context, x, _ ->
    context.model.extraStore[HistoryDateKey].getOrElse(x.toInt()) { "" }
}

private val HistoryMarkerFormatter = DefaultCartesianMarker.ValueFormatter { _, targets ->
    val column = (targets[0] as ColumnCartesianLayerMarkerTarget).columns[0]
    val count = column.entry.y.toInt()
    SpannableStringBuilder().append(
        "$count reviews",
        ForegroundColorSpan(column.color),
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    )
}

@Composable
fun ReviewHistoryChart(history: List<ReviewHistoryEntry>) {
    if (history.all { it.reviews == 0 }) return

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(history) {
        val reversed = history.reversed()
        modelProducer.runTransaction {
            columnSeries { series(reversed.map { it.reviews }) }
            extras { it[HistoryDateKey] = reversed.map { it.dateKey.takeLast(5) } }
        }
    }

    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val labelComponent = rememberTextComponent(color = onSurfaceVariant)
    val marker = rememberDefaultCartesianMarker(
        label = rememberTextComponent(color = MaterialTheme.colorScheme.onSurface),
        valueFormatter = HistoryMarkerFormatter,
    )

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
            Spacer(modifier = Modifier.height(8.dp))
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberColumnCartesianLayer(
                        columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                            rememberLineComponent(fill = fill(AccentTeal), thickness = 6.dp)
                        )
                    ),
                    startAxis = VerticalAxis.rememberStart(label = labelComponent),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        label = labelComponent,
                        itemPlacer = remember { HorizontalAxis.ItemPlacer.aligned(spacing = { 5 }) },
                        valueFormatter = HistoryBottomAxisFormatter,
                    ),
                    marker = marker,
                    layerPadding = { cartesianLayerPadding(scalableStart = 4.dp, scalableEnd = 4.dp) },
                ),
                modelProducer = modelProducer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            )
        }
    }
}
