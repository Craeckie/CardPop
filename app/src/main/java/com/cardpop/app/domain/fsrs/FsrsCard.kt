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

package com.cardpop.app.domain.fsrs

// Pure-domain snapshot of a card's FSRS state. Constructed from FlashcardEntity,
// mutated by Fsrs.calculate(), written back to the entity by SrsUseCase.
data class FsrsCard(
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val scheduledDays: Int = 0,
    val elapsedDays: Int = 0,       // days since last review (0 for new/same-day)
    val reps: Int = 0,
    val lapses: Int = 0,
    val state: FsrsCardState = FsrsCardState.New,
    val lastReviewAt: Long = 0L     // epoch ms, 0 if never reviewed
)
