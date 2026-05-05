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

package com.floflacards.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.floflacards.app.R
import com.floflacards.app.data.repository.SettingsRepository
import com.floflacards.app.presentation.screen.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MorningReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsManager: SettingsRepository

    companion object {
        private const val TAG = "MorningReminderReceiver"
        const val ACTION_MORNING_REMINDER = "com.floflacards.action.MORNING_REMINDER"
        private const val CHANNEL_ID = "morning_reminder_channel"
        private const val NOTIFICATION_ID = 3001
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MORNING_REMINDER) return

        // Reschedule for tomorrow before any early returns.
        MorningReminderScheduler.schedule(context)

        if (settingsManager.getIsLearningActive()) {
            Log.d(TAG, "Learning is active — skipping morning reminder")
            return
        }

        Log.d(TAG, "Learning is stopped — showing morning reminder notification")
        showNotification(context)
    }

    private fun showNotification(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Morning Reminder",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily reminder to start your learning session"
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Time to study!")
            .setContentText("Your flashcard session hasn't started today.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(tapPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
