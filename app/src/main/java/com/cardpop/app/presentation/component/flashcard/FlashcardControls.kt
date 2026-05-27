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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cardpop.app.R
import com.cardpop.app.domain.model.FlashcardRating

object RatingColors {
    val again = Color(0xFFD32F2F)
    val hard  = Color(0xFFF57C00)
    val good  = Color(0xFF388E3C)
    val easy  = Color(0xFF1976D2)
}

fun FlashcardRating.ratingColor(): Color = when (this) {
    FlashcardRating.WRONG  -> RatingColors.again
    FlashcardRating.HARD   -> RatingColors.hard
    FlashcardRating.GOOD   -> RatingColors.good
    FlashcardRating.EASY   -> RatingColors.easy
    FlashcardRating.CLOSED -> Color.Transparent
}

/**
 * Rating buttons: Again, Hard, Good, Easy.
 *
 * The overlay is user-resizable down to fairly small widths. At ≥ NARROW_THRESHOLD
 * the four buttons sit in a single row; below that they fold into a 2×2 grid so
 * each tap target stays readable at 40dp height.
 */
@Composable
fun FlashcardControls(
    onRating: (FlashcardRating) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (maxWidth < NARROW_THRESHOLD) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AgainButton(onRating, Modifier.weight(1f))
                    HardButton(onRating, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoodButton(onRating, Modifier.weight(1f))
                    EasyButton(onRating, Modifier.weight(1f))
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AgainButton(onRating, Modifier.weight(1f))
                HardButton(onRating, Modifier.weight(1f))
                GoodButton(onRating, Modifier.weight(1f))
                EasyButton(onRating, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AgainButton(onRating: (FlashcardRating) -> Unit, modifier: Modifier) =
    RatingButton(label = stringResource(R.string.rating_again),
        containerColor = RatingColors.again,
        onClick = { onRating(FlashcardRating.WRONG) }, modifier = modifier)

@Composable
private fun HardButton(onRating: (FlashcardRating) -> Unit, modifier: Modifier) =
    RatingButton(label = stringResource(R.string.rating_hard),
        containerColor = RatingColors.hard,
        onClick = { onRating(FlashcardRating.HARD) }, modifier = modifier)

@Composable
private fun GoodButton(onRating: (FlashcardRating) -> Unit, modifier: Modifier) =
    RatingButton(label = stringResource(R.string.rating_good),
        containerColor = RatingColors.good,
        onClick = { onRating(FlashcardRating.GOOD) }, modifier = modifier)

@Composable
private fun EasyButton(onRating: (FlashcardRating) -> Unit, modifier: Modifier) =
    RatingButton(label = stringResource(R.string.rating_easy),
        containerColor = RatingColors.easy,
        onClick = { onRating(FlashcardRating.EASY) }, modifier = modifier)

@Composable
private fun RatingButton(
    label: String,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Surface(
        color = containerColor,
        contentColor = Color.White,
        shape = SharedStyles.CornerRadius.small,
        shadowElevation = 2.dp,
        modifier = modifier
            .height(34.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = label,
                fontSize = 13.sp,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

private val NARROW_THRESHOLD: Dp = 240.dp
