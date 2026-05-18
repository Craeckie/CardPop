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

package com.cardpop.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "flashcards",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["dueAt"])
    ]
)
data class FlashcardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val categoryId: Long,
    val question: String,
    val answer: String,
    val questionImagePath: String? = null,
    val answerImagePath: String? = null,
    val isEnabled: Boolean = true,

    // Per-rating counters (display-only stats)
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val hardCount: Int = 0,
    val easyCount: Int = 0,

    // FSRS state. Zero values represent a brand-new card.
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val scheduledDays: Int = 0,
    val reps: Int = 0,
    val lapses: Int = 0,
    // FsrsCardState.value: 0=New, 1=Learning, 2=Review, 3=Relearning
    val state: Int = 0,
    val lastReviewedAt: Long = 0,
    val dueAt: Long = 0,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
