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

enum class FsrsCardState(val value: Int) {
    New(0),        // Never reviewed
    Learning(1),   // In the initial learning phase
    Review(2),     // Graduated; schedules in days
    Relearning(3); // Lapsed from Review; back to short intervals

    companion object {
        fun fromValue(v: Int): FsrsCardState =
            entries.firstOrNull { it.value == v } ?: New
    }
}
