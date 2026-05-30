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

/** Overall assessment of a user's study habits derived from FSRS and review data. */
enum class StudyHealthStatus {
    /** No issues — study habits look great. */
    ON_TRACK,
    /** Minor issues — worth a small adjustment but nothing urgent. */
    GOOD,
    /** One or more significant issues detected. */
    NEEDS_ATTENTION,
    /** Too little data to judge; show an encouraging prompt instead. */
    GETTING_STARTED
}

/** Actionable suggestion keys. Mapped to localized strings in the UI layer. */
enum class StudyTip {
    // Severe — drives NEEDS_ATTENTION status
    CATCH_UP_BACKLOG,
    LOW_ACCURACY,
    LOW_STABILITY_CHURN,
    // Minor — drives GOOD status
    HOLD_OFF_NEW_CARDS,
    LEECHES,
    DECK_TOO_HARD,
    STUDY_DAILY,
    // Positive / informational — shown when nothing is wrong
    ADD_MORE_CARDS,
    RETENTION_HIGH,
    KEEP_GOING
}

/**
 * Summary of a user's study health.
 *
 * @param status     Overall status label.
 * @param tips       Tips ranked by severity (severe first). The UI displays the top two.
 * @param leechCount Non-zero when [StudyTip.LEECHES] is in [tips]; used by the UI to
 *                   show the count and offer a tap-through filter.
 */
data class StudyHealth(
    val status: StudyHealthStatus,
    val tips: List<StudyTip>,
    val leechCount: Int = 0
)
