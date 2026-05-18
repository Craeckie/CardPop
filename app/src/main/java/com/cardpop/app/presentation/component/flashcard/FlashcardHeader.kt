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

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.cardpop.app.R
import com.cardpop.app.data.entity.CategoryEntity
import com.cardpop.app.data.model.FlashcardTheme

/**
 * Header for the flashcard overlay. The entire row acts as a drag handle to
 * move the overlay window — drag is always available, no mode toggle.
 */
@Composable
fun FlashcardHeader(
    category: CategoryEntity?,
    theme: FlashcardTheme = FlashcardTheme.DEFAULT_THEME,
    onPositionChange: (Int, Int) -> Unit,
    onSnooze: () -> Unit = { },
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                FlashcardColors.getHeaderBackgroundColor(theme),
                RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    onPositionChange(dragAmount.x.toInt(), dragAmount.y.toInt())
                }
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(text = "📁", fontSize = 14.sp)
            Text(
                text = category?.name ?: "",
                color = FlashcardColors.getTextColor(theme),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.1.sp
            )
        }

        IconButton(
            onClick = onSnooze,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Snooze,
                contentDescription = stringResource(R.string.overlay_snooze_content_description),
                tint = FlashcardColors.getTextColor(theme)
            )
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = FlashcardColors.getTextColor(theme)
            )
        }
    }
}
