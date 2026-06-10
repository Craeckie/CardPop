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

package com.cardpop.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardpop.app.data.entity.FlashcardEntity
import com.cardpop.app.data.repository.FlashcardRepository
import com.cardpop.app.domain.fsrs.Fsrs
import com.cardpop.app.domain.fsrs.FsrsCardState
import com.cardpop.app.data.source.ReviewHistoryPreferences
import com.cardpop.app.data.source.ReviewHistoryEntry
import com.cardpop.app.data.repository.SettingsRepository
import com.cardpop.app.domain.model.StudyHealth
import com.cardpop.app.domain.usecase.RetentionData
import com.cardpop.app.domain.usecase.StatisticsUseCase
import com.cardpop.app.domain.usecase.SimpleStreakUseCase
import com.cardpop.app.domain.usecase.StudyHealthUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FlashcardStats(
    val id: Long,
    val question: String,
    val answer: String,
    val correctCount: Int,
    val incorrectCount: Int,
    val hardCount: Int,
    val easyCount: Int,
    val difficultyScore: Float,
    val successRate: Float,
    val lastSeenTimestamp: Long,
    val reviewCount: Int,
    val lapses: Int,
    val isEnabled: Boolean,
    val isMastered: Boolean,
    val state: Int,
    val stability: Double,
    /** Current predicted recall probability 0..100, null if card was never reviewed. */
    val retrievability: Float?
) {
    val lastSeenText: String = when {
        lastSeenTimestamp == 0L -> "⏰ Not scheduled yet"
        else -> {
            val date = java.util.Date(lastSeenTimestamp)
            val formatter = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, java.util.Locale.getDefault())
            "⏰ ${formatter.format(date)}"
        }
    }

    // FSRS difficulty is on a 1..10 scale where LOW = easy and HIGH = hard,
    // the inverse of the old SM-2 easiness factor. Keep the label semantics
    // ("Easy"/"Medium"/"Hard") so the UI doesn't need to change.
    val difficultyLevel: String = when {
        difficultyScore <= 4.0f -> "Easy"
        difficultyScore <= 7.0f -> "Medium"
        else -> "Hard"
    }

    val totalAttempts: Int = correctCount + incorrectCount + hardCount + easyCount
}

data class CategoryStats(
    val categoryId: Long,
    val categoryName: String,
    val totalCards: Int,
    val studiedCards: Int,
    val masteredCards: Int,
    val averageSuccessRate: Float,
    val flashcards: List<FlashcardStats>,
    val isExpanded: Boolean = false
) {
    val studiedPercentage: Int = if (totalCards > 0) (studiedCards * 100) / totalCards else 0
    val masteredPercentage: Int = if (totalCards > 0) (masteredCards * 100) / totalCards else 0
    val masteredRate: Float = if (totalCards > 0) masteredCards.toFloat() / totalCards.toFloat() else 0f
}

data class EnhancedOverallStats(
    val streakDays: Int,
    val highestStreak: Int,
    val masteredFlashcards: Int,
    val totalFlashcards: Int,
    val dueNowCount: Int,
    val newCount: Int,
    val youngCount: Int,
    val matureCount: Int
)

data class RatingDistribution(
    val wrong: Int,
    val hard: Int,
    val good: Int,
    val easy: Int
) {
    val total: Int get() = wrong + hard + good + easy
}

data class StabilityDistribution(
    val b0to3: Int,
    val b3to7: Int,
    val b7to14: Int,
    val b14to21: Int,
    val b21to30: Int,
    val b30plus: Int
) {
    val total: Int get() = b0to3 + b3to7 + b7to14 + b14to21 + b21to30 + b30plus
}

