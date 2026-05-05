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

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

import com.floflacards.app.R
import com.floflacards.app.presentation.screen.MainActivity
import com.floflacards.app.data.repository.FlashcardRepository
import com.floflacards.app.data.repository.SettingsRepository
import com.floflacards.app.domain.manager.ServiceStateManager
import com.floflacards.app.util.UsageStatsHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class TimerForegroundService : Service() {
    
    companion object {
        private const val TAG = "TimerForegroundService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "learning_timer_channel"
        private const val CHANNEL_NAME = "Learning Timer"
        private const val DEFAULT_INTERVAL_MINUTES = 5
        private const val ALARM_REQUEST_CODE = 1001
        private const val POSTPONE_DELAY_MS = 60_000L
        
        // Timer alarm constant
        const val ACTION_TIMER_ALARM = "com.example.myapplication.TIMER_ALARM"
        
        fun start(context: Context, intervalMinutes: Int) {
            val intent = Intent(context, TimerForegroundService::class.java).apply {
                putExtra("interval_minutes", intervalMinutes)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start service", e)
            }
        }
        
        fun stop(context: Context) {
            try {
                val intent = Intent(context, TimerForegroundService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop service", e)
            }
        }
    }
    
    @Inject
    lateinit var flashcardRepository: FlashcardRepository
    
    @Inject
    lateinit var settingsManager: SettingsRepository
    
    @Inject
    lateinit var serviceCommunicationManager: ServiceStateManager
    
    private lateinit var alarmManager: AlarmManager
    private var alarmPendingIntent: PendingIntent? = null
    private lateinit var powerManager: PowerManager
    private lateinit var wakeLock: PowerManager.WakeLock

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var isInitialized = false
    private var intervalMinutes = DEFAULT_INTERVAL_MINUTES
    private var timerStartTime = 0L
    private var countdownJob: Job? = null

    // Set when the interval alarm fires while the screen is off; the next
    // ACTION_SCREEN_ON triggers a 60s deferred display instead of showing
    // the overlay immediately.
    private var postponePending = false
    private var postponeJob: Job? = null
    private var screenOnReceiver: BroadcastReceiver? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate called")
        try {
            createNotificationChannel()
            initializeSystemServices()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            throw e
        }
    }
    
    private fun initializeSystemServices() {
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:FlashcardTimer"
        )

        registerScreenOnReceiver()

        Log.d(TAG, "System services initialized")
    }

    private fun registerScreenOnReceiver() {
        if (screenOnReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != Intent.ACTION_SCREEN_ON) return
                onScreenTurnedOn()
            }
        }
        registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        screenOnReceiver = receiver
    }

    private fun unregisterScreenOnReceiver() {
        screenOnReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering screen-on receiver", e)
            }
        }
        screenOnReceiver = null
    }
    

    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand called with action: ${intent?.action}")
        
        try {
            if (!::flashcardRepository.isInitialized || !::settingsManager.isInitialized) {
                Log.e(TAG, "Dependencies not initialized yet, stopping service")
                stopSelf()
                return START_NOT_STICKY
            }
            
            // Handle alarm trigger
            if (intent?.action == ACTION_TIMER_ALARM) {
                Log.d(TAG, "Timer alarm triggered")
                handleTimerAlarm()
                return START_STICKY
            }
            
            // Handle normal service start
            isInitialized = true
            intervalMinutes = intent?.getIntExtra("interval_minutes", DEFAULT_INTERVAL_MINUTES) ?: DEFAULT_INTERVAL_MINUTES

            startForeground(NOTIFICATION_ID, createNotification())
            serviceCommunicationManager.updateServiceStatus(true)
            startTimer()

            Log.d(TAG, "Timer service started with interval: $intervalMinutes minutes")
            return START_STICKY
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand", e)
            stopSelf()
            return START_NOT_STICKY
        }
    }
    
    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy called")
        try {
            stopTimer()
            unregisterScreenOnReceiver()
            releaseWakeLock()
            isInitialized = false
            serviceCommunicationManager.updateServiceStatus(false)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        } finally {
            super.onDestroy()
        }
    }
    
    private fun startTimer() {
        if (!isInitialized) {
            Log.w(TAG, "Service not initialized, cannot start timer")
            return
        }
        
        try {
            // Cancel any existing alarm and countdown
            stopTimer()
            
            // Set timer start time
            timerStartTime = System.currentTimeMillis()
            
            // Schedule alarm for flashcard display
            val triggerTime = timerStartTime + (intervalMinutes * 60 * 1000L)
            val intent = Intent(this, TimerForegroundService::class.java).apply {
                action = ACTION_TIMER_ALARM
            }
            alarmPendingIntent = PendingIntent.getService(
                this,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    alarmPendingIntent!!
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    alarmPendingIntent!!
                )
            }

            // Start simple countdown updates
            startCountdownUpdates(timerStartTime, intervalMinutes * 60 * 1000L)
            
            Log.d(TAG, "Timer started for $intervalMinutes minutes")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting timer", e)
        }
    }
    
    private fun stopTimer() {
        try {
            // Cancel alarm
            alarmPendingIntent?.let { pendingIntent ->
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                alarmPendingIntent = null
            }

            // Cancel countdown updates
            countdownJob?.cancel()
            countdownJob = null

            // Cancel any pending postponed display
            postponeJob?.cancel()
            postponeJob = null
            postponePending = false

            Log.d(TAG, "Timer stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping timer", e)
        }
    }
    
    private fun startCountdownUpdates(startTime: Long, durationMs: Long) {
        countdownJob?.cancel()
        countdownJob = serviceScope.launch {
            try {
                while (isInitialized) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val remaining = maxOf(0L, durationMs - elapsed) / 1000L

                    broadcastCountdownUpdate(remaining)

                    if (remaining <= 0) break
                    delay(1000L)
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Countdown updates cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Error in countdown updates", e)
            }
        }
    }
    
    private fun broadcastCountdownUpdate(countdown: Long) {
        try {
            serviceCommunicationManager.updateCountdownTime(countdown)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating countdown", e)
        }
    }
    

    private fun releaseWakeLock() {
        try {
            if (wakeLock.isHeld) {
                wakeLock.release()
                Log.d(TAG, "WakeLock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WakeLock", e)
        }
    }
    
    private fun handleTimerAlarm() {
        if (!isInitialized) {
            Log.w(TAG, "Service not initialized, ignoring alarm")
            return
        }

        // Stop countdown updates regardless — the interval has elapsed.
        countdownJob?.cancel()
        countdownJob = null

        if (!powerManager.isInteractive) {
            Log.d(TAG, "Screen is off, deferring flashcard until 60s after screen-on")
            postponePending = true
            // Don't restart the timer; OverlayService restarts it after the
            // deferred display once the user interacts with the overlay.
            return
        }

        serviceScope.launch {
            try {
                // Acquire wake lock for flashcard display
                if (!wakeLock.isHeld) {
                    wakeLock.acquire(30000) // 30 seconds timeout
                }

                showFlashcard()

            } catch (e: Exception) {
                Log.e(TAG, "Error handling timer alarm", e)
            }
            // Timer will be restarted by OverlayService after user interaction
        }
    }

    private fun onScreenTurnedOn() {
        if (!postponePending) return
        if (!isInitialized) return
        if (!settingsManager.getIsLearningActive()) {
            postponePending = false
            return
        }

        postponePending = false
        postponeJob?.cancel()
        // Surface the 60s wait as a normal countdown so the main screen pill
        // shows "Next in 0:60" instead of falling back to "Preparing…".
        startCountdownUpdates(System.currentTimeMillis(), POSTPONE_DELAY_MS)
        postponeJob = serviceScope.launch {
            try {
                delay(POSTPONE_DELAY_MS)

                if (!isInitialized || !settingsManager.getIsLearningActive()) {
                    return@launch
                }

                if (!powerManager.isInteractive) {
                    // Screen went back off during the wait — re-arm and let
                    // the next ACTION_SCREEN_ON trigger another 60s wait.
                    postponePending = true
                    countdownJob?.cancel()
                    countdownJob = null
                    return@launch
                }

                if (!wakeLock.isHeld) {
                    wakeLock.acquire(30000)
                }
                showFlashcard()
            } catch (e: CancellationException) {
                Log.d(TAG, "Postponed display cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Error in postponed display", e)
            }
        }
    }
    

    

    
    private suspend fun showFlashcard() {
        if (!isInitialized) {
            Log.w(TAG, "Service not initialized, cannot show flashcard")
            return
        }
        
        // CRITICAL FIX: Check if learning is still active before showing flashcard
        // This prevents leftover scheduled alarms from showing flashcards when learning is stopped
        if (!settingsManager.getIsLearningActive()) {
            Log.d(TAG, "Learning is inactive, not showing flashcard - stopping service")
            stopSelf()
            return
        }
        
        try {
            if (shouldSkipForBlocklist()) {
                Log.i(TAG, "Skipping tick: foreground app is on blocklist, rescheduling in $intervalMinutes min")
                startTimer()
                return
            }

            val nextFlashcard = flashcardRepository.getNextAvailableFlashcard()

            // Always show flashcard - repository now guarantees a result (regular or empty state)
            Log.d(TAG, "Showing flashcard: ${nextFlashcard.id}")
            OverlayService.startWithFlashcard(this@TimerForegroundService, nextFlashcard)

        } catch (e: Exception) {
            Log.e(TAG, "Error showing flashcard", e)
        } finally {
            // Release wake lock after processing
            releaseWakeLock()
        }
    }

    /**
     * True when the user is inside an app they've added to the blocklist. The
     * lookup silently returns false if usage access was never granted (or was
     * revoked), so the feature degrades to "always show" rather than blocking
     * the overlay entirely.
     */
    private fun shouldSkipForBlocklist(): Boolean {
        val blocklist = settingsManager.getBlocklist()
        if (blocklist.isEmpty()) {
            Log.d(TAG, "Blocklist empty, proceeding with overlay")
            return false
        }
        if (!UsageStatsHelper.hasAccess(this)) {
            Log.w(TAG, "Blocklist has ${blocklist.size} entries but usage access is not granted; proceeding with overlay")
            return false
        }
        val fg = UsageStatsHelper.currentForegroundPackage(this)
        if (fg == null) {
            Log.d(TAG, "Could not determine foreground app, proceeding with overlay")
            return false
        }
        val blocked = fg in blocklist
        Log.d(TAG, "Foreground app: $fg — blocked=$blocked (blocklist size=${blocklist.size})")
        return blocked
    }
    

    

    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows learning timer status"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Learning Timer Active")
            .setContentText("Flashcards will appear at regular intervals")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    private fun notifyNoFlashcardsAvailable() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("No Flashcards Available")
                .setContentText("All flashcards are cooling down. Timer will continue.")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .build()
            
            notificationManager.notify(NOTIFICATION_ID + 1, notification)
            Log.d(TAG, "No flashcards notification sent")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing no flashcards notification", e)
        }
    }
}
