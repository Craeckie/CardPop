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

package com.cardpop.app.presentation.screen

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.cardpop.app.data.repository.SettingsRepository
import com.cardpop.app.data.model.AppTheme
import com.cardpop.app.presentation.component.WelcomeScreen
import com.cardpop.app.presentation.theme.FloatingLearningTheme
import com.cardpop.app.presentation.viewmodel.WelcomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WelcomeActivity : AppCompatActivity() {

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var settingsManager: SettingsRepository

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        welcomeViewModel?.refreshPermissions()
    }

    private val safFolderLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { treeUri ->
                contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                welcomeViewModel?.handleSafFolderSelected(treeUri.toString())
            }
        } else {
            welcomeViewModel?.refreshPermissions()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        welcomeViewModel?.refreshPermissions()
    }

    private var welcomeViewModel: WelcomeViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isWelcomeCompleted = sharedPreferences.getBoolean("welcome_completed", false)
        if (isWelcomeCompleted && areAllPermissionsGranted()) {
            navigateToMainApp()
            return
        }

        setContent {
            val currentTheme by settingsManager.appTheme.collectAsState()

            val isDarkTheme = when (currentTheme) {
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
                AppTheme.BLACK -> true
                AppTheme.SYSTEM -> isSystemInDarkTheme()
            }

            LaunchedEffect(isDarkTheme) {
                updateSystemBars(isDarkTheme)
            }

            FloatingLearningTheme(appTheme = currentTheme) {
                val viewModel: WelcomeViewModel = hiltViewModel()
                welcomeViewModel = viewModel

                WelcomeScreen(
                    viewModel = viewModel,
                    onRequestOverlayPermission = { requestOverlayPermission() },
                    onRequestBackupFolder = { requestBackupFolder() },
                    onRequestNotificationPermission = { requestNotificationPermission() },
                    onRequestUsageAccess = { welcomeViewModel?.requestUsageAccess() },
                    onWelcomeCompleted = { completeWelcomeFlow() },
                    onLanguageChanged = { selectedLanguage ->
                        settingsManager.setAppLocale(selectedLanguage)
                        recreate()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        welcomeViewModel?.refreshPermissions()
    }

    private fun areAllPermissionsGranted(): Boolean {
        val isWelcomeCompleted = sharedPreferences.getBoolean("welcome_completed", false)
        if (!isWelcomeCompleted) return false

        val permissionHelper = com.cardpop.app.util.PermissionHelper(this)
        val batteryOptSkipped = settingsManager.isBatteryOptimizationSkipped()
        val batteryOptEverDisabled = settingsManager.hasBatteryOptimizationEverBeenDisabled()

        val batteryOptRequirementMet = permissionHelper.isBatteryOptimizationDisabled() ||
                batteryOptSkipped ||
                batteryOptEverDisabled

        return permissionHelper.hasOverlayPermission() && batteryOptRequirementMet
    }

    private fun requestOverlayPermission() {
        val intent = android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION.let {
            Intent(it, android.net.Uri.parse("package:$packageName"))
        }
        overlayPermissionLauncher.launch(intent)
    }

    private fun requestBackupFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        safFolderLauncher.launch(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun completeWelcomeFlow() {
        sharedPreferences.edit()
            .putBoolean("welcome_completed", true)
            .apply()
        navigateToMainApp()
    }

    private fun navigateToMainApp() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun updateSystemBars(isDarkTheme: Boolean) {
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
                detectDarkMode = { isDarkTheme }
            ),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
                detectDarkMode = { isDarkTheme }
            )
        )
    }
}
