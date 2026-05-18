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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.cardpop.app.R

/**
 * Unified dialog system to eliminate code duplication across the app.
 * Follows DRY principle by providing a single, reusable dialog component.
 * Follows KISS principle with simple, clear API.
 */
@Composable
fun UnifiedDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmButtonText: String? = null,
    dismissButtonText: String? = null,
    onConfirm: () -> Unit = onDismiss,
    content: @Composable ColumnScope.() -> Unit
) {
    val actualConfirmButtonText = confirmButtonText ?: stringResource(R.string.dialog_ok)
    val actualDismissButtonText = dismissButtonText ?: stringResource(R.string.dialog_cancel)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(24.dp),
        title = { 
            Text(
                text = title, 
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            Column(content = content)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(actualConfirmButtonText)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text(actualDismissButtonText)
            }
        }
    )
}

/**
 * Simple confirmation dialog using unified system.
 */
@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmButtonText: String? = null,
    dismissButtonText: String? = null
) {
    val actualConfirmButtonText = confirmButtonText ?: stringResource(R.string.dialog_confirm)
    val actualDismissButtonText = dismissButtonText ?: stringResource(R.string.dialog_cancel)
    UnifiedDialog(
        title = title,
        confirmButtonText = actualConfirmButtonText,
        dismissButtonText = actualDismissButtonText,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    ) {
        Text(message)
    }
}

/**
 * Specialized deletion confirmation dialog for flashcards.
 * Uses unified dialog system to avoid duplication.
 */
@Composable
fun DeleteFlashcardConfirmationDialog(
    flashcardQuestion: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ConfirmationDialog(
        title = stringResource(R.string.delete_flashcard_title),
        message = stringResource(R.string.delete_flashcard_message, flashcardQuestion),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmButtonText = stringResource(R.string.delete_flashcard_confirm),
        dismissButtonText = stringResource(R.string.delete_flashcard_cancel)
    )
}

/**
 * Specialized deletion confirmation dialog for categories.
 * Uses unified dialog system to avoid duplication.
 */
@Composable
fun DeleteCategoryConfirmationDialog(
    categoryName: String,
    flashcardCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val message = if (flashcardCount > 0) {
        stringResource(R.string.delete_category_message_with_cards, categoryName, flashcardCount)
    } else {
        stringResource(R.string.delete_category_message_empty, categoryName)
    }
    
    ConfirmationDialog(
        title = stringResource(R.string.delete_category_title),
        message = message,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmButtonText = stringResource(R.string.delete_category_confirm),
        dismissButtonText = stringResource(R.string.delete_category_cancel)
    )
}
