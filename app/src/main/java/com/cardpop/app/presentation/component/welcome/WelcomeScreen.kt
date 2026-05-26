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

package com.cardpop.app.presentation.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cardpop.app.R
import com.cardpop.app.data.model.Language
import com.cardpop.app.presentation.viewmodel.WelcomeViewModel

/**
 * Navigation-Compose-based wizard host. Each onboarding step is a route; back navigation
 * comes for free via popBackStack() and the system back gesture.
 */
@Composable
fun WelcomeScreen(
    viewModel: WelcomeViewModel,
    onRequestOverlayPermission: () -> Unit,
    onRequestBackupFolder: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onWelcomeCompleted: () -> Unit,
    onLanguageChanged: ((Language) -> Unit)? = null
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "intro"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Progress indicator (hidden on completed step)
        if (currentRoute != "completed") {
            WelcomeProgressIndicator(
                currentRoute = currentRoute,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Back button row — shown whenever there is a previous entry on the stack
        if (navController.previousBackStackEntry != null) {
            TextButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = "← ${stringResource(R.string.welcome_button_back)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Step content — NavHost fills the remaining space; each route scrolls independently
        NavHost(
            navController = navController,
            startDestination = "intro",
            modifier = Modifier.weight(1f)
        ) {
            composable("intro") {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    IntroductionStep(
                        onNext = { navController.navigate("privacy") },
                        onLanguageChanged = onLanguageChanged
                    )
                }
            }
            composable("privacy") {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    PrivacyOfflineStep(
                        onNext = { navController.navigate("backup-folder") }
                    )
                }
            }
            composable("backup-folder") {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    BackupFolderStep(
                        hasFolderConfigured = uiState.hasBackupFolderConfigured,
                        onRequestFolderSelection = onRequestBackupFolder,
                        onNext = {
                            if (uiState.hasBackupAvailable) navController.navigate("restore")
                            else navController.navigate("overlay")
                        }
                    )
                }
            }
            composable("restore") {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    BackupCheckStep(
                        hasBackup = uiState.hasBackupAvailable,
                        backupInfo = uiState.backupInfo,
                        onRestore = {
                            viewModel.restoreBackup()
                            navController.navigate("overlay")
                        },
                        onSkip = { navController.navigate("overlay") }
                    )
                }
            }
            composable("overlay") {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OverlayPermissionStep(
                        hasPermission = uiState.hasOverlayPermission,
                        onRequestPermission = onRequestOverlayPermission,
                        onNext = { navController.navigate("permissions") }
                    )
                }
            }
            composable("permissions") {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    PermissionsStep(
                        isBatteryOptimizationDisabled = uiState.isBatteryOptimizationDisabled,
                        hasNotificationPermission = uiState.hasNotificationPermission,
                        onRequestDisableBattery = { viewModel.requestBatteryOptimizationDisable() },
                        onRequestNotification = onRequestNotificationPermission,
                        onSkip = {
                            viewModel.skipPermissions()
                            navController.navigate("usage-access")
                        },
                        onNext = { navController.navigate("usage-access") }
                    )
                }
            }
            composable("usage-access") {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    UsageAccessStep(
                        hasAccess = uiState.hasUsageAccess,
                        onRequestAccess = { viewModel.requestUsageAccess() },
                        onNext = { navController.navigate("completed") }
                    )
                }
            }
            composable("completed") {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CompletedStep(onEnterApp = onWelcomeCompleted)
                }
            }
        }
    }
}

@Composable
private fun WelcomeProgressIndicator(
    currentRoute: String,
    modifier: Modifier = Modifier
) {
    val routeOrder = listOf(
        "intro"         to stringResource(R.string.welcome_progress_introduction),
        "privacy"       to stringResource(R.string.welcome_progress_privacy),
        "backup-folder" to stringResource(R.string.welcome_progress_backup_folder),
        "restore"       to stringResource(R.string.welcome_progress_backup_check),
        "overlay"       to stringResource(R.string.welcome_progress_overlay),
        "permissions"   to stringResource(R.string.welcome_progress_permissions),
        "usage-access"  to stringResource(R.string.welcome_progress_usage)
    )

    val currentIndex = routeOrder.indexOfFirst { it.first == currentRoute }.takeIf { it >= 0 }
        ?: (routeOrder.size - 1)
    val stepLabel = routeOrder.getOrNull(currentIndex)?.second ?: ""
    val total = routeOrder.size

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.welcome_progress_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LinearProgressIndicator(
            progress = { (currentIndex + 1) / total.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = Color(0xFF4CAF50),
            trackColor = Color(0xFFE0E0E0)
        )

        Text(
            text = stringResource(
                R.string.welcome_progress_step,
                currentIndex + 1,
                total,
                stepLabel
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
