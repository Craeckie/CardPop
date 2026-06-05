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

package com.cardpop.app.domain.usecase

/**
 * Pure decision logic for when a brand-new (FSRS-New) card may be surfaced on an
 * overlay tick. Kept Android-free so it is fully unit-testable; the timer service
 * supplies the live inputs (current hour, today's count, user settings).
 *
 * Two independent gates:
 *  - **Daily cap (hard)**: stop introducing new cards once today's count reaches
 *    [limitPerDay]. `-1` = unlimited, `0` = never introduce new, `N` = that many.
 *  - **Cutoff (soft bias)**: before [cutoffHour] new cards are *prioritized* (the
 *    caller lifts them above Review); from [cutoffHour] onward they are suppressed.
 *    When [cutoffEnabled] is false the cutoff has no effect — new cards keep their
 *    default lowest priority and only the cap applies.
 *
 * A new card is eligible only when **under the cap AND before the cutoff**.
 */
object NewCardGating {

    /**
     * @param allowNew      whether New cards may be shown at all on this tick.
     * @param prioritizeNew whether eligible New cards should be lifted above Review
     *                      (front-loading new material earlier in the day).
     */
    data class Decision(val allowNew: Boolean, val prioritizeNew: Boolean)

    fun decide(
        hourOfDay: Int,
        newCardsToday: Int,
        limitPerDay: Int,
        cutoffEnabled: Boolean,
        cutoffHour: Int
    ): Decision {
        val underCap = limitPerDay < 0 || newCardsToday < limitPerDay
        val beforeCutoff = !cutoffEnabled || hourOfDay < cutoffHour
        val allowNew = underCap && beforeCutoff
        // Only front-load (prioritize above reviews) when the cutoff feature is on.
        val prioritizeNew = allowNew && cutoffEnabled
        return Decision(allowNew, prioritizeNew)
    }
}
