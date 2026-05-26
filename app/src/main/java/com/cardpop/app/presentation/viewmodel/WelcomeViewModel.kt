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
import com.cardpop.app.util.PermissionHelper
import com.cardpop.app.data.backup.BackupInfo
import com.cardpop.app.data.repository.SettingsRepository
import com.cardpop.app.data.source.BackupPreferences
import com.cardpop.app.domain.usecase.backup.GetBackupInfoUseCase
import com.cardpop.app.domain.usecase.backup.RestoreBackupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val permissionHelper: PermissionHelper,
    private val settingsManager: SettingsRepository,
    private val backupPreferences: BackupPreferences,
    private val getBackupInfoUseCase: GetBackupInfoUseCase,
    private val restoreBackupUseCase: RestoreBackupUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(WelcomeUiState())
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    init {
        refreshPermissions()
    }

    fun refreshPermissions() {
        viewModelScope.launch {
            val isBatteryOptDisabled = permissionHelper.isBatteryOptimizationDisabled()

            if (isBatteryOptDisabled && !settingsManager.hasBatteryOptimizationEverBeenDisabled()) {
                settingsManager.setBatteryOptimizationEverDisabled(true)
            }

            _uiState.value = _uiState.value.copy(
                hasOverlayPermission = permissionHelper.hasOverlayPermission(),
                isBatteryOptimizationDisabled = isBatteryOptDisabled,
                hasNotificationPermission = permissionHelper.hasNotificationPermission(),
                hasUsageAccess = permissionHelper.hasUsageAccess(),
                hasBackupFolderConfigured = backupPreferences.hasSafFolderConfigured(),
                isRefreshing = false
            )
        }
    }

    fun requestBatteryOptimizationDisable() {
        permissionHelper.requestBatteryOptimizationDisable()
    }

    fun requestUsageAccess() {
        permissionHelper.requestUsageAccess()
    }

    /** Skip the combined permissions step. Marks battery opt as skipped and advances via nav. */
    fun skipPermissions() {
        settingsManager.setBatteryOptimizationSkipped(true)
    }

    /**
     * Called after SAF folder selection. Persists URI, refreshes permission state, and fetches
     * backup info so the next screen can decide whether to show the restore step.
     */
    fun handleSafFolderSelected(treeUri: String) {
        backupPreferences.setSafTreeUri(treeUri)
        viewModelScope.launch {
            val isBatteryOptDisabled = permissionHelper.isBatteryOptimizationDisabled()
            val backupInfo = try { getBackupInfoUseCase() } catch (e: Exception) {
                BackupInfo(exists = false, filePath = "")
            }
            _uiState.value = _uiState.value.copy(
                hasOverlayPermission = permissionHelper.hasOverlayPermission(),
                isBatteryOptimizationDisabled = isBatteryOptDisabled,
                hasNotificationPermission = permissionHelper.hasNotificationPermission(),
                hasUsageAccess = permissionHelper.hasUsageAccess(),
                hasBackupFolderConfigured = backupPreferences.hasSafFolderConfigured(),
                backupInfo = backupInfo,
                hasBackupAvailable = backupInfo.exists,
                isRefreshing = false
            )
        }
    }

    fun restoreBackup() {
        viewModelScope.launch {
            try {
                restoreBackupUseCase()
            } catch (e: Exception) {
                // Restore failure is surfaced via navigation (caller advances regardless)
            }
        }
    }

    fun areAllPermissionsGranted(): Boolean {
        val state = _uiState.value
        val batteryOptSkipped = settingsManager.isBatteryOptimizationSkipped()
        val batteryOptEverDisabled = settingsManager.hasBatteryOptimizationEverBeenDisabled()
        val batteryOptRequirementMet = state.isBatteryOptimizationDisabled ||
                batteryOptSkipped ||
                batteryOptEverDisabled
        return state.hasOverlayPermission && batteryOptRequirementMet
    }
}

data class WelcomeUiState(
    val hasOverlayPermission: Boolean = false,
    val isBatteryOptimizationDisabled: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val hasBackupFolderConfigured: Boolean = false,
    val hasBackupAvailable: Boolean = false,
    val backupInfo: BackupInfo = BackupInfo(exists = false, filePath = ""),
    val isRefreshing: Boolean = false
)
