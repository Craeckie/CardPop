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

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.floflacards.app.domain.model.FlashcardRating

/**
 * Rating buttons: Again (❌), Hard (❓), Good (✅), Easy (💎).
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
    RatingButton(emoji = "❌", containerColor = Color(0xFFD32F2F),
        onClick = { onRating(FlashcardRating.WRONG) }, modifier = modifier)

@Composable
private fun HardButton(onRating: (FlashcardRating) -> Unit, modifier: Modifier) =
    RatingButton(emoji = "❓", containerColor = Color(0xFFF57C00),
        onClick = { onRating(FlashcardRating.HARD) }, modifier = modifier)

@Composable
private fun GoodButton(onRating: (FlashcardRating) -> Unit, modifier: Modifier) =
    RatingButton(emoji = "✅", containerColor = Color(0xFF388E3C),
        onClick = { onRating(FlashcardRating.GOOD) }, modifier = modifier)

@Composable
private fun EasyButton(onRating: (FlashcardRating) -> Unit, modifier: Modifier) =
    RatingButton(emoji = "💎", containerColor = Color(0xFF1976D2),
        onClick = { onRating(FlashcardRating.EASY) }, modifier = modifier)

@Composable
private fun RatingButton(
    emoji: String,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Color.White
        ),
        shape = SharedStyles.CornerRadius.small,
        elevation = ButtonElevation.standard(),
        modifier = modifier.height(40.dp)
    ) {
        Text(text = emoji, fontSize = 18.sp)
    }
}

private val NARROW_THRESHOLD: Dp = 240.dp
