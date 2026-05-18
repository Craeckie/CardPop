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

package com.cardpop.app.util

/**
 * Interval-related constants.
 * Centralizes magic numbers to follow DRY principle and ease maintenance.
 */
object IntervalConstants {
    /** Minimum allowed interval in minutes */
    const val MIN_INTERVAL_MINUTES = 1
    
    /** Maximum allowed interval in minutes (24 hours) */
    const val MAX_INTERVAL_MINUTES = 1440
    
    /** Default interval in minutes */
    const val DEFAULT_INTERVAL_MINUTES = 5
    
    /** Predefined intervals for quick selection */
    val PREDEFINED_INTERVALS = listOf(1, 5, 10, 15, 30)
    
    /**
     * Validates if the given interval is within acceptable range.
     */
    fun isValidInterval(minutes: Int): Boolean {
        return minutes in MIN_INTERVAL_MINUTES..MAX_INTERVAL_MINUTES
    }
    
    /**
     * Parses and validates interval from string input.
     * Returns null if invalid.
     */
    fun parseInterval(input: String): Int? {
        return input.toIntOrNull()?.takeIf { isValidInterval(it) }
    }
}
