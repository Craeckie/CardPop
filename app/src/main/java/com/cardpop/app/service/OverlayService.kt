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
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.cardpop.app.R
import com.cardpop.app.data.entity.FlashcardEntity
import com.cardpop.app.domain.model.FlashcardRating
import com.cardpop.app.domain.usecase.SrsUseCase
import com.cardpop.app.domain.usecase.SimpleStreakUseCase
import com.cardpop.app.data.repository.FlashcardRepository
import com.cardpop.app.data.dao.CategoryDao
import com.cardpop.app.data.repository.SettingsRepository
import com.cardpop.app.data.source.FlashcardUiPreferences
import com.cardpop.app.service.ViewModelStoreManager
import com.cardpop.app.service.LearningServiceManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    
    companion object {
        private const val TAG = "OverlayService"
        private const val EXTRA_FLASHCARD_ID = "flashcard_id"
        private const val EXTRA_FLASHCARD_QUESTION = "flashcard_question"
        private const val EXTRA_FLASHCARD_ANSWER = "flashcard_answer"
        private const val EXTRA_FLASHCARD_CATEGORY_ID = "flashcard_category_id"
        private const val EXTRA_FLASHCARD_IS_ENABLED = "flashcard_is_enabled"
        private const val EXTRA_FLASHCARD_CORRECT_COUNT = "flashcard_correct_count"
        private const val EXTRA_FLASHCARD_INCORRECT_COUNT = "flashcard_incorrect_count"
        private const val EXTRA_FLASHCARD_HARD_COUNT = "flashcard_hard_count"
        private const val EXTRA_FLASHCARD_EASY_COUNT = "flashcard_easy_count"
        private const val EXTRA_FLASHCARD_STABILITY = "flashcard_stability"
        private const val EXTRA_FLASHCARD_DIFFICULTY = "flashcard_difficulty"
        private const val EXTRA_FLASHCARD_SCHEDULED_DAYS = "flashcard_scheduled_days"
        private const val EXTRA_FLASHCARD_REPS = "flashcard_reps"
        private const val EXTRA_FLASHCARD_LAPSES = "flashcard_lapses"
        private const val EXTRA_FLASHCARD_STATE = "flashcard_state"
        private const val EXTRA_FLASHCARD_LAST_REVIEWED_AT = "flashcard_last_reviewed_at"
        private const val EXTRA_FLASHCARD_DUE_AT = "flashcard_due_at"
        private const val EXTRA_FLASHCARD_CREATED_AT = "flashcard_created_at"
        private const val EXTRA_FLASHCARD_UPDATED_AT = "flashcard_updated_at"
        private const val EXTRA_FLASHCARD_QUESTION_IMAGE_PATH = "flashcard_question_image_path"
        private const val EXTRA_FLASHCARD_ANSWER_IMAGE_PATH = "flashcard_answer_image_path"
        
        fun startWithFlashcard(context: Context, flashcard: FlashcardEntity) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                Log.e(TAG, "Overlay permission not granted")
                return
            }
            
            val intent = Intent(context, OverlayService::class.java).apply {
                putExtra(EXTRA_FLASHCARD_ID, flashcard.id)
                putExtra(EXTRA_FLASHCARD_QUESTION, flashcard.question)
                putExtra(EXTRA_FLASHCARD_ANSWER, flashcard.answer)
                putExtra(EXTRA_FLASHCARD_CATEGORY_ID, flashcard.categoryId)
                putExtra(EXTRA_FLASHCARD_IS_ENABLED, flashcard.isEnabled)
                putExtra(EXTRA_FLASHCARD_CORRECT_COUNT, flashcard.correctCount)
                putExtra(EXTRA_FLASHCARD_INCORRECT_COUNT, flashcard.incorrectCount)
                putExtra(EXTRA_FLASHCARD_HARD_COUNT, flashcard.hardCount)
                putExtra(EXTRA_FLASHCARD_EASY_COUNT, flashcard.easyCount)
                putExtra(EXTRA_FLASHCARD_STABILITY, flashcard.stability)
                putExtra(EXTRA_FLASHCARD_DIFFICULTY, flashcard.difficulty)
                putExtra(EXTRA_FLASHCARD_SCHEDULED_DAYS, flashcard.scheduledDays)
                putExtra(EXTRA_FLASHCARD_REPS, flashcard.reps)
                putExtra(EXTRA_FLASHCARD_LAPSES, flashcard.lapses)
                putExtra(EXTRA_FLASHCARD_STATE, flashcard.state)
                putExtra(EXTRA_FLASHCARD_LAST_REVIEWED_AT, flashcard.lastReviewedAt)
                putExtra(EXTRA_FLASHCARD_DUE_AT, flashcard.dueAt)
                putExtra(EXTRA_FLASHCARD_CREATED_AT, flashcard.createdAt)
                putExtra(EXTRA_FLASHCARD_UPDATED_AT, flashcard.updatedAt)
                putExtra(EXTRA_FLASHCARD_QUESTION_IMAGE_PATH, flashcard.questionImagePath)
                putExtra(EXTRA_FLASHCARD_ANSWER_IMAGE_PATH, flashcard.answerImagePath)
            }
            
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start overlay service", e)
            }
        }
        
        /**
         * Starts overlay service with demo flashcard for first-time users.
         * Follows SRP by separating demo logic from regular flashcard display.
         */
        fun startWithDemoFlashcard(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                Log.e(TAG, "Overlay permission not granted for demo")
                return
            }
            
            // Create demo flashcard with educational content. Defaults match a brand-new
            // FSRS card (state=New, all FSRS fields zeroed) — the demo never goes through
            // SrsUseCase so these values are never persisted.
            val demoFlashcard = FlashcardEntity(
                id = -1L, // Special ID to indicate demo
                categoryId = -1L,
                question = context.getString(R.string.demo_welcome_question),
                answer = context.getString(R.string.demo_welcome_answer),
                isEnabled = true,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            
            startWithFlashcard(context, demoFlashcard)
        }
    }
    
    @Inject
    lateinit var srsUseCase: SrsUseCase
    
    @Inject
    lateinit var simpleStreakUseCase: SimpleStreakUseCase
    
    @Inject
    lateinit var flashcardRepository: FlashcardRepository
    
    @Inject
    lateinit var categoryDao: CategoryDao
    
    @Inject
    lateinit var settingsManager: SettingsRepository
    
    @Inject
    lateinit var flashcardUiPreferences: FlashcardUiPreferences
    
    @Inject
    lateinit var viewModelStoreManager: ViewModelStoreManager
    
    @Inject
    lateinit var learningServiceManager: LearningServiceManager
    
    // Extracted components following SOLID principles
    private lateinit var overlayManager: OverlayManager
    private lateinit var overlayComponents: OverlayComponents
    
    private var flashcard: FlashcardEntity? = null
    
    // Service scope for coroutines
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Lifecycle components
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore
        get() = viewModelStoreManager.getOverlayViewModelStore()
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "OverlayService onCreate")
        try {
            // Initialize extracted components following SOLID principles
            overlayManager = OverlayManager(this, flashcardUiPreferences)
            overlayComponents = OverlayComponents(categoryDao, flashcardUiPreferences, settingsManager)
            
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            stopSelf()
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "OverlayService onStartCommand")
        try {
            val flashcard = intent?.let { extractFlashcardFromIntent(it) }
            if (flashcard != null) {
                if (::overlayManager.isInitialized && overlayManager.isOverlayActive()) {
                    Log.d(TAG, "Overlay already showing; ignoring new flashcard request")
                    return START_NOT_STICKY
                }

                this.flashcard = flashcard

                // Mark demo as running if this is a demo flashcard
                if (flashcard.id == -1L) {
                    settingsManager.setDemoRunning(true)
                    Log.d(TAG, "Demo flashcard started, marked as running")
                }

                showOverlay(flashcard)
            } else {
                Log.e(TAG, "No flashcard data in intent")
                stopSelf()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }
    
    private fun extractFlashcardFromIntent(intent: Intent): FlashcardEntity? {
        return FlashcardEntity(
            id = intent.getLongExtra(EXTRA_FLASHCARD_ID, 0),
            categoryId = intent.getLongExtra(EXTRA_FLASHCARD_CATEGORY_ID, 0),
            question = intent.getStringExtra(EXTRA_FLASHCARD_QUESTION) ?: "",
            answer = intent.getStringExtra(EXTRA_FLASHCARD_ANSWER) ?: "",
            questionImagePath = intent.getStringExtra(EXTRA_FLASHCARD_QUESTION_IMAGE_PATH),
            answerImagePath = intent.getStringExtra(EXTRA_FLASHCARD_ANSWER_IMAGE_PATH),
            isEnabled = intent.getBooleanExtra(EXTRA_FLASHCARD_IS_ENABLED, true),
            correctCount = intent.getIntExtra(EXTRA_FLASHCARD_CORRECT_COUNT, 0),
            incorrectCount = intent.getIntExtra(EXTRA_FLASHCARD_INCORRECT_COUNT, 0),
            hardCount = intent.getIntExtra(EXTRA_FLASHCARD_HARD_COUNT, 0),
            easyCount = intent.getIntExtra(EXTRA_FLASHCARD_EASY_COUNT, 0),
            stability = intent.getDoubleExtra(EXTRA_FLASHCARD_STABILITY, 0.0),
            difficulty = intent.getDoubleExtra(EXTRA_FLASHCARD_DIFFICULTY, 0.0),
            scheduledDays = intent.getIntExtra(EXTRA_FLASHCARD_SCHEDULED_DAYS, 0),
            reps = intent.getIntExtra(EXTRA_FLASHCARD_REPS, 0),
            lapses = intent.getIntExtra(EXTRA_FLASHCARD_LAPSES, 0),
            state = intent.getIntExtra(EXTRA_FLASHCARD_STATE, 0),
            lastReviewedAt = intent.getLongExtra(EXTRA_FLASHCARD_LAST_REVIEWED_AT, 0),
            dueAt = intent.getLongExtra(EXTRA_FLASHCARD_DUE_AT, 0),
            createdAt = intent.getLongExtra(EXTRA_FLASHCARD_CREATED_AT, System.currentTimeMillis()),
            updatedAt = intent.getLongExtra(EXTRA_FLASHCARD_UPDATED_AT, System.currentTimeMillis())
        )
    }
    
    private fun showOverlay(flashcard: FlashcardEntity) {
        val success = overlayManager.showOverlay(
            lifecycleOwner = this,
            viewModelStoreOwner = this,
            savedStateRegistryOwner = this
        ) {
            overlayComponents.OverlayContent(
                flashcard = flashcard,
                onPositionChange = { deltaX, deltaY ->
                    overlayManager.updateWindowPositionRelative(deltaX, deltaY)
                },
                onSizeChange = { deltaWidth, deltaHeight ->
                    overlayManager.updateWindowSizeRelative(deltaWidth, deltaHeight)
                },
                onRating = { rating -> handleFlashcardRating(flashcard, rating) },
                onClose = { handleFlashcardClose(flashcard) },
                onManageCards = { handleManageCardsNavigation() },
                onSnooze = { handleSnooze() }
            )
        }
        
        if (success) {
            // STREAK UPDATE: Track flashcard view activity for streak calculation
            // This is called when flashcard becomes visible to user (both regular and demo)
            try {
                val updatedStreak = simpleStreakUseCase.recordFlashcardActivity()
                Log.d(TAG, "Streak updated: current=${updatedStreak.currentStreak}, highest=${updatedStreak.highestStreak}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update streak: ${e.message}") // Non-critical, don't fail overlay
            }
        } else {
            Log.e(TAG, "Failed to show overlay")
            stopSelf()
        }
    }
    

    
    private fun handleFlashcardRating(flashcard: FlashcardEntity, rating: FlashcardRating) {
        Log.d(TAG, "Handling flashcard rating: $rating")
        
        // Check if this is a demo flashcard (ID = -1)
        if (flashcard.id == -1L) {
            Log.d(TAG, "Demo flashcard completed, not updating SRS data")
            handleDemoCompletion()
        } else {
            // Regular flashcard - update SRS data
            serviceScope.launch(Dispatchers.IO) {
                try {
                    // Fetch the latest flashcard data from database to avoid stale data
                    val latestFlashcard = flashcardRepository.getFlashcardById(flashcard.id)
                    if (latestFlashcard != null) {
                        srsUseCase.updateFlashcardRating(latestFlashcard, rating)
                    } else {
                        Log.w(TAG, "Could not find flashcard with id=${flashcard.id}, using original")
                        srsUseCase.updateFlashcardRating(flashcard, rating)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating flashcard rating", e)
                }
            }
            
            // Close overlay and resume timer for regular flashcards
            closeOverlay()
            resumeTimerAfterInteraction()
        }
    }
    
    private fun handleFlashcardClose(flashcard: FlashcardEntity) {
        Log.d(TAG, "Handling flashcard close (skip)")
        
        // Check if this is a demo flashcard
        if (flashcard.id == -1L) {
            Log.d(TAG, "Demo flashcard closed, not resuming timer")
            handleDemoCompletion()
        } else {
            // Regular flashcard - resume timer
            closeOverlay()
            resumeTimerAfterInteraction()
        }
    }
    
    /**
     * Handles manage button click from empty state overlay.
     * Stops the learning service and opens the app on home screen.
     */
    private fun handleManageCardsNavigation() {
        Log.d(TAG, "Stopping learning service and opening home screen")
        
        // Stop the learning service first
        learningServiceManager.stopLearningService()
        
        // Close overlay
        closeOverlay()
        
        // Launch the app using package manager's launch intent
        try {
            val packageManager = packageManager
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(launchIntent)
                Log.d(TAG, "Successfully launched app")
            } else {
                Log.w(TAG, "Could not get launch intent for package")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching app", e)
        }
    }
    
    /**
     * Handles completion of demo flashcard.
     * Follows SRP by separating demo completion logic.
     * CRITICAL FIX: Use LearningServiceManager to properly start learning with all state updates.
     */
    private fun handleDemoCompletion() {
        Log.d(TAG, "Demo completed, starting real learning session")
        
        // Close demo overlay
        closeOverlay()
        
        // Mark demo as no longer running and as completed
        settingsManager.setDemoRunning(false)
        settingsManager.setFirstDemoShown()
        
        // Start the real learning session using LearningServiceManager
        // This ensures proper state management (isLearningActive, UI state, etc.)
        serviceScope.launch {
            try {
                learningServiceManager.startLearningService(settingsManager.getIntervalMinutes())
                Log.d(TAG, "Learning service started after demo completion")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting real learning session after demo", e)
            }
        }
    }
    
    /**
     * Handles snooze button tap: stops the timer, schedules an alarm to resume,
     * and closes the overlay. Order matters — stopLearningService() clears
     * pausedUntil, so we re-set it afterwards.
     */
    private fun handleSnooze() {
        Log.d(TAG, "Handling snooze")
        val snoozeDurationMs = settingsManager.getSnoozeDurationMinutes() * 60 * 1000L

        // 1. Stop the learning service (this clears pausedUntil)
        learningServiceManager.stopLearningService()
        // 2. Re-set pausedUntil after stop cleared it
        settingsManager.setPausedUntil(System.currentTimeMillis() + snoozeDurationMs)
        // 3. Schedule the alarm to resume
        scheduleSnoozeResume(snoozeDurationMs)
        // 4. Close the overlay
        closeOverlay()
    }

    /**
     * Schedules an exact alarm that fires [SnoozeBroadcastReceiver] after
     * [durationMs] milliseconds. Uses setExactAndAllowWhileIdle on API 23+.
     */
    private fun scheduleSnoozeResume(durationMs: Long) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(this, SnoozeBroadcastReceiver::class.java).apply {
            action = SnoozeBroadcastReceiver.ACTION_RESUME_AFTER_SNOOZE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            SnoozeBroadcastReceiver.ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAtMillis = System.currentTimeMillis() + durationMs

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
        Log.d(TAG, "Snooze alarm scheduled for ${durationMs / 1000}s from now")
    }

    private fun closeOverlay() {
        Log.d(TAG, "Closing overlay")
        
        // Transition lifecycle to prevent new animations
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        
        // Use extracted OverlayManager for cleanup following SOLID principles
        overlayManager.closeOverlay(serviceScope) {
            // Stop the service after overlay cleanup is complete
            stopSelf()
        }
    }
    
    private fun resumeTimerAfterInteraction() {
        try {
            // CRITICAL FIX: Check if learning is still active before resuming timer
            if (!settingsManager.getIsLearningActive()) {
                Log.d(TAG, "Learning is stopped, not resuming timer after interaction")
                return
            }
            
            Log.d(TAG, "Resuming timer after interaction")
            // Start a new timer cycle after user interaction
            val intent = Intent(this, TimerForegroundService::class.java).apply {
                putExtra("interval_minutes", settingsManager.getIntervalMinutes())
            }
            startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming timer", e)
        }
    }
    
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::overlayManager.isInitialized && overlayManager.isOverlayActive()) {
            overlayManager.relayoutForCurrentConfig()
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "OverlayService onDestroy")
        try {
            // Transition lifecycle to destroyed state
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            
            // Cancel all coroutines
            serviceScope.cancel()
            
            // Clean up demo state if this was a demo flashcard
            flashcard?.let { fc ->
                if (fc.id == -1L) {
                    settingsManager.setDemoRunning(false)
                    Log.d(TAG, "Demo state cleared during service destruction")
                }
            }
            
            // Release ViewModelStore reference to prevent memory leaks
            viewModelStoreManager.releaseOverlayViewModelStore()
            
            // Use extracted OverlayManager for emergency cleanup
            overlayManager.forceCleanup()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        } finally {
            super.onDestroy()
        }
    }
}
