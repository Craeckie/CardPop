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

package com.floflacards.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.floflacards.app.data.entity.FlashcardEntity
import com.floflacards.app.data.repository.FlashcardRepository
import com.floflacards.app.domain.usecase.StatisticsUseCase
import com.floflacards.app.domain.usecase.SimpleStreakUseCase
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
    val isEnabled: Boolean,
    val isMastered: Boolean
) {
    val lastSeenText: String = when {
        lastSeenTimestamp == 0L -> "Not reviewed yet"
        else -> {
            val date = java.util.Date(lastSeenTimestamp)
            val formatter = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
            formatter.format(date)
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
    val learningCount: Int,
    val reviewCount: Int,
    val relearningCount: Int
)

data class ModernStatisticsUiState(
    val isLoading: Boolean = false,
    val overallStats: EnhancedOverallStats? = null,
    val categoryStats: List<CategoryStats> = emptyList(),
    val searchQuery: String = "" // Current search query
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val statisticsUseCase: StatisticsUseCase,
    private val repository: FlashcardRepository,
    private val simpleStreakUseCase: SimpleStreakUseCase
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
                
                // FSRS card-state breakdown — only enabled cards in enabled
                // categories, mirroring what the overlay actually picks from.
                val stateCounts = repository.getCardCountsByState().associate { it.state to it.count }
                val dueNowCount = repository.getDueNowCount()

                val enhancedOverallStats = EnhancedOverallStats(
                    streakDays = streakDays,
                    highestStreak = highestStreak,
                    masteredFlashcards = masteredFlashcards,
                    totalFlashcards = totalFlashcards,
                    dueNowCount = dueNowCount,
                    newCount = stateCounts[0] ?: 0,
                    learningCount = stateCounts[1] ?: 0,
                    reviewCount = stateCounts[2] ?: 0,
                    relearningCount = stateCounts[3] ?: 0
                )
                
                allCategories.collect { categories ->
                    val categoryStatsList = categories
                        .sortedBy { it.createdAt } // Sort categories by creation date
                        .map { category ->
                            val categoryFlashcards = allFlashcards.filter { it.categoryId == category.id }
                            val studiedCards = categoryFlashcards.count { it.reps > 0 }
                            val masteredCards = categoryFlashcards.count { it.stability >= 21.0 && it.reps >= 3 }
                            
                            val flashcardStats = categoryFlashcards.map { flashcard ->
                                val isMastered = flashcard.stability >= 21.0 && flashcard.reps >= 3

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
                                    lastSeenTimestamp = flashcard.lastReviewedAt,
                                    reviewCount = flashcard.reps,
                                    isEnabled = flashcard.isEnabled,
                                    isMastered = isMastered
                                )
                            }.sortedWith(
                                compareByDescending<FlashcardStats> { it.successRate } // Best success rate first
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
                    
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        overallStats = enhancedOverallStats,
                        categoryStats = categoryStatsList
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
        _uiState.value = _uiState.value.copy(searchQuery = query.trim())
    }
    
    /**
     * Returns filtered category stats based on search query.
     * Searches both category names and flashcard content (questions/answers).
     * Categories are included if:
     * - The category name matches the query, OR
     * - Any flashcard within the category matches the query
     */
    fun getFilteredCategoryStats(): List<CategoryStats> {
        val query = _uiState.value.searchQuery
        if (query.isBlank()) {
            return _uiState.value.categoryStats
        }
        
        return _uiState.value.categoryStats.mapNotNull { category ->
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
                // Reload statistics to reflect changes
                loadStatistics()
            } catch (e: Exception) {
                println("Failed to reset all statistics: ${e.message}")
            }
        }
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
