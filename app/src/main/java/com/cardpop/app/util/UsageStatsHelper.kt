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

package com.cardpop.app.util

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log

/**
 * Wraps [UsageStatsManager] + the AppOps-based permission check needed by the
 * app blocklist feature. [PACKAGE_USAGE_STATS] cannot be granted via a runtime
 * dialog — the user has to toggle it in system settings, which [requestAccess]
 * deep-links into.
 */
object UsageStatsHelper {

    private const val TAG = "UsageStatsHelper"

    // Window for finding the most recent MOVE_TO_FOREGROUND event. Needs to cover
    // long-running sessions: if the user launched the blocked app an hour ago and
    // hasn't switched since, there's no event in a short window.
    private const val EVENT_LOOKBACK_MS = 6L * 60L * 60L * 1000L // 6 hours

    // Fallback window for queryUsageStats, used when no event shows up above.
    private const val STATS_LOOKBACK_MS = 24L * 60L * 60L * 1000L // 24 hours

    fun hasAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun requestAccess(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open usage access settings", e)
        }
    }

    /**
     * Returns the package name of the most recently foregrounded app, or `null`
     * if nothing could be determined. Tries [UsageStatsManager.queryEvents] first
     * (more accurate — literal "last time a window moved to the foreground")
     * and falls back to [UsageStatsManager.queryUsageStats] when no event is in
     * the window (e.g. user has been inside the same app for longer than the
     * lookback).
     */
    fun currentForegroundPackage(context: Context): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val self = context.packageName
        val now = System.currentTimeMillis()

        val fromEvents = lastForegroundEvent(usm, now, self)
        if (fromEvents != null) {
            Log.d(TAG, "Foreground from events: $fromEvents")
            return fromEvents
        }

        val fromStats = lastUsedFromStats(usm, now, self)
        if (fromStats != null) {
            Log.d(TAG, "Foreground from stats fallback: $fromStats")
            return fromStats
        }

        Log.d(TAG, "No foreground app could be determined")
        return null
    }

    private fun lastForegroundEvent(
        usm: UsageStatsManager,
        now: Long,
        selfPackage: String
    ): String? {
        return try {
            val events = usm.queryEvents(now - EVENT_LOOKBACK_MS, now)
            val event = UsageEvents.Event()
            var lastPkg: String? = null
            var lastTime = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType != UsageEvents.Event.MOVE_TO_FOREGROUND) continue
                if (event.packageName == selfPackage) continue
                if (event.timeStamp >= lastTime) {
                    lastTime = event.timeStamp
                    lastPkg = event.packageName
                }
            }
            lastPkg
        } catch (e: Exception) {
            Log.e(TAG, "queryEvents failed", e)
            null
        }
    }

    private fun lastUsedFromStats(
        usm: UsageStatsManager,
        now: Long,
        selfPackage: String
    ): String? {
        return try {
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                now - STATS_LOOKBACK_MS,
                now
            ) ?: return null
            stats
                .filter { it.packageName != selfPackage && it.lastTimeUsed > 0 }
                .maxByOrNull { it.lastTimeUsed }
                ?.packageName
        } catch (e: Exception) {
            Log.e(TAG, "queryUsageStats failed", e)
            null
        }
    }
}
