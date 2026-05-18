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
import com.cardpop.app.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receives the snooze alarm and restarts the learning timer.
 * If the user manually started/stopped learning before the alarm fires,
 * `pausedUntil` will have been cleared to 0 and this receiver no-ops.
 */
@AndroidEntryPoint
class SnoozeBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var learningServiceManager: LearningServiceManager
    @Inject lateinit var settingsManager: SettingsRepository

    companion object {
        private const val TAG = "SnoozeBroadcastReceiver"
        const val ACTION_RESUME_AFTER_SNOOZE = "com.cardpop.action.RESUME_AFTER_SNOOZE"
        const val ALARM_REQUEST_CODE = 2001
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RESUME_AFTER_SNOOZE) return

        val pausedUntil = settingsManager.getPausedUntil()
        if (pausedUntil == 0L) {
            Log.d(TAG, "pausedUntil is 0 — snooze was cancelled by manual action, skipping resume")
            return
        }

        Log.d(TAG, "Snooze alarm fired, resuming learning")
        settingsManager.setPausedUntil(0L)
        learningServiceManager.startLearningService(settingsManager.getIntervalMinutes())
    }
}
