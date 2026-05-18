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

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.cardpop.app.service.TimerForegroundService
import com.cardpop.app.data.repository.SettingsRepository
import com.cardpop.app.domain.manager.ServiceStateManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LearningServiceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsRepository,
    private val serviceCommunicationManager: ServiceStateManager
) {
    val isServiceActive: StateFlow<Boolean> = serviceCommunicationManager.isServiceActive
    val countdownTime: StateFlow<Long> = serviceCommunicationManager.countdownTime

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        checkServiceState()
    }

    fun startLearningService(intervalMinutes: Int) {
        cancelSnoozeAlarm()
        settingsManager.setPausedUntil(0L)
        serviceCommunicationManager.updateServiceStatus(true)
        settingsManager.setLearningActive(true)
        TimerForegroundService.start(context, intervalMinutes)
    }

    fun stopLearningService() {
        cancelSnoozeAlarm()
        settingsManager.setPausedUntil(0L)
        serviceCommunicationManager.updateServiceStatus(false)
        serviceCommunicationManager.resetCountdown()
        settingsManager.setLearningActive(false)
        TimerForegroundService.stop(context)
    }

    private fun cancelSnoozeAlarm() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, SnoozeBroadcastReceiver::class.java).apply {
            action = SnoozeBroadcastReceiver.ACTION_RESUME_AFTER_SNOOZE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            SnoozeBroadcastReceiver.ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
    

    
    private fun checkServiceState() {
        // Service state is now managed by ServiceStateManager
        // No additional logic needed here
    }
    
    fun cleanup() {
        serviceScope.cancel()
    }
}
