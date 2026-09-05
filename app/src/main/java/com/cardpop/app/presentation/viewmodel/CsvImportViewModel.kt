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

package com.cardpop.app.presentation.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardpop.app.R
import com.cardpop.app.data.importer.ImportFileParser
import com.cardpop.app.data.importer.ImportFormat
import com.cardpop.app.data.importer.ImportParseOutcome
import com.cardpop.app.data.csv.CsvImportResult
import com.cardpop.app.data.csv.CsvParseResult
import com.cardpop.app.domain.usecase.csv.ImportCsvUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel for CSV import operations.
 *
 * Manages parse preview, validation, and import execution.
 * Caches the parsed result so the file is only read once during preview,
 * and the cached result is used for the actual import.
 */
@HiltViewModel
class CsvImportViewModel @Inject constructor(
    private val importCsvUseCase: ImportCsvUseCase,
    private val importFileParser: ImportFileParser,
    private val application: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(CsvImportUiState())
    val uiState: StateFlow<CsvImportUiState> = _uiState.asStateFlow()

    /**
     * The parsed result from the most recent preview.
     * Cached so that [executeImport] does not need to re-read the file.
     */
    private var cachedParseResult: CsvParseResult? = null

    private var pendingUri: Uri? = null
    private var pendingFileName: String? = null

    /**
     * Sets the file to be imported (from SAF picker result).
     * Automatically triggers parsing for preview.
     */
    fun setFile(uri: Uri, fileName: String, contentResolver: android.content.ContentResolver) {
        pendingUri = uri
        pendingFileName = fileName
        _uiState.value = _uiState.value.copy(
            fileName = fileName,
            parseResult = null,
            importResult = null,
            error = null,
            step = ImportStep.PREVIEW_READY
        )

        // Automatically parse for preview
        parseForPreview(contentResolver)
    }

    /**
     * Parses the file for preview.
     *
     * The format is detected from the file's leading bytes by [ImportFileParser]; the
     * file name is not consulted, because the SAF picker often hands us an opaque
     * document id rather than a name. Caches the result for [executeImport].
     */
    fun parseForPreview(contentResolver: android.content.ContentResolver) {
        val uri = pendingUri ?: run {
            _uiState.value = _uiState.value.copy(error = "No file selected")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                step = ImportStep.PARSING
            )

            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val outcome = withContext(Dispatchers.IO) {
                        importFileParser.parse(inputStream, application.cacheDir)
                    }

                    when (outcome) {
                        is ImportParseOutcome.Unsupported -> {
                            cachedParseResult = null
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                parseResult = null,
                                error = application.getString(
                                    when (outcome.format) {
                                        ImportFormat.UNSUPPORTED_XML -> R.string.csv_import_error_not_pleco_xml
                                        else -> R.string.csv_import_error_unsupported
                                    }
                                ),
                                step = ImportStep.ERROR
                            )
                        }

                        is ImportParseOutcome.Parsed -> {
                            val result = outcome.result
                            cachedParseResult = result

                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                parseResult = result,
                                step = if (result.validCards.isEmpty()) {
                                    ImportStep.NO_VALID_CARDS
                                } else {
                                    ImportStep.PREVIEW_READY
                                },
                                error = if (result.validCards.isEmpty()) {
                                    "No valid flashcards found"
                                } else {
                                    null
                                }
                            )
                        }
                    }
                } ?: run {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Cannot open file",
                        step = ImportStep.ERROR
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to parse file: ${e.message}",
                    step = ImportStep.ERROR
                )
            }
        }
    }

    /**
     * Executes the import using the cached parse result.
     * This avoids re-reading the file from the URI.
     *
     * Exactly one of [categoryId] or [newCategoryName] must be provided.
     * When [newCategoryName] is non-blank, the category is created (or reused if a category
     * with that name already exists) inside the use-case — only when cards are actually
     * written to the DB, so cancelling before this call leaves no orphan category.
     */
    fun executeImport(
        categoryId: Long?,
        newCategoryName: String?,
        skipDuplicates: Boolean
    ) {
        if (categoryId == null && newCategoryName.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(
                error = "Please select or create a category before importing."
            )
            return
        }

        val parseResult = cachedParseResult ?: run {
            _uiState.value = _uiState.value.copy(
                error = "No parsed data available. Please preview the file first."
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isImporting = true,
                importResult = null
            )

            try {
                val result = importCsvUseCase.importFromParsed(
                    parseResult = parseResult,
                    fallbackCategoryId = categoryId ?: 0L,
                    skipDuplicates = skipDuplicates,
                    resolveCategories = false,
                    newCategoryName = newCategoryName
                )

                result.fold(
                    onSuccess = { importResult ->
                        _uiState.value = _uiState.value.copy(
                            isImporting = false,
                            importResult = importResult,
                            step = ImportStep.IMPORT_COMPLETE,
                            error = null
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isImporting = false,
                            error = "Import failed: ${error.message}",
                            step = ImportStep.ERROR
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    error = "Import failed: ${e.message}",
                    step = ImportStep.ERROR
                )
            }
        }
    }

    /**
     * Resets the import state for a new import operation.
     */
    fun reset() {
        pendingUri = null
        pendingFileName = null
        cachedParseResult = null
        _uiState.value = CsvImportUiState()
    }

    /**
     * Clears the current error.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

/**
 * Steps in the import flow.
 */
enum class ImportStep {
    IDLE,
    PARSING,
    PREVIEW_READY,
    NO_VALID_CARDS,
    IMPORTING,
    IMPORT_COMPLETE,
    ERROR
}

/**
 * UI state for CSV import.
 */
data class CsvImportUiState(
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
    val fileName: String? = null,
    val parseResult: CsvParseResult? = null,
    val importResult: CsvImportResult? = null,
    val error: String? = null,
    val step: ImportStep = ImportStep.IDLE
)
