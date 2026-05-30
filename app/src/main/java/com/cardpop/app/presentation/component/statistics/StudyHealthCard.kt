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

package com.cardpop.app.presentation.component.statistics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cardpop.app.R
import com.cardpop.app.domain.model.StudyHealth
import com.cardpop.app.domain.model.StudyHealthStatus
import com.cardpop.app.domain.model.StudyTip

/**
 * Card displayed at the top of the Statistics screen that distils FSRS data into one
 * overall health status and up to two actionable tips.
 *
 * @param health          The computed [StudyHealth] result.
 * @param onLeechTipClick Called when the user taps the LEECHES tip to filter the card list.
 *                        Null when the tip isn't shown.
 */
@Composable
fun StudyHealthCard(
    health: StudyHealth,
    onLeechTipClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = getStatisticsSurface()),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row: title + status chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.study_health_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = getStatisticsOnSurface()
                )
                StatusChip(status = health.status)
            }

            // Tips — show top two
            val shownTips = health.tips.take(2)
            if (shownTips.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = getStatisticsCardBorder(), thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))
                shownTips.forEach { tip ->
                    TipRow(
                        tip      = tip,
                        leechCount = health.leechCount,
                        onClick  = if (tip == StudyTip.LEECHES) onLeechTipClick else null
                    )
                    if (tip != shownTips.last()) Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

// ── Internal composables ───────────────────────────────────────────────────────

@Composable
private fun StatusChip(status: StudyHealthStatus) {
    val (icon, color, label) = statusAttributes(status)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.15f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

@Composable
private fun TipRow(
    tip: StudyTip,
    leechCount: Int,
    onClick: (() -> Unit)?
) {
    val tipText = tipText(tip = tip, leechCount = leechCount)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null)
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                else Modifier
            )
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = tipText,
            style = MaterialTheme.typography.bodyMedium,
            color = getStatisticsOnSurfaceVariant(),
            modifier = Modifier.weight(1f)
        )
        if (onClick != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = getStatisticsOnSurfaceVariant(),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ── String / color helpers (enum → UI, keeps domain free of R) ────────────────

private data class StatusAttrs(val icon: ImageVector, val color: Color, val label: String)

@Composable
private fun statusAttributes(status: StudyHealthStatus): StatusAttrs = when (status) {
    StudyHealthStatus.ON_TRACK        -> StatusAttrs(Icons.Filled.CheckCircle, AccentGreen,
                                            stringResource(R.string.study_health_status_on_track))
    StudyHealthStatus.GOOD            -> StatusAttrs(Icons.Filled.Info, AccentAmber,
                                            stringResource(R.string.study_health_status_good))
    StudyHealthStatus.NEEDS_ATTENTION -> StatusAttrs(Icons.Filled.Warning, AccentRed,
                                            stringResource(R.string.study_health_status_needs_attention))
    StudyHealthStatus.GETTING_STARTED -> StatusAttrs(Icons.Filled.Star, AccentTeal,
                                            stringResource(R.string.study_health_status_getting_started))
}

@Composable
private fun tipText(tip: StudyTip, leechCount: Int): String = when (tip) {
    StudyTip.CATCH_UP_BACKLOG    -> stringResource(R.string.study_tip_catch_up_backlog)
    StudyTip.LOW_ACCURACY        -> stringResource(R.string.study_tip_low_accuracy)
    StudyTip.LOW_STABILITY_CHURN -> stringResource(R.string.study_tip_low_stability_churn)
    StudyTip.HOLD_OFF_NEW_CARDS  -> stringResource(R.string.study_tip_hold_off_new_cards)
    StudyTip.LEECHES             -> stringResource(R.string.study_tip_leeches, leechCount)
    StudyTip.DECK_TOO_HARD       -> stringResource(R.string.study_tip_deck_too_hard)
    StudyTip.STUDY_DAILY         -> stringResource(R.string.study_tip_study_daily)
    StudyTip.ADD_MORE_CARDS      -> stringResource(R.string.study_tip_add_more_cards)
    StudyTip.RETENTION_HIGH      -> stringResource(R.string.study_tip_retention_high)
    StudyTip.KEEP_GOING          -> stringResource(R.string.study_tip_keep_going)
}
