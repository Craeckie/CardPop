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

package com.floflacards.app.service

import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.floflacards.app.data.entity.FlashcardEntity
import com.floflacards.app.data.entity.CategoryEntity
import com.floflacards.app.data.dao.CategoryDao
import com.floflacards.app.data.model.FlashcardFont
import com.floflacards.app.domain.model.FlashcardRating
import com.floflacards.app.data.source.FlashcardUiPreferences
import com.floflacards.app.data.repository.SettingsRepository
import com.floflacards.app.presentation.component.FlashcardContainer
import com.floflacards.app.presentation.component.flashcard.EmptyStateFlashcardContainer
import androidx.compose.runtime.collectAsState
import java.io.File

/**
 * UI composition for the overlay service. Wires overlay callbacks to
 * FlashcardContainer / EmptyStateFlashcardContainer and refreshes the
 * persisted position/size state as the user drags/resizes the window.
 */
class OverlayComponents(
    private val categoryDao: CategoryDao,
    private val flashcardUiPreferences: FlashcardUiPreferences,
    private val settingsManager: SettingsRepository
) {
    companion object {
        private const val TAG = "OverlayComponents"
    }

    @Composable
    fun OverlayContent(
        flashcard: FlashcardEntity,
        onPositionChange: (Int, Int) -> Unit,
        onSizeChange: (Int, Int) -> Unit,
        onRating: (FlashcardRating) -> Unit,
        onClose: () -> Unit,
        onManageCards: () -> Unit = { },
        onSnooze: () -> Unit = { }
    ) {
        val context = LocalContext.current
        var category by remember { mutableStateOf<CategoryEntity?>(null) }
        var currentUiState by remember { mutableStateOf(flashcardUiPreferences.getFlashcardUiState()) }
        val currentFlashcardTheme by settingsManager.flashcardTheme.collectAsState()
        val currentFlashcardFont by settingsManager.flashcardFont.collectAsState()
        val customFontFile = remember(currentFlashcardFont) {
            if (currentFlashcardFont == FlashcardFont.CUSTOM)
                File(context.filesDir, "custom_font.ttf").takeIf { it.exists() }
            else null
        }

        LaunchedEffect(flashcard.categoryId) {
            try {
                category = if (flashcard.categoryId == -1L) {
                    CategoryEntity(id = -1L, name = "Demo Category")
                } else {
                    categoryDao.getCategoryById(flashcard.categoryId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get category", e)
            }
        }

        // Re-read preferences after each drag/resize so the Compose view reflects
        // the persisted (constrained) position and size.
        val handlePositionChange: (Int, Int) -> Unit = { dx, dy ->
            onPositionChange(dx, dy)
            currentUiState = flashcardUiPreferences.getFlashcardUiState()
        }
        val handleSizeChange: (Int, Int) -> Unit = { dw, dh ->
            onSizeChange(dw, dh)
            currentUiState = flashcardUiPreferences.getFlashcardUiState()
        }

        if (flashcard.id == -2L) {
            EmptyStateFlashcardContainer(
                flashcard = flashcard,
                uiState = currentUiState,
                theme = currentFlashcardTheme,
                onPositionChange = handlePositionChange,
                onSizeChange = handleSizeChange,
                onManageCards = onManageCards,
                onSnooze = onSnooze,
                onClose = onClose
            )
        } else {
            FlashcardContainer(
                flashcard = flashcard,
                category = category,
                uiState = currentUiState,
                theme = currentFlashcardTheme,
                font = currentFlashcardFont,
                customFontFile = customFontFile,
                onPositionChange = handlePositionChange,
                onSizeChange = handleSizeChange,
                onRating = onRating,
                onSnooze = onSnooze,
                onClose = onClose
            )
        }
    }
}
