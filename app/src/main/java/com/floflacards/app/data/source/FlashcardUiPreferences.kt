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
import android.util.DisplayMetrics
import android.view.WindowManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages persistent UI preferences for the flashcard overlay: position, size,
 * and opacity. Values are stored as percentages of screen dimensions so they
 * remain correct across orientation changes.
 */
@Singleton
class FlashcardUiPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "flashcard_ui_preferences"

        private const val KEY_POSITION_X_PERCENT = "position_x_percent"
        private const val KEY_POSITION_Y_PERCENT = "position_y_percent"
        private const val KEY_WIDTH_PERCENT = "width_percent"
        private const val KEY_HEIGHT_PERCENT = "height_percent"
        private const val KEY_OPACITY = "flashcard_opacity"

        private const val DEFAULT_POSITION_X_PERCENT = 0.05f
        private const val DEFAULT_POSITION_Y_PERCENT = 0.2f
        private const val DEFAULT_WIDTH_PERCENT = 0.9f
        private const val DEFAULT_HEIGHT_PERCENT = 0.4f
        private const val DEFAULT_OPACITY = 1.0f

        // Minimum dp and maximum percent together ensure the card is usable on
        // small screens while never dominating a large one.
        private const val MIN_WIDTH_DP = 250f
        private const val MIN_HEIGHT_DP = 200f
        private const val MAX_WIDTH_PERCENT = 0.95f
        private const val MAX_HEIGHT_PERCENT = 0.8f

        // 10% floor keeps the card visible even at "max transparency".
        private const val MIN_OPACITY = 0.1f
        private const val MAX_OPACITY = 1.0f
    }

    data class FlashcardUiState(
        val positionX: Int,
        val positionY: Int,
        val width: Int,
        val height: Int,
        val opacity: Float = DEFAULT_OPACITY
    ) {
        fun getAlpha(): Float = opacity.coerceIn(MIN_OPACITY, MAX_OPACITY)
    }

    private fun getScreenDimensions(): Pair<Int, Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            Pair(displayMetrics.widthPixels, displayMetrics.heightPixels)
        }
    }

    private fun percentToPixels(percent: Float, dimension: Int): Int =
        (percent * dimension).toInt()

    private fun pixelsToPercent(pixels: Int, dimension: Int): Float =
        if (dimension > 0) pixels.toFloat() / dimension else 0f

    private fun dpToPixels(dp: Float): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }

    fun getFlashcardUiState(): FlashcardUiState {
        val (screenWidth, screenHeight) = getScreenDimensions()

        val xPercent = prefs.getFloat(KEY_POSITION_X_PERCENT, DEFAULT_POSITION_X_PERCENT)
        val yPercent = prefs.getFloat(KEY_POSITION_Y_PERCENT, DEFAULT_POSITION_Y_PERCENT)
        val widthPercent = prefs.getFloat(KEY_WIDTH_PERCENT, DEFAULT_WIDTH_PERCENT)
        val heightPercent = prefs.getFloat(KEY_HEIGHT_PERCENT, DEFAULT_HEIGHT_PERCENT)

        val positionX = percentToPixels(xPercent, screenWidth)
        val positionY = percentToPixels(yPercent, screenHeight)
        val width = percentToPixels(widthPercent, screenWidth)
        val height = percentToPixels(heightPercent, screenHeight)

        val constrainedX = positionX.coerceIn(0, (screenWidth - width).coerceAtLeast(0))
        val constrainedY = positionY.coerceIn(0, (screenHeight - height).coerceAtLeast(0))

        val opacity = prefs.getFloat(KEY_OPACITY, DEFAULT_OPACITY).coerceIn(MIN_OPACITY, MAX_OPACITY)

        return FlashcardUiState(
            positionX = constrainedX,
            positionY = constrainedY,
            width = width,
            height = height,
            opacity = opacity
        )
    }

    fun savePosition(x: Int, y: Int) {
        val (screenWidth, screenHeight) = getScreenDimensions()
        val xPercent = pixelsToPercent(x, screenWidth)
        val yPercent = pixelsToPercent(y, screenHeight)

        prefs.edit()
            .putFloat(KEY_POSITION_X_PERCENT, xPercent)
            .putFloat(KEY_POSITION_Y_PERCENT, yPercent)
            .apply()
    }

    fun saveSize(width: Int, height: Int) {
        val (screenWidth, screenHeight) = getScreenDimensions()

        val minWidthPx = dpToPixels(MIN_WIDTH_DP)
        val minHeightPx = dpToPixels(MIN_HEIGHT_DP)
        val maxWidthPx = percentToPixels(MAX_WIDTH_PERCENT, screenWidth)
        val maxHeightPx = percentToPixels(MAX_HEIGHT_PERCENT, screenHeight)

        val constrainedWidth = width.coerceIn(minWidthPx, maxWidthPx)
        val constrainedHeight = height.coerceIn(minHeightPx, maxHeightPx)

        val widthPercent = pixelsToPercent(constrainedWidth, screenWidth)
        val heightPercent = pixelsToPercent(constrainedHeight, screenHeight)

        prefs.edit()
            .putFloat(KEY_WIDTH_PERCENT, widthPercent)
            .putFloat(KEY_HEIGHT_PERCENT, heightPercent)
            .apply()
    }

    fun getMinSize(): Pair<Int, Int> = Pair(
        dpToPixels(MIN_WIDTH_DP),
        dpToPixels(MIN_HEIGHT_DP)
    )

    fun getMaxSize(): Pair<Int, Int> {
        val (screenWidth, screenHeight) = getScreenDimensions()
        return Pair(
            percentToPixels(MAX_WIDTH_PERCENT, screenWidth),
            percentToPixels(MAX_HEIGHT_PERCENT, screenHeight)
        )
    }

    fun isWithinBounds(x: Int, y: Int, width: Int, height: Int): Boolean {
        val (screenWidth, screenHeight) = getScreenDimensions()
        return x >= 0 && y >= 0 &&
               (x + width) <= screenWidth &&
               (y + height) <= screenHeight
    }

    fun constrainToBounds(x: Int, y: Int, width: Int, height: Int): FlashcardUiState {
        val (screenWidth, screenHeight) = getScreenDimensions()
        val (minWidth, minHeight) = getMinSize()
        val (maxWidth, maxHeight) = getMaxSize()

        val constrainedWidth = width.coerceIn(minWidth, maxWidth.coerceAtMost(screenWidth))
        val constrainedHeight = height.coerceIn(minHeight, maxHeight.coerceAtMost(screenHeight))

        val constrainedX = x.coerceIn(0, (screenWidth - constrainedWidth).coerceAtLeast(0))
        val constrainedY = y.coerceIn(0, (screenHeight - constrainedHeight).coerceAtLeast(0))

        val currentOpacity = prefs.getFloat(KEY_OPACITY, DEFAULT_OPACITY)
            .coerceIn(MIN_OPACITY, MAX_OPACITY)

        return FlashcardUiState(
            positionX = constrainedX,
            positionY = constrainedY,
            width = constrainedWidth,
            height = constrainedHeight,
            opacity = currentOpacity
        )
    }

    fun saveOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(MIN_OPACITY, MAX_OPACITY)
        prefs.edit()
            .putFloat(KEY_OPACITY, clamped)
            .apply()
    }

    fun getCurrentOpacity(): Float =
        prefs.getFloat(KEY_OPACITY, DEFAULT_OPACITY).coerceIn(MIN_OPACITY, MAX_OPACITY)

    /**
     * Snapshot of the persisted percentages. Returned as-is (no screen
     * dimension conversion) so it can round-trip through a backup file and
     * restore correctly on a device with different dimensions.
     */
    data class PercentSnapshot(
        val positionXPercent: Float,
        val positionYPercent: Float,
        val widthPercent: Float,
        val heightPercent: Float,
        val opacity: Float
    )

    fun getPercentSnapshot(): PercentSnapshot = PercentSnapshot(
        positionXPercent = prefs.getFloat(KEY_POSITION_X_PERCENT, DEFAULT_POSITION_X_PERCENT),
        positionYPercent = prefs.getFloat(KEY_POSITION_Y_PERCENT, DEFAULT_POSITION_Y_PERCENT),
        widthPercent = prefs.getFloat(KEY_WIDTH_PERCENT, DEFAULT_WIDTH_PERCENT),
        heightPercent = prefs.getFloat(KEY_HEIGHT_PERCENT, DEFAULT_HEIGHT_PERCENT),
        opacity = prefs.getFloat(KEY_OPACITY, DEFAULT_OPACITY).coerceIn(MIN_OPACITY, MAX_OPACITY)
    )

    fun savePercentSnapshot(snapshot: PercentSnapshot) {
        prefs.edit()
            .putFloat(KEY_POSITION_X_PERCENT, snapshot.positionXPercent.coerceIn(0f, 1f))
            .putFloat(KEY_POSITION_Y_PERCENT, snapshot.positionYPercent.coerceIn(0f, 1f))
            .putFloat(KEY_WIDTH_PERCENT, snapshot.widthPercent.coerceIn(0f, MAX_WIDTH_PERCENT))
            .putFloat(KEY_HEIGHT_PERCENT, snapshot.heightPercent.coerceIn(0f, MAX_HEIGHT_PERCENT))
            .putFloat(KEY_OPACITY, snapshot.opacity.coerceIn(MIN_OPACITY, MAX_OPACITY))
            .apply()
    }

    fun resetToDefaults() {
        prefs.edit()
            .putFloat(KEY_POSITION_X_PERCENT, DEFAULT_POSITION_X_PERCENT)
            .putFloat(KEY_POSITION_Y_PERCENT, DEFAULT_POSITION_Y_PERCENT)
            .putFloat(KEY_WIDTH_PERCENT, DEFAULT_WIDTH_PERCENT)
            .putFloat(KEY_HEIGHT_PERCENT, DEFAULT_HEIGHT_PERCENT)
            .putFloat(KEY_OPACITY, DEFAULT_OPACITY)
            .apply()
    }
}
