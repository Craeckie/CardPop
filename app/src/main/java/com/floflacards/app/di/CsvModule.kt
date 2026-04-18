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

package com.floflacards.app.di

import com.floflacards.app.data.anki.AnkiParser
import com.floflacards.app.data.csv.CsvExporter
import com.floflacards.app.data.csv.CsvParser
import com.floflacards.app.data.dao.CategoryDao
import com.floflacards.app.data.dao.FlashcardDao
import com.floflacards.app.domain.usecase.csv.BulkExportCsvUseCase
import com.floflacards.app.domain.usecase.csv.ExportCsvUseCase
import com.floflacards.app.domain.usecase.csv.ImportCsvUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency injection module for CSV import/export functionality.
 */
@Module
@InstallIn(SingletonComponent::class)
object CsvModule {

    @Provides
    @Singleton
    fun provideAnkiParser(): AnkiParser {
        return AnkiParser()
    }

    @Provides
    @Singleton
    fun provideCsvParser(): CsvParser {
        return CsvParser()
    }

    @Provides
    @Singleton
    fun provideCsvExporter(): CsvExporter {
        return CsvExporter()
    }

    @Provides
    fun provideImportCsvUseCase(
        csvParser: CsvParser,
        flashcardDao: FlashcardDao,
        categoryDao: CategoryDao
    ): ImportCsvUseCase {
        return ImportCsvUseCase(csvParser, flashcardDao, categoryDao)
    }

    @Provides
    fun provideExportCsvUseCase(
        flashcardDao: FlashcardDao,
        csvExporter: CsvExporter
    ): ExportCsvUseCase {
        return ExportCsvUseCase(flashcardDao, csvExporter)
    }

    @Provides
    fun provideBulkExportCsvUseCase(
        flashcardDao: FlashcardDao,
        categoryDao: CategoryDao,
        csvExporter: CsvExporter
    ): BulkExportCsvUseCase {
        return BulkExportCsvUseCase(flashcardDao, categoryDao, csvExporter)
    }
}
