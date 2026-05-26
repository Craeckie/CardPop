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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cardpop.app.R
import com.cardpop.app.data.backup.BackupInfo
import com.cardpop.app.data.model.Language
import com.cardpop.app.presentation.component.welcome.WelcomeLanguageSelectionDialog

/**
 * Welcome onboarding step composables. Each corresponds to a wizard route in WelcomeNavHost.
 */

@Composable
fun IntroductionStep(
    onNext: () -> Unit,
    onLanguageChanged: ((Language) -> Unit)? = null
) {
    val viewModel: com.cardpop.app.presentation.viewmodel.AppSettingsViewModel =
        androidx.hilt.navigation.compose.hiltViewModel()
    val currentLanguage: Language by viewModel.appLocale.collectAsState()
    var showLanguageDialog by remember { mutableStateOf(false) }

    WelcomeStepCard(
        title = stringResource(R.string.welcome_intro_title),
        content = {
            Column {
                Text(
                    text = stringResource(R.string.welcome_intro_description),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                Text(
                    text = stringResource(R.string.welcome_intro_features_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                listOf(
                    stringResource(R.string.welcome_intro_benefit),
                    stringResource(R.string.welcome_intro_feature_anki),
                    stringResource(R.string.welcome_intro_feature_fsrs),
                ).forEach { feature ->
                    Text(
                        text = feature,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF90CAF9),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }
            }
        },
        buttonText = stringResource(R.string.welcome_intro_button),
        onButtonClick = { showLanguageDialog = true },
        isButtonEnabled = true
    )

    if (showLanguageDialog) {
        WelcomeLanguageSelectionDialog(
            currentLanguage = currentLanguage,
            preselectCurrent = false,
            onLanguageSelected = { selectedLanguage ->
                viewModel.setAppLocale(selectedLanguage)
                onLanguageChanged?.invoke(selectedLanguage)
                showLanguageDialog = false
                onNext()
            },
            onDismiss = { showLanguageDialog = false }
        )
    }
}

@Composable
fun PrivacyOfflineStep(onNext: () -> Unit) {
    WelcomeStepCard(
        title = stringResource(R.string.welcome_privacy_title),
        content = {
            Column {
                Text(
                    text = stringResource(R.string.welcome_privacy_description),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                Text(
                    text = stringResource(R.string.welcome_privacy_flow),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        buttonText = stringResource(R.string.welcome_privacy_button),
        onButtonClick = onNext,
        isButtonEnabled = true
    )
}

@Composable
fun BackupFolderStep(
    hasFolderConfigured: Boolean,
    onRequestFolderSelection: () -> Unit,
    onNext: () -> Unit
) {
    WelcomeStepCard(
        title = stringResource(R.string.welcome_backup_title),
        content = {
            Column {
                Text(
                    text = stringResource(R.string.welcome_backup_description),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = stringResource(R.string.welcome_backup_restore_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = stringResource(R.string.welcome_backup_benefits_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                listOf(
                    stringResource(R.string.welcome_backup_benefit_1),
                    stringResource(R.string.welcome_backup_benefit_2),
                    stringResource(R.string.welcome_backup_benefit_3),
                    stringResource(R.string.welcome_backup_benefit_4)
                ).forEach { benefit ->
                    Text(
                        text = benefit,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                PermissionStatusIndicator(
                    isGranted = hasFolderConfigured,
                    grantedText = stringResource(R.string.welcome_backup_granted),
                    deniedText = stringResource(R.string.welcome_backup_required)
                )
            }
        },
        buttonText = if (hasFolderConfigured) stringResource(R.string.welcome_backup_button_continue)
                     else stringResource(R.string.welcome_backup_button_select),
        onButtonClick = if (hasFolderConfigured) onNext else onRequestFolderSelection,
        isButtonEnabled = true
    )
}

@Composable
fun OverlayPermissionStep(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onNext: () -> Unit
) {
    WelcomeStepCard(
        title = stringResource(R.string.welcome_overlay_title),
        content = {
            Column {
                Text(
                    text = stringResource(R.string.welcome_overlay_description),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                PermissionStatusIndicator(
                    isGranted = hasPermission,
                    grantedText = stringResource(R.string.welcome_overlay_granted),
                    deniedText = stringResource(R.string.welcome_overlay_required)
                )
            }
        },
        buttonText = if (hasPermission) stringResource(R.string.welcome_overlay_button_continue)
                     else stringResource(R.string.welcome_overlay_button_grant),
        onButtonClick = if (hasPermission) onNext else onRequestPermission,
        isButtonEnabled = true
    )
}

/**
 * Combined battery-optimization + notification step.
 * Explains that both are needed for reliable popups. Staged: fix battery first, then notification.
 */
@Composable
fun PermissionsStep(
    isBatteryOptimizationDisabled: Boolean,
    hasNotificationPermission: Boolean,
    onRequestDisableBattery: () -> Unit,
    onRequestNotification: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit
) {
    val allGranted = isBatteryOptimizationDisabled && hasNotificationPermission

    val primaryText: String
    val primaryAction: () -> Unit
    when {
        !isBatteryOptimizationDisabled -> {
            primaryText = stringResource(R.string.welcome_permissions_button_battery)
            primaryAction = onRequestDisableBattery
        }
        !hasNotificationPermission -> {
            primaryText = stringResource(R.string.welcome_permissions_button_notification)
            primaryAction = onRequestNotification
        }
        else -> {
            primaryText = stringResource(R.string.welcome_permissions_button_continue)
            primaryAction = onNext
        }
    }

    WelcomeStepCard(
        title = stringResource(R.string.welcome_permissions_title),
        content = {
            Column {
                Text(
                    text = stringResource(R.string.welcome_permissions_description),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                PermissionStatusIndicator(
                    isGranted = isBatteryOptimizationDisabled,
                    grantedText = stringResource(R.string.welcome_battery_granted),
                    deniedText = stringResource(R.string.welcome_battery_enabled)
                )

                Spacer(modifier = Modifier.height(4.dp))

                PermissionStatusIndicator(
                    isGranted = hasNotificationPermission,
                    grantedText = stringResource(R.string.welcome_permissions_notification_granted),
                    deniedText = stringResource(R.string.welcome_permissions_notification_required)
                )
            }
        },
        buttonText = primaryText,
        onButtonClick = primaryAction,
        isButtonEnabled = true,
        secondaryButtonText = if (allGranted) null else stringResource(R.string.welcome_permissions_button_skip),
        onSecondaryButtonClick = if (allGranted) null else onSkip
    )
}

@Composable
fun BackupCheckStep(
    hasBackup: Boolean,
    backupInfo: BackupInfo,
    onRestore: () -> Unit,
    onSkip: () -> Unit
) {
    WelcomeStepCard(
        title = if (hasBackup) stringResource(R.string.welcome_backup_check_found_title)
                else stringResource(R.string.welcome_backup_check_fresh_title),
        content = {
            Column {
                if (hasBackup) {
                    Text(
                        text = stringResource(R.string.welcome_backup_check_found_description),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Text(
                        text = stringResource(R.string.welcome_backup_check_details_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = stringResource(
                            R.string.welcome_backup_check_created,
                            java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                                .format(java.util.Date(backupInfo.createdAt))
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    Text(
                        text = stringResource(R.string.welcome_backup_check_categories, backupInfo.categoryCount),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    Text(
                        text = stringResource(R.string.welcome_backup_check_flashcards, backupInfo.flashcardCount),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Text(
                        text = stringResource(R.string.welcome_backup_check_question),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = stringResource(R.string.welcome_backup_check_fresh_description),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }
        },
        buttonText = if (hasBackup) stringResource(R.string.welcome_backup_check_button_restore)
                     else stringResource(R.string.welcome_backup_check_button_fresh),
        onButtonClick = if (hasBackup) onRestore else onSkip,
        isButtonEnabled = true,
        secondaryButtonText = if (hasBackup) stringResource(R.string.welcome_backup_check_button_fresh) else null,
        onSecondaryButtonClick = if (hasBackup) onSkip else null
    )
}

/** Optional usage-access step for the app blocklist feature. */
@Composable
fun UsageAccessStep(
    hasAccess: Boolean,
    onRequestAccess: () -> Unit,
    onNext: () -> Unit
) {
    WelcomeStepCard(
        title = stringResource(R.string.welcome_usage_title),
        content = {
            Column {
                Text(
                    text = stringResource(R.string.welcome_usage_description),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = stringResource(R.string.welcome_usage_optional),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                PermissionStatusIndicator(
                    isGranted = hasAccess,
                    grantedText = stringResource(R.string.welcome_usage_granted),
                    deniedText = stringResource(R.string.welcome_usage_required)
                )
            }
        },
        buttonText = if (hasAccess) stringResource(R.string.welcome_usage_button_continue)
                     else stringResource(R.string.welcome_usage_button_grant),
        onButtonClick = if (hasAccess) onNext else onRequestAccess,
        isButtonEnabled = true,
        secondaryButtonText = if (hasAccess) null else stringResource(R.string.welcome_usage_button_skip),
        onSecondaryButtonClick = if (hasAccess) null else onNext
    )
}

@Composable
fun CompletedStep(onEnterApp: () -> Unit) {
    WelcomeStepCard(
        title = stringResource(R.string.welcome_completed_title),
        content = {
            Column {
                Text(
                    text = stringResource(R.string.welcome_completed_description),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = stringResource(R.string.welcome_completed_ready_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                listOf(
                    stringResource(R.string.welcome_completed_feature_1),
                    stringResource(R.string.welcome_completed_feature_2),
                    stringResource(R.string.welcome_completed_feature_3),
                    stringResource(R.string.welcome_completed_feature_4)
                ).forEach { feature ->
                    Text(
                        text = feature,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.welcome_completed_happy_learning),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2196F3)
                )
            }
        },
        buttonText = stringResource(R.string.welcome_completed_button),
        onButtonClick = onEnterApp,
        isButtonEnabled = true
    )
}
