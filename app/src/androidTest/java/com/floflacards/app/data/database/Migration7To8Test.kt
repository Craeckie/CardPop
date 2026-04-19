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

package com.floflacards.app.data.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests MIGRATION_7_8 (SM-2 → FSRS schema swap).
 *
 * Sets up a v7-shape database manually via raw SQL (no MigrationTestHelper —
 * v7 was the last version with exportSchema=false, so no baseline JSON exists
 * to feed the helper). Inserts representative rows, runs the migration, and
 * asserts the v8 invariants:
 *   - all rows survive
 *   - correctCount / incorrectCount / hardCount / lastReviewedAt are preserved
 *   - easyCount + 7 FSRS columns are zeroed
 *   - the new index_flashcards_dueAt exists
 *
 * Once a v7 schema is captured, switch to MigrationTestHelper for schema-validated
 * coverage on top of these behavioural assertions.
 */
@RunWith(AndroidJUnit4::class)
class Migration7To8Test {

    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Always start clean; previous runs may have left this DB on disk.
        context.deleteDatabase(TEST_DB)
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DB)
            .callback(V7Callback())
            .build()
        helper = FrameworkSQLiteOpenHelperFactory().create(config)
    }

    @After
    fun tearDown() {
        helper.close()
        ApplicationProvider.getApplicationContext<android.content.Context>().deleteDatabase(TEST_DB)
    }

    @Test
    fun migrate_preserves_review_history_and_zeroes_fsrs_state() {
        val db = helper.writableDatabase
        seedV7Rows(db)

        FloatingLearningDatabase.MIGRATION_7_8.migrate(db)

        db.query("SELECT id, correctCount, incorrectCount, hardCount, easyCount, " +
            "stability, difficulty, scheduledDays, reps, lapses, state, " +
            "lastReviewedAt, dueAt FROM flashcards ORDER BY id ASC").use { c ->
            assertEquals("row count survived migration", 3, c.count)

            // Row 1: untouched card (no review history).
            assertTrue(c.moveToNext())
            assertEquals(1L, c.getLong(0))
            assertEquals(0, c.getInt(1))   // correctCount
            assertEquals(0, c.getInt(2))   // incorrectCount
            assertEquals(0, c.getInt(3))   // hardCount
            assertEquals(0, c.getInt(4))   // easyCount (new)
            assertEquals(0.0, c.getDouble(5), 0.0)  // stability
            assertEquals(0.0, c.getDouble(6), 0.0)  // difficulty
            assertEquals(0, c.getInt(7))   // scheduledDays
            assertEquals(0, c.getInt(8))   // reps
            assertEquals(0, c.getInt(9))   // lapses
            assertEquals(0, c.getInt(10))  // state == FsrsCardState.New.value
            assertEquals(0L, c.getLong(11)) // lastReviewedAt
            assertEquals(0L, c.getLong(12)) // dueAt zeroed (cooldownUntil discarded)

            // Row 2: card with review history.
            assertTrue(c.moveToNext())
            assertEquals(2L, c.getLong(0))
            assertEquals(7, c.getInt(1))
            assertEquals(2, c.getInt(2))
            assertEquals(3, c.getInt(3))
            assertEquals(0, c.getInt(4))
            // FSRS state must still be zeroed even though SM-2 had reviewCount=9.
            assertEquals(0.0, c.getDouble(5), 0.0)
            assertEquals(0.0, c.getDouble(6), 0.0)
            assertEquals(0, c.getInt(7))
            assertEquals(0, c.getInt(8))
            assertEquals(0, c.getInt(9))
            assertEquals(0, c.getInt(10))
            assertEquals(LAST_REVIEW_2, c.getLong(11))
            assertEquals(0L, c.getLong(12))

            // Row 3: card that was on cooldown — cooldownUntil is dropped, dueAt resets to 0
            // so the card becomes immediately reviewable post-upgrade (documented trade-off).
            assertTrue(c.moveToNext())
            assertEquals(3L, c.getLong(0))
            assertEquals(1, c.getInt(1))
            assertEquals(0, c.getInt(2))
            assertEquals(0, c.getInt(3))
            assertEquals(0, c.getInt(4))
            assertEquals(0L, c.getLong(12))
        }
    }

    @Test
    fun migrate_creates_dueAt_index() {
        val db = helper.writableDatabase
        seedV7Rows(db)

        FloatingLearningDatabase.MIGRATION_7_8.migrate(db)

        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'flashcards'").use { c ->
            val names = mutableListOf<String>()
            while (c.moveToNext()) names += c.getString(0)
            assertTrue("index_flashcards_categoryId should exist post-migration: $names",
                names.contains("index_flashcards_categoryId"))
            assertTrue("index_flashcards_dueAt should exist post-migration: $names",
                names.contains("index_flashcards_dueAt"))
        }
    }

    @Test
    fun migrate_drops_old_sm2_columns() {
        val db = helper.writableDatabase
        seedV7Rows(db)

        FloatingLearningDatabase.MIGRATION_7_8.migrate(db)

        db.query("PRAGMA table_info(flashcards)").use { c ->
            val cols = mutableListOf<String>()
            while (c.moveToNext()) cols += c.getString(c.getColumnIndexOrThrow("name"))
            assertTrue("easinessFactor should be dropped: $cols", !cols.contains("easinessFactor"))
            assertTrue("reviewCount should be dropped: $cols", !cols.contains("reviewCount"))
            assertTrue("cooldownUntil should be dropped: $cols", !cols.contains("cooldownUntil"))
            assertTrue("dueAt should exist: $cols", cols.contains("dueAt"))
            assertTrue("easyCount should exist: $cols", cols.contains("easyCount"))
            for (fsrsCol in listOf("stability", "difficulty", "scheduledDays",
                "reps", "lapses", "state")) {
                assertTrue("$fsrsCol should exist: $cols", cols.contains(fsrsCol))
            }
        }
    }

    private fun seedV7Rows(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO categories (id, name, isEnabled, createdAt, updatedAt) " +
            "VALUES (1, 'Test', 1, 0, 0)")
        // Untouched card.
        db.execSQL("INSERT INTO flashcards (id, categoryId, question, answer, " +
            "questionImagePath, answerImagePath, isEnabled, correctCount, incorrectCount, " +
            "hardCount, easinessFactor, reviewCount, lastReviewedAt, cooldownUntil, " +
            "createdAt, updatedAt) VALUES " +
            "(1, 1, 'q1', 'a1', NULL, NULL, 1, 0, 0, 0, 2.5, 0, 0, 0, 0, 0)")
        // Card with review history.
        db.execSQL("INSERT INTO flashcards (id, categoryId, question, answer, " +
            "questionImagePath, answerImagePath, isEnabled, correctCount, incorrectCount, " +
            "hardCount, easinessFactor, reviewCount, lastReviewedAt, cooldownUntil, " +
            "createdAt, updatedAt) VALUES " +
            "(2, 1, 'q2', 'a2', NULL, NULL, 1, 7, 2, 3, 2.3, 9, $LAST_REVIEW_2, 0, 0, 0)")
        // Card on cooldown.
        db.execSQL("INSERT INTO flashcards (id, categoryId, question, answer, " +
            "questionImagePath, answerImagePath, isEnabled, correctCount, incorrectCount, " +
            "hardCount, easinessFactor, reviewCount, lastReviewedAt, cooldownUntil, " +
            "createdAt, updatedAt) VALUES " +
            "(3, 1, 'q3', 'a3', NULL, NULL, 1, 1, 0, 0, 2.4, 1, 1000, 9999999999, 0, 0)")
    }

    /** Builds the v7 schema. Mirrors the cumulative effect of MIGRATION_5_6 and MIGRATION_6_7. */
    private class V7Callback : SupportSQLiteOpenHelper.Callback(7) {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE categories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    isEnabled INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
            """)
            db.execSQL("""
                CREATE TABLE flashcards (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    categoryId INTEGER NOT NULL,
                    question TEXT NOT NULL,
                    answer TEXT NOT NULL,
                    questionImagePath TEXT DEFAULT NULL,
                    answerImagePath TEXT DEFAULT NULL,
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
            db.execSQL("CREATE INDEX index_flashcards_categoryId ON flashcards(categoryId)")
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private companion object {
        const val TEST_DB = "migration_7_8_test"
        const val LAST_REVIEW_2 = 1_700_000_000_000L
    }
}
