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

package com.cardpop.app.data.source

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks how many brand-new cards have been introduced *today* so the overlay can
 * enforce a daily new-card limit. Only the current day matters, so storage is
 * bounded to a single (dateKey, count) pair: when the stored date is not today the
 * count is treated as 0 and overwritten on the next [increment].
 *
 * Storage layout (SharedPreferences "new_card_prefs"):
 *   "date"  — String "yyyy-MM-dd" of the last increment
 *   "count" — Int, number of new cards introduced on that date
 */
@Singleton
class NewCardPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** New cards introduced since local midnight; 0 once the day rolls over. */
    fun getCountToday(now: Long = System.currentTimeMillis()): Int {
        return if (prefs.getString(KEY_DATE, null) == dateKeyOf(now)) {
            prefs.getInt(KEY_COUNT, 0)
        } else {
            0
        }
    }

    /** Records that one more new card was introduced, rolling the day if needed. */
    fun increment(now: Long = System.currentTimeMillis()) {
        val today = dateKeyOf(now)
        val current = if (prefs.getString(KEY_DATE, null) == today) {
            prefs.getInt(KEY_COUNT, 0)
        } else {
            0
        }
        prefs.edit()
            .putString(KEY_DATE, today)
            .putInt(KEY_COUNT, current + 1)
            .apply()
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

    private companion object {
        const val PREFS_NAME = "new_card_prefs"
        const val KEY_DATE = "date"
        const val KEY_COUNT = "count"
    }
}
