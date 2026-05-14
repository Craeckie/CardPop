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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import com.floflacards.app.R
import com.floflacards.app.data.entity.FlashcardEntity
import com.floflacards.app.data.model.FlashcardFont
import com.floflacards.app.data.model.FlashcardTheme
import com.floflacards.app.presentation.component.flashcard.FlashcardFonts
import com.floflacards.app.util.PlecoLauncher
import java.io.File

/**
 * Content component for question and answer display
 * Extracted from FlashcardOverlayComponents.kt to follow "avoid large files" rule
 * 
 * NEW DESIGN - This component provides:
 * - Question display with grayish styling
 * - Show Answer button with purple accent
 * - Answer display with purple styling
 * - Scrollable content for long text
 * - Dark background theme
 * - Centralized color scheme following DRY principle
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FlashcardContent(
    flashcard: FlashcardEntity,
    showAnswer: Boolean,
    onShowAnswer: () -> Unit,
    theme: FlashcardTheme = FlashcardTheme.DEFAULT_THEME,
    font: FlashcardFont = FlashcardFont.DEFAULT_FONT,
    customFontFile: java.io.File? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val plecoAvailable = remember { PlecoLauncher.isAvailable(context) }
    val clipboardManager = LocalClipboardManager.current
    val questionFontFamily = remember(font, customFontFile) { FlashcardFonts.resolve(font, customFontFile) }
    val questionFontSize = (17f * font.sizeScale).sp
    val questionLineHeight = (24f * font.sizeScale).sp
    val questionLetterSpacing = font.letterSpacingEm.em

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Question section with theme-aware styling
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        clipboardManager.setText(AnnotatedString(flashcard.question))
                        android.widget.Toast.makeText(context, context.getString(R.string.copied_to_clipboard), android.widget.Toast.LENGTH_SHORT).show()
                    }
                ),
            colors = FlashcardColors.getQuestionCardColors(theme = theme),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = flashcard.question,
                    color = FlashcardColors.getQuestionTextColor(theme),
                    fontSize = questionFontSize,
                    fontFamily = questionFontFamily,
                    textAlign = TextAlign.Center,
                    lineHeight = questionLineHeight,
                    letterSpacing = questionLetterSpacing,
                    fontWeight = if (font == FlashcardFont.CUSTOM) FontWeight.SemiBold else FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth()
                )

                // Question image
                flashcard.questionImagePath?.let { imagePath ->
                    FlashcardImage(
                        imagePath = imagePath,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        if (!showAnswer) {
            // Show Answer button with theme-aware accent
            Button(
                onClick = onShowAnswer,
                colors = FlashcardColors.getShowAnswerButtonColors(theme = theme),
                shape = RoundedCornerShape(12.dp),
                elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 2.dp
                ),
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .height(48.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "👁️",
                        fontSize = 16.sp
                    )
                    Text(
                        text = stringResource(R.string.flashcard_show_answer),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        } else {
            if (plecoAvailable) {
                TextButton(
                    onClick = { PlecoLauncher.lookup(context, flashcard.question) },
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = "📖 Pleco",
                        color = FlashcardColors.getQuestionTextColor(theme),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Answer section with theme-aware styling
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            clipboardManager.setText(AnnotatedString(flashcard.answer))
                            android.widget.Toast.makeText(context, context.getString(R.string.copied_to_clipboard), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    ),
                colors = FlashcardColors.getAnswerCardColors(theme = theme),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = flashcard.answer,
                        color = FlashcardColors.getAnswerTextColor(theme),
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Answer image
                    flashcard.answerImagePath?.let { imagePath ->
                        FlashcardImage(
                            imagePath = imagePath,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Flashcard image display with tap to expand.
 * 
 * Default: Cropped preview (150dp max)
 * Tap to expand: Full image view (400dp max)
 * Tap again: Back to preview
 * 
 * @param imagePath Path to the image file
 */
@Composable
private fun FlashcardImage(
    imagePath: String,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    AsyncImage(
        model = File(imagePath),
        contentDescription = stringResource(R.string.flashcard_image),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(
                min = 100.dp,
                max = if (isExpanded) 400.dp else 150.dp  // Expand on tap
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable { isExpanded = !isExpanded },  // Tap to toggle
        contentScale = if (isExpanded) ContentScale.Fit else ContentScale.Crop
    )
}