data class ModernStatisticsUiState(
    val isLoading: Boolean = false,
    val overallStats: EnhancedOverallStats? = null,
    val categoryStats: List<CategoryStats> = emptyList(),
    val reviewHistory: List<ReviewHistoryEntry> = emptyList(),
    val ratingDistribution: RatingDistribution? = null,
    val stabilityDistribution: StabilityDistribution? = null,
    val retentionData: RetentionData? = null,
    val studyHealth: StudyHealth? = null,
    val searchQuery: String = "",
    /** When true, the category list is filtered to cards with lapses ≥ LEECH_LAPSES. */
    val leechFilter: Boolean = false
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val statisticsUseCase: StatisticsUseCase,
    private val repository: FlashcardRepository,
    private val simpleStreakUseCase: SimpleStreakUseCase,
    private val reviewHistory: ReviewHistoryPreferences,
    private val settingsRepository: SettingsRepository,
    private val studyHealthUseCase: StudyHealthUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ModernStatisticsUiState())
    val uiState: StateFlow<ModernStatisticsUiState> = _uiState.asStateFlow()
    
    fun loadStatistics() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                val allFlashcards = repository.getAllFlashcardsForStatistics()
                val allCategories = repository.getAllCategories()
                
                // Mastered = card has reached high stability (≥21d ≈ 3 weeks) after at
                // least 3 successful reviews. Stability is FSRS's interval-prediction
                // memory strength, so this is roughly "the algorithm believes you'll
                // still recall it 3 weeks out."
                val masteredFlashcards = allFlashcards.count { it.stability >= 21.0 && it.reps >= 3 }
                val totalFlashcards = allFlashcards.size
                
                // Use new simple streak system instead of complex historical calculation
                val currentStreakData = simpleStreakUseCase.getCurrentStreakData()
                val streakDays = currentStreakData.currentStreak
                val highestStreak = currentStreakData.highestStreak
                
                // Maturity breakdown — only enabled cards in enabled categories.
                // New = never reviewed (state 0); Mature = stability ≥ 21d and
                // reps ≥ 3; Young = everything else (Learning/Relearning/Review-young).
                val stateCounts = repository.getCardCountsByState().associate { it.state to it.count }
                val dueNowCount = repository.getDueNowCount()
                val newCount = stateCounts[0] ?: 0
                val totalEnabled = stateCounts.values.sum()
                val matureCount = repository.getEnabledMatureCount()
                val youngCount = (totalEnabled - newCount - matureCount).coerceAtLeast(0)

                val enhancedOverallStats = EnhancedOverallStats(
                    streakDays = streakDays,
                    highestStreak = highestStreak,
                    masteredFlashcards = masteredFlashcards,
                    totalFlashcards = totalFlashcards,
                    dueNowCount = dueNowCount,
                    newCount = newCount,
                    youngCount = youngCount,
                    matureCount = matureCount
                )
                
                // Last 30 days of activity for the over-time chart. Reading
                // SharedPreferences is fast enough to do on the main thread,
                // but we're already off-main here so it's free.
                val historySeries = reviewHistory.getHistory(days = HISTORY_DAYS)

                val fsrs = Fsrs(
                    requestRetention = settingsRepository.getTargetRetention(),
                    params = settingsRepository.getFsrsParameters()
                )
                val now = System.currentTimeMillis()

                allCategories.collect { categories ->
                    val categoryStatsList = categories
                        .filter { it.isEnabled }
                        .sortedBy { it.createdAt } // Sort categories by creation date
                        .map { category ->
                            val categoryFlashcards = allFlashcards.filter { it.categoryId == category.id && it.isEnabled }
                            val studiedCards = categoryFlashcards.count { it.reps > 0 }
                            val masteredCards = categoryFlashcards.count { it.stability >= 21.0 && it.reps >= 3 }

                            val flashcardStats = categoryFlashcards.map { flashcard ->
                                val isMastered = flashcard.stability >= 21.0 && flashcard.reps >= 3
                                val retrievability = if (
                                    flashcard.reps > 0 &&
                                    flashcard.stability > 0.0 &&
                                    flashcard.lastReviewedAt > 0L
                                ) {
                                    val elapsedDays = (now - flashcard.lastReviewedAt) / 86_400_000.0
                                    (fsrs.retrievability(elapsedDays, flashcard.stability) * 100.0).toFloat()
                                } else null

                                FlashcardStats(
                                    id = flashcard.id,
                                    question = flashcard.question,
                                    answer = flashcard.answer,
                                    correctCount = flashcard.correctCount,
                                    incorrectCount = flashcard.incorrectCount,
                                    hardCount = flashcard.hardCount,
                                    easyCount = flashcard.easyCount,
                                    difficultyScore = flashcard.difficulty.toFloat(),
                                    successRate = weightedSuccessRate(flashcard) * 100f,
                                    lastSeenTimestamp = flashcard.dueAt,
                                    reviewCount = flashcard.reps,
                                    lapses = flashcard.lapses,
                                    isEnabled = flashcard.isEnabled,
                                    isMastered = isMastered,
                                    state = flashcard.state,
                                    stability = flashcard.stability,
                                    retrievability = retrievability
                                )
                            }.sortedWith(
                                compareByDescending<FlashcardStats> { it.difficultyScore } // Hardest first
                                    .thenByDescending { it.reviewCount } // Most reviewed first
                                    .thenBy { if (it.lastSeenTimestamp == 0L) Long.MAX_VALUE else -it.lastSeenTimestamp } // Never seen at bottom
                            )

                        val averageSuccessRate = if (categoryFlashcards.isNotEmpty()) {
                            categoryFlashcards.map { weightedSuccessRate(it) }.average().toFloat()
                        } else 0f
                        
                        CategoryStats(
                            categoryId = category.id,
                            categoryName = category.name,
                            totalCards = categoryFlashcards.size,
                            studiedCards = studiedCards,
                            masteredCards = masteredCards,
                            averageSuccessRate = averageSuccessRate,
                            flashcards = flashcardStats
                        )
                    }
                    
                    val dist = RatingDistribution(
                        wrong = allFlashcards.sumOf { it.incorrectCount },
                        hard  = allFlashcards.sumOf { it.hardCount },
                        good  = allFlashcards.sumOf { it.correctCount },
                        easy  = allFlashcards.sumOf { it.easyCount }
                    )

                    val reviewCards = allFlashcards.filter { it.state == FsrsCardState.Review.value }
                    val stabilityDist = StabilityDistribution(
                        b0to3   = reviewCards.count { it.stability < 3.0 },
                        b3to7   = reviewCards.count { it.stability >= 3.0  && it.stability < 7.0 },
                        b7to14  = reviewCards.count { it.stability >= 7.0  && it.stability < 14.0 },
                        b14to21 = reviewCards.count { it.stability >= 14.0 && it.stability < 21.0 },
                        b21to30 = reviewCards.count { it.stability >= 21.0 && it.stability < 30.0 },
                        b30plus = reviewCards.count { it.stability >= 30.0 }
                    )

                    val remembered = allFlashcards.sumOf { it.correctCount + it.easyCount + it.hardCount }
                    val forgotten  = allFlashcards.sumOf { it.incorrectCount }
                    val totalRev   = remembered + forgotten
                    val retentionData = RetentionData(
                        rate = if (totalRev > 0) remembered.toFloat() / totalRev else 0f,
                        totalReviews = totalRev
                    ).takeIf { totalRev >= 10 }

                    // recallRate = (good+easy+hard)/total — matches FSRS "not-Again" semantics
                    // so it can be compared directly against targetRetention. Note: this differs
                    // from weightedSuccessRate, which counts Hard as 0.5.
                    val recallRate = if (totalRev > 0) remembered.toFloat() / totalRev else 0f
                    val now7DaysAgo = System.currentTimeMillis() - 7L * 86_400_000L
                    val studyHealth = studyHealthUseCase.evaluate(
                        StudyHealthUseCase.Input(
                            activeTotal              = totalEnabled,
                            newCount                 = newCount,
                            youngCount               = youngCount,
                            matureCount              = matureCount,
                            dueNowCount              = dueNowCount,
                            totalReviews             = totalRev,
                            recallRate               = recallRate,
                            targetRetention          = settingsRepository.getTargetRetention(),
                            leechCount               = allFlashcards.count { it.lapses >= StudyHealthUseCase.LEECH_LAPSES },
                            reviewCardCount          = reviewCards.size,
                            hardDifficultyCount      = reviewCards.count { it.difficulty > StudyHealthUseCase.HARD_DIFF_THRESHOLD },
                            lowStabilityReviewCount  = stabilityDist.b0to3 + stabilityDist.b3to7,
                            cardsAddedLast7Days      = allFlashcards.count { it.createdAt >= now7DaysAgo },
                            zeroReviewDaysLast7      = historySeries.takeLast(7).count { it.reviews == 0 }
                        )
                    )

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        overallStats = enhancedOverallStats,
                        categoryStats = categoryStatsList,
                        reviewHistory = historySeries,
                        ratingDistribution = dist.takeIf { it.total > 0 },
                        stabilityDistribution = stabilityDist.takeIf { it.total > 0 },
                        retentionData = retentionData,
                        studyHealth = studyHealth
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                println("Failed to load statistics: ${e.message}")
            }
        }
    }
    
    fun toggleCategoryExpansion(categoryId: Long) {
        val currentStats = _uiState.value.categoryStats
        val updatedStats = currentStats.map { category ->
            if (category.categoryId == categoryId) {
                category.copy(isExpanded = !category.isExpanded)
            } else {
                category
            }
        }
        _uiState.value = _uiState.value.copy(categoryStats = updatedStats)
    }
    
    // Search functionality
    fun updateSearchQuery(query: String) {
        // Activating text search clears the leech filter (they're mutually exclusive)
        _uiState.value = _uiState.value.copy(searchQuery = query.trim(), leechFilter = false)
    }

    /** Activates or clears the leech filter. Clears text search when activating. */
    fun setLeechFilter(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            leechFilter = enabled,
            searchQuery = if (enabled) "" else _uiState.value.searchQuery
        )
    }

    /**
     * Returns filtered category stats based on the active filter.
     *
     * Leech filter: shows only flashcards with lapses ≥ [StudyHealthUseCase.LEECH_LAPSES],
     * auto-expanding categories that have matching cards.
     *
     * Text search: searches both category names and flashcard content (questions/answers).
     * Categories are included if:
     * - The category name matches the query, OR
     * - Any flashcard within the category matches the query
     *
     * The two modes are mutually exclusive; activating one clears the other.
     */
    fun getFilteredCategoryStats(): List<CategoryStats> {
        val state = _uiState.value

        // Leech filter overrides text search
        if (state.leechFilter) {
            return state.categoryStats.mapNotNull { category ->
                val leeches = category.flashcards.filter { it.lapses >= StudyHealthUseCase.LEECH_LAPSES }
                if (leeches.isEmpty()) null
                else category.copy(flashcards = leeches, isExpanded = true)
            }
        }

        val query = state.searchQuery
        if (query.isBlank()) {
            return state.categoryStats
        }

        return state.categoryStats.mapNotNull { category ->
            val categoryNameMatches = category.categoryName.contains(query, ignoreCase = true)

            // Filter flashcards within the category
            val matchingFlashcards = category.flashcards.filter { flashcard ->
                flashcard.question.contains(query, ignoreCase = true) ||
                flashcard.answer.contains(query, ignoreCase = true)
            }

            when {
                // Category name matches - include with all flashcards
                categoryNameMatches -> category
                // Some flashcards match - include category with only matching flashcards
                matchingFlashcards.isNotEmpty() -> category.copy(
                    flashcards = matchingFlashcards,
                    isExpanded = true // Auto-expand to show matched flashcards
                )
                // No match - exclude category
                else -> null
            }
        }
    }
    
    // Reset statistics methods
    fun resetFlashcardStatistics(flashcardId: Long) {
        viewModelScope.launch {
            try {
                repository.resetFlashcardStatistics(flashcardId)
                // Reload statistics to reflect changes
                loadStatistics()
            } catch (e: Exception) {
                println("Failed to reset flashcard statistics: ${e.message}")
            }
        }
    }
    
    fun resetCategoryStatistics(categoryId: Long) {
        viewModelScope.launch {
            try {
                repository.resetCategoryStatistics(categoryId)
                // Reload statistics to reflect changes
                loadStatistics()
            } catch (e: Exception) {
                println("Failed to reset category statistics: ${e.message}")
            }
        }
    }
    
    fun resetAllStatistics() {
        viewModelScope.launch {
            try {
                repository.resetAllStatistics()
                // Also reset streak data when resetting all statistics
                simpleStreakUseCase.resetStreak()
                // Wipe the over-time chart history too — keeping it would show a
                // mastered curve that doesn't match the freshly-zeroed cards.
                reviewHistory.reset()
                // Reload statistics to reflect changes
                loadStatistics()
            } catch (e: Exception) {
                println("Failed to reset all statistics: ${e.message}")
            }
        }
    }

    private companion object {
        const val HISTORY_DAYS = 30
    }
}

/**
 * Weighted recall rate from per-rating counters: Good and Easy fully count as
 * remembered, Hard counts as half (the user produced the answer but slowly),
 * Wrong counts as zero. Returns a 0..1 fraction; callers multiply by 100 for a
 * percentage.
 */
internal fun weightedSuccessRate(card: FlashcardEntity): Float {
    val total = card.correctCount + card.incorrectCount + card.hardCount + card.easyCount
    if (total == 0) return 0f
    val weighted = card.correctCount + card.easyCount + card.hardCount * 0.5f
    return weighted / total.toFloat()
}
