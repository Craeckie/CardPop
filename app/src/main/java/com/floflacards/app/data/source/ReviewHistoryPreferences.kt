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

package com.floflacards.app.data.source

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists per-day review counts and mastered totals for the Statistics chart.
 *
 * Storage layout (SharedPreferences "review_history_prefs"):
 *   "all_date_keys"  — StringSet of every "yyyy-MM-dd" key that has data
 *   "r_{dateKey}"    — Int, number of reviews on that day
 *   "m_{dateKey}"    — Int, running mastered total snapshotted after last review on that day
 */
@Singleton
class ReviewHistoryPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class RawEntry(val dateKey: String, val reviews: Int, val masteredTotal: Int)

    fun recordReview(masteredTotal: Int, now: Long = System.currentTimeMillis()) {
        val dateKey = dateKeyOf(now)
        val reviews = prefs.getInt(reviewKey(dateKey), 0) + 1
        val known = prefs.getStringSet(KEY_ALL_DATES, emptySet())!!.toMutableSet().also { it.add(dateKey) }
        prefs.edit()
            .putInt(reviewKey(dateKey), reviews)
            .putInt(masteredKey(dateKey), masteredTotal)
            .putStringSet(KEY_ALL_DATES, known)
            .apply()
    }

    /** Returns entries for the last [days] calendar days, oldest first. */
    fun getHistory(days: Int): List<ReviewHistoryEntry> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return (days - 1 downTo 0).map { i ->
            val day = (cal.clone() as Calendar).also { it.add(Calendar.DAY_OF_YEAR, -i) }
            val key = dateKeyOf(day.timeInMillis)
            ReviewHistoryEntry(
                dateKey = key,
                reviews = prefs.getInt(reviewKey(key), 0),
                masteredTotal = prefs.getInt(masteredKey(key), 0)
            )
        }
    }

    fun exportAll(): List<RawEntry> {
        val keys = prefs.getStringSet(KEY_ALL_DATES, emptySet()) ?: emptySet()
        return keys.sorted().map { key ->
            RawEntry(
                dateKey = key,
                reviews = prefs.getInt(reviewKey(key), 0),
                masteredTotal = prefs.getInt(masteredKey(key), 0)
            )
        }
    }

    fun importAll(entries: List<RawEntry>) {
        val editor = prefs.edit().clear()
        val keys = mutableSetOf<String>()
        for (e in entries) {
            editor.putInt(reviewKey(e.dateKey), e.reviews)
            editor.putInt(masteredKey(e.dateKey), e.masteredTotal)
            keys.add(e.dateKey)
        }
        editor.putStringSet(KEY_ALL_DATES, keys).apply()
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    private fun dateKeyOf(epochMs: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = epochMs
        return "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    private fun reviewKey(dateKey: String) = "r_$dateKey"
    private fun masteredKey(dateKey: String) = "m_$dateKey"

    private companion object {
        const val PREFS_NAME = "review_history_prefs"
        const val KEY_ALL_DATES = "all_date_keys"
    }
}
