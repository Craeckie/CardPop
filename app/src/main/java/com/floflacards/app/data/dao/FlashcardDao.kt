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

package com.floflacards.app.data.dao

import androidx.room.*
import com.floflacards.app.data.entity.FlashcardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FlashcardDao {
    
    @Query("SELECT * FROM flashcards WHERE categoryId = :categoryId ORDER BY createdAt ASC")
    fun getFlashcardsByCategory(categoryId: Long): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE categoryId = :categoryId ORDER BY createdAt ASC")
    suspend fun getFlashcardsByCategorySync(categoryId: Long): List<FlashcardEntity>
    
    @Query("""
        SELECT f.* FROM flashcards f 
        INNER JOIN categories c ON f.categoryId = c.id 
        WHERE f.isEnabled = 1 AND c.isEnabled = 1
        ORDER BY f.createdAt ASC
    """)
    suspend fun getAllFlashcards(): List<FlashcardEntity>
    
    @Query("""
        SELECT f.* FROM flashcards f 
        INNER JOIN categories c ON f.categoryId = c.id 
        ORDER BY f.createdAt ASC
    """)
    suspend fun getAllFlashcardsForStatistics(): List<FlashcardEntity>
    
    @Query("SELECT * FROM flashcards WHERE id = :id")
    suspend fun getFlashcardById(id: Long): FlashcardEntity?
    
    /**
     * Picks the highest-priority due card. State priority puts Relearning first
     * (fix lapses ASAP), then Learning, then Review, then New (introduce new
     * material last so overdue reviews always win). Within a state, oldest
     * dueAt wins; ties broken by higher difficulty, then RANDOM() so a stale
     * tie doesn't always show the same card.
     */
    @Query("""
        SELECT f.* FROM flashcards f
        INNER JOIN categories c ON f.categoryId = c.id
        WHERE f.isEnabled = 1 AND c.isEnabled = 1
        AND f.dueAt <= :now
        ORDER BY
            CASE f.state
                WHEN 3 THEN 0
                WHEN 1 THEN 1
                WHEN 2 THEN 2
                WHEN 0 THEN 3
            END ASC,
            f.dueAt ASC,
            f.difficulty DESC,
            RANDOM()
        LIMIT 1
    """)
    suspend fun getNextDueFlashcard(now: Long = System.currentTimeMillis()): FlashcardEntity?

    /**
     * Fallback when nothing is due: pick the card whose dueAt is closest to now.
     * Used so the overlay tick never returns null while there are any enabled cards.
     */
    @Query("""
        SELECT f.* FROM flashcards f
        INNER JOIN categories c ON f.categoryId = c.id
        WHERE f.isEnabled = 1 AND c.isEnabled = 1
        ORDER BY f.dueAt ASC, f.difficulty DESC, RANDOM()
        LIMIT 1
    """)
    suspend fun getNearestDueFlashcard(): FlashcardEntity?

    /**
     * Gets the next available flashcard, guaranteeing a result if any cards exist.
     * First tries to get a card whose FSRS due-date has passed, then falls back to
     * the card whose due-date is nearest.
     */
    suspend fun getNextAvailableFlashcard(now: Long = System.currentTimeMillis()): FlashcardEntity? {
        getNextDueFlashcard(now)?.let { return it }
        return getNearestDueFlashcard()
    }
    
    @Query("""
        SELECT COUNT(*) FROM flashcards f
        INNER JOIN categories c ON f.categoryId = c.id
        WHERE f.isEnabled = 1 AND c.isEnabled = 1
    """)
    suspend fun getActiveFlashcardCount(): Int

    /**
     * FSRS card-state breakdown for the stats screen. Filtered to enabled
     * flashcards in enabled categories so it matches the population the overlay
     * actually picks from. State values: 0=New, 1=Learning, 2=Review, 3=Relearning.
     */
    @Query("""
        SELECT f.state AS state, COUNT(*) AS count FROM flashcards f
        INNER JOIN categories c ON f.categoryId = c.id
        WHERE f.isEnabled = 1 AND c.isEnabled = 1
        GROUP BY f.state
    """)
    suspend fun getCardCountsByState(): List<StateCount>

    /**
     * Number of enabled cards whose FSRS due time has passed — i.e. how many
     * cards the overlay would consider "due" right now.
     */
    @Query("""
        SELECT COUNT(*) FROM flashcards f
        INNER JOIN categories c ON f.categoryId = c.id
        WHERE f.isEnabled = 1 AND c.isEnabled = 1 AND f.dueAt <= :now
    """)
    suspend fun getDueNowCount(now: Long = System.currentTimeMillis()): Int

    /**
     * Count of mastered cards across the whole deck (stability ≥ 21 days and at
     * least 3 reps), matching the definition used by StatisticsViewModel.
     * Includes disabled cards/categories so the history snapshot doesn't drop
     * when the user toggles a category off.
     */
    @Query("SELECT COUNT(*) FROM flashcards WHERE stability >= 21.0 AND reps >= 3")
    suspend fun getMasteredCount(): Int

    data class StateCount(val state: Int, val count: Int)
    
    @Query("SELECT COUNT(*) FROM flashcards WHERE categoryId = :categoryId")
    suspend fun getFlashcardCountByCategory(categoryId: Long): Int
    
    @Insert
    suspend fun insertFlashcard(flashcard: FlashcardEntity): Long
    
    @Update
    suspend fun updateFlashcard(flashcard: FlashcardEntity)
    
    @Delete
    suspend fun deleteFlashcard(flashcard: FlashcardEntity)
    
    @Query("DELETE FROM flashcards WHERE id = :id")
    suspend fun deleteFlashcardById(id: Long)
    
    @Query("DELETE FROM flashcards WHERE categoryId = :categoryId")
    suspend fun deleteFlashcardsByCategoryId(categoryId: Long)
    
    // Statistics reset methods. Reverts the card to FSRS-New (state=0, all FSRS
    // counters zeroed, dueAt=0 so it becomes immediately reviewable) and clears
    // per-rating display counters.
    @Query("""
        UPDATE flashcards
        SET correctCount = 0,
            incorrectCount = 0,
            hardCount = 0,
            easyCount = 0,
            stability = 0.0,
            difficulty = 0.0,
            scheduledDays = 0,
            reps = 0,
            lapses = 0,
            state = 0,
            lastReviewedAt = 0,
            dueAt = 0,
            updatedAt = :timestamp
        WHERE id = :flashcardId
    """)
    suspend fun resetFlashcardStatistics(flashcardId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("""
        UPDATE flashcards
        SET correctCount = 0,
            incorrectCount = 0,
            hardCount = 0,
            easyCount = 0,
            stability = 0.0,
            difficulty = 0.0,
            scheduledDays = 0,
            reps = 0,
            lapses = 0,
            state = 0,
            lastReviewedAt = 0,
            dueAt = 0,
            updatedAt = :timestamp
        WHERE categoryId = :categoryId
    """)
    suspend fun resetCategoryStatistics(categoryId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("""
        UPDATE flashcards
        SET correctCount = 0,
            incorrectCount = 0,
            hardCount = 0,
            easyCount = 0,
            stability = 0.0,
            difficulty = 0.0,
            scheduledDays = 0,
            reps = 0,
            lapses = 0,
            state = 0,
            lastReviewedAt = 0,
            dueAt = 0,
            updatedAt = :timestamp
    """)
    suspend fun resetAllStatistics(timestamp: Long = System.currentTimeMillis())
    
    // Backup-specific methods
    @Query("SELECT * FROM flashcards WHERE question = :question AND answer = :answer LIMIT 1")
    suspend fun getFlashcardByQuestionAndAnswer(question: String, answer: String): FlashcardEntity?
    
    @Query("DELETE FROM flashcards")
    suspend fun deleteAllFlashcards()
    
    // Bulk operations for select/deselect all functionality
    @Query("UPDATE flashcards SET isEnabled = 1, updatedAt = :timestamp WHERE categoryId = :categoryId")
    suspend fun enableAllFlashcardsInCategory(categoryId: Long, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE flashcards SET isEnabled = 0, updatedAt = :timestamp WHERE categoryId = :categoryId")
    suspend fun disableAllFlashcardsInCategory(categoryId: Long, timestamp: Long = System.currentTimeMillis())
    
    @Query("SELECT COUNT(*) FROM flashcards WHERE categoryId = :categoryId AND isEnabled = 1")
    suspend fun getEnabledFlashcardCountByCategory(categoryId: Long): Int

    // CSV import — batch insert for performance
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcardsBatch(flashcards: List<FlashcardEntity>): List<Long>

    // CSV import — duplicate detection across all categories
    @Query("SELECT question, answer FROM flashcards")
    suspend fun getExistingQuestionAnswerPairsAllCategories(): List<QuestionAnswerPair>

    /**
     * Simple data class for duplicate detection.
     */
    data class QuestionAnswerPair(
        val question: String,
        val answer: String
    )
}
