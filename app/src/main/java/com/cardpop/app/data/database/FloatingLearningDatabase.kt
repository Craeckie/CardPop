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

package com.cardpop.app.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cardpop.app.data.dao.CategoryDao
import com.cardpop.app.data.dao.FlashcardDao
import com.cardpop.app.data.entity.CategoryEntity
import com.cardpop.app.data.entity.FlashcardEntity

@Database(
    entities = [CategoryEntity::class, FlashcardEntity::class],
    version = 8,
    exportSchema = true
)
abstract class FloatingLearningDatabase : RoomDatabase() {
    
    abstract fun categoryDao(): CategoryDao
    abstract fun flashcardDao(): FlashcardDao
    
    companion object {
        const val DATABASE_NAME = "floating_learning_database"
        
        // Migration to add cooldownUntil field
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE flashcards ADD COLUMN cooldownUntil INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        // Migration to add consecutiveCorrectCount field (now removed in v4)
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE flashcards ADD COLUMN consecutiveCorrectCount INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        // Migration to SM-2 algorithm: remove old fields, add SM-2 fields
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add SM-2 fields
                database.execSQL("ALTER TABLE flashcards ADD COLUMN easinessFactor REAL NOT NULL DEFAULT 2.5")
                database.execSQL("ALTER TABLE flashcards ADD COLUMN reviewCount INTEGER NOT NULL DEFAULT 0")
                
                // Remove old algorithm fields by creating new table and copying data
                database.execSQL("""
                    CREATE TABLE flashcards_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        categoryId INTEGER NOT NULL,
                        question TEXT NOT NULL,
                        answer TEXT NOT NULL,
                        isEnabled INTEGER NOT NULL,
                        correctCount INTEGER NOT NULL,
                        incorrectCount INTEGER NOT NULL,
                        easinessFactor REAL NOT NULL,
                        reviewCount INTEGER NOT NULL,
                        lastReviewedAt INTEGER NOT NULL,
                        cooldownUntil INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(categoryId) REFERENCES categories(id) ON DELETE CASCADE
                    )
                """)
                
                // Copy data from old table to new table
                database.execSQL("""
                    INSERT INTO flashcards_new (
                        id, categoryId, question, answer, isEnabled, correctCount, incorrectCount,
                        easinessFactor, reviewCount, lastReviewedAt, cooldownUntil, createdAt, updatedAt
                    )
                    SELECT 
                        id, categoryId, question, answer, isEnabled, correctCount, incorrectCount,
                        2.5, 0, lastReviewedAt, cooldownUntil, createdAt, updatedAt
                    FROM flashcards
                """)
                
                // Drop old table and rename new table
                database.execSQL("DROP TABLE flashcards")
                database.execSQL("ALTER TABLE flashcards_new RENAME TO flashcards")
                
                // Recreate index
                database.execSQL("CREATE INDEX index_flashcards_categoryId ON flashcards(categoryId)")
            }
        }
        
        // Migration to add hardCount field for tracking "Hard" button presses
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE flashcards ADD COLUMN hardCount INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        // Migration to add image path fields for flashcard images
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE flashcards ADD COLUMN questionImagePath TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE flashcards ADD COLUMN answerImagePath TEXT DEFAULT NULL")
            }
        }

        // Migration from SM-2 to FSRS-6. Drops easinessFactor + reviewCount,
        // renames cooldownUntil -> dueAt, adds easyCount + 7 FSRS state columns.
        // SQLite has no DROP COLUMN before 3.35, so the table is recreated.
        // Existing cards become FSRS-New (all FSRS state zeroed); review counters
        // (correctCount, incorrectCount, hardCount, lastReviewedAt) are preserved.
        // See FSRS_IMPLEMENTATION_PLAN.md "Decision: do not try to convert SM-2
        // state to FSRS state" for the rationale.
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE flashcards_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        categoryId INTEGER NOT NULL,
                        question TEXT NOT NULL,
                        answer TEXT NOT NULL,
                        questionImagePath TEXT,
                        answerImagePath TEXT,
                        isEnabled INTEGER NOT NULL,
                        correctCount INTEGER NOT NULL,
                        incorrectCount INTEGER NOT NULL,
                        hardCount INTEGER NOT NULL,
                        easyCount INTEGER NOT NULL DEFAULT 0,
                        stability REAL NOT NULL DEFAULT 0.0,
                        difficulty REAL NOT NULL DEFAULT 0.0,
                        scheduledDays INTEGER NOT NULL DEFAULT 0,
                        reps INTEGER NOT NULL DEFAULT 0,
                        lapses INTEGER NOT NULL DEFAULT 0,
                        state INTEGER NOT NULL DEFAULT 0,
                        lastReviewedAt INTEGER NOT NULL,
                        dueAt INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(categoryId) REFERENCES categories(id) ON DELETE CASCADE
                    )
                """)
                database.execSQL("""
                    INSERT INTO flashcards_new (
                        id, categoryId, question, answer, questionImagePath, answerImagePath,
                        isEnabled, correctCount, incorrectCount, hardCount, easyCount,
                        stability, difficulty, scheduledDays, reps, lapses, state,
                        lastReviewedAt, dueAt, createdAt, updatedAt
                    )
                    SELECT
                        id, categoryId, question, answer, questionImagePath, answerImagePath,
                        isEnabled, correctCount, incorrectCount, hardCount, 0,
                        0.0, 0.0, 0, 0, 0, 0,
                        lastReviewedAt, 0, createdAt, updatedAt
                    FROM flashcards
                """)
                database.execSQL("DROP TABLE flashcards")
                database.execSQL("ALTER TABLE flashcards_new RENAME TO flashcards")
                database.execSQL("CREATE INDEX index_flashcards_categoryId ON flashcards(categoryId)")
                database.execSQL("CREATE INDEX index_flashcards_dueAt ON flashcards(dueAt)")
            }
        }
        
        // Migration to clean up schema and remove old fields
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create new table with correct schema
                database.execSQL("""
                    CREATE TABLE flashcards_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        categoryId INTEGER NOT NULL,
                        question TEXT NOT NULL,
                        answer TEXT NOT NULL,
                        isEnabled INTEGER NOT NULL,
                        correctCount INTEGER NOT NULL,
                        incorrectCount INTEGER NOT NULL,
                        hardCount INTEGER NOT NULL,
                        easinessFactor REAL NOT NULL,
                        reviewCount INTEGER NOT NULL,
                        lastReviewedAt INTEGER NOT NULL,
                        cooldownUntil INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(categoryId) REFERENCES categories(id) ON DELETE CASCADE
                    )
                """)
                
                // Copy data from old table to new table, handling missing columns
                database.execSQL("""
                    INSERT INTO flashcards_new (
                        id, categoryId, question, answer, isEnabled, correctCount, incorrectCount,
                        hardCount, easinessFactor, reviewCount, lastReviewedAt, cooldownUntil, createdAt, updatedAt
                    )
                    SELECT 
                        id, categoryId, question, answer, isEnabled, correctCount, incorrectCount,
                        COALESCE(hardCount, 0), easinessFactor, reviewCount, lastReviewedAt, cooldownUntil, createdAt, updatedAt
                    FROM flashcards
                """)
                
                // Drop old table and rename new table
                database.execSQL("DROP TABLE flashcards")
                database.execSQL("ALTER TABLE flashcards_new RENAME TO flashcards")
                
                // Recreate index
                database.execSQL("CREATE INDEX index_flashcards_categoryId ON flashcards(categoryId)")
            }
        }
    }
}
