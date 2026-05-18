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

package com.cardpop.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private const val PREFS_NAME = "floating_learning_settings"
        private const val KEY_IS_LEARNING_ACTIVE = "is_learning_active"
        private const val KEY_INTERVAL_MINUTES = "interval_minutes"
        private const val DEFAULT_INTERVAL_MINUTES = 5
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                Log.d(TAG, "Boot completed — rescheduling morning reminder")
                MorningReminderScheduler.schedule(context)
                restartLearningIfActive(context)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d(TAG, "Package replaced — restoring learning service state")
                MorningReminderScheduler.schedule(context)
                restartLearningIfActive(context)
            }
        }
    }

    private fun restartLearningIfActive(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isActive = prefs.getBoolean(KEY_IS_LEARNING_ACTIVE, false)
        if (isActive) {
            val interval = prefs.getInt(KEY_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES)
            Log.d(TAG, "Learning was active — restarting timer service (interval=$interval min)")
            TimerForegroundService.start(context, interval)
        }
    }
}
