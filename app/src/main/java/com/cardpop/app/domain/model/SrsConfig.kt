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

package com.cardpop.app.domain.model

/**
 * The four FSRS ratings the user can press, plus CLOSED for "dismissed without
 * rating" — emitted when the overlay is closed via the X button or auto-hides.
 * CLOSED is consumed by SrsUseCase as a no-op (FSRS state untouched).
 */
enum class FlashcardRating(val displayName: String) {
    WRONG("Again"),
    HARD("Hard"),
    GOOD("Good"),
    EASY("Easy"),
    CLOSED("Closed")
}
