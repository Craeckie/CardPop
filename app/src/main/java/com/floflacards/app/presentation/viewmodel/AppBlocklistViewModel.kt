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

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.floflacards.app.data.repository.SettingsRepository
import com.floflacards.app.util.UsageStatsHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Lightweight view of an installed app as shown in the blocklist picker.
 */
data class InstalledApp(
    val packageName: String,
    val label: String
)

@HiltViewModel
class AppBlocklistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsRepository
) : ViewModel() {

    val blocklist: StateFlow<Set<String>> = settingsManager.blocklist

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _hasUsageAccess = MutableStateFlow(UsageStatsHelper.hasAccess(context))
    val hasUsageAccess: StateFlow<Boolean> = _hasUsageAccess.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    init {
        loadApps()
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    /** Re-check usage access; called after the user returns from system settings. */
    fun refreshUsageAccess() {
        _hasUsageAccess.value = UsageStatsHelper.hasAccess(context)
    }

    fun requestUsageAccess() {
        UsageStatsHelper.requestAccess(context)
    }

    fun toggle(packageName: String, blocked: Boolean) {
        if (blocked) settingsManager.addToBlocklist(packageName)
        else settingsManager.removeFromBlocklist(packageName)
    }

    private fun loadApps() {
        viewModelScope.launch {
            _loading.value = true
            val pm = context.packageManager
            val self = context.packageName
            val result = withContext(Dispatchers.IO) {
                val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                pm.queryIntentActivities(mainIntent, 0)
                    .asSequence()
                    .map { it.activityInfo.applicationInfo }
                    .filter { it.packageName != self }
                    .distinctBy { it.packageName }
                    .map { info ->
                        InstalledApp(
                            packageName = info.packageName,
                            label = runCatching { pm.getApplicationLabel(info).toString() }
                                .getOrDefault(info.packageName)
                        )
                    }
                    .sortedBy { it.label.lowercase() }
                    .toList()
            }
            _apps.value = result
            _loading.value = false
        }
    }
}
