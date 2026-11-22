package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material.icons.filled.Stadium
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FixtureItem
import com.example.data.model.FixtureStatus
import com.example.data.model.MatchDetailBundle
import com.example.data.model.OWNER_INSTAGRAM_HANDLE
import com.example.data.model.OWNER_INSTAGRAM_URL
import com.example.data.model.OWNER_TIKTOK_HANDLE
import com.example.data.model.OWNER_TIKTOK_URL
import com.example.data.model.OWNER_YOUTUBE_HANDLE
import com.example.data.model.OWNER_YOUTUBE_URL
import com.example.ui.i18n.DyfootCopy
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.InfoCyan
import com.example.ui.theme.InfoCyanContainer
import com.example.ui.theme.LiveRed
import com.example.ui.theme.LiveRedContainer
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.MidnightCardAlt
import com.example.ui.theme.MidnightElevated
import com.example.ui.theme.MutedText
import com.example.ui.theme.NeonLime
import com.example.ui.theme.NeonLimeContainer
import com.example.ui.theme.SlateText
import com.example.ui.theme.VipAmber
import com.example.ui.theme.VipAmberContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HotTimetableCard(
    fixture: FixtureItem,
    isReminded: Boolean,
    userPick: String?,
    copy: DyfootCopy,
    onToggleReminder: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isKickoffReminded: Boolean = false,
    onToggleKickoffReminder: (() -> Unit)? = null
) {
    val status = FixtureStatus.safeFrom(fixture.status)
    val timeFmt = SimpleDateFormat("EEE, HH:mm", Locale.getDefault())
    val kickoffFormatted = if (fixture.kickoffEpochMs > 0L) timeFmt.format(Date(fixture.kickoffEpochMs)) else fixture.minuteLabel
    val countdownLabel = rememberKickoffCountdown(fixture.kickoffEpochMs, status, fixture.minuteLabel)

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightCard),
        border = BorderStroke(
            1.dp,
            when {
                status == FixtureStatus.LIVE -> LiveRed.copy(alpha = 0.65f)
                isReminded -> NeonLime.copy(alpha = 0.6f)
                else -> MidnightBorder
            }
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        color = if (status == FixtureStatus.LIVE) LiveRedContainer else NeonLimeContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (status == FixtureStatus.LIVE) "LIVE ${fixture.minuteLabel}" else kickoffFormatted,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (status == FixtureStatus.LIVE) LiveRed else NeonLime,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = fixture.competition,
                        style = MaterialTheme.typography.labelMedium,
                        color = SlateText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (countdownLabel.isNotBlank()) {
                        Surface(color = MidnightCardAlt, shape = RoundedCornerShape(8.dp)) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Schedule, contentDescription = null, tint = InfoCyan, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(countdownLabel, style = MaterialTheme.typography.labelSmall, color = InfoCyan)
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    if (onToggleKickoffReminder != null && status != FixtureStatus.FINISHED) {
                        Surface(
                            onClick = onToggleKickoffReminder,
                            shape = RoundedCornerShape(10.dp),
                            color = if (isKickoffReminded) InfoCyanContainer else MidnightCardAlt,
                            border = BorderStroke(1.dp, if (isKickoffReminded) InfoCyan else MidnightBorder),
                            modifier = Modifier.testTag("remind_timetable_${fixture.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isKickoffReminded) Icons.Filled.NotificationsActive else Icons.Outlined.Notifications,
                                    contentDescription = if (isKickoffReminded) copy.reminded else copy.remindMe,
                                    tint = if (isKickoffReminded) InfoCyan else CrispWhite,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (isKickoffReminded) copy.reminded else copy.remindMe,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isKickoffReminded) InfoCyan else CrispWhite
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Surface(
                        onClick = onToggleReminder,
                        shape = RoundedCornerShape(10.dp),
                        color = if (isReminded) NeonLime else MidnightCardAlt,
                        border = BorderStroke(1.dp, if (isReminded) NeonLime else MidnightBorder),
                        modifier = Modifier.testTag("fav_timetable_${fixture.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isReminded) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = if (isReminded) copy.favedAlertsOn else copy.favButton,
                                tint = if (isReminded) MidnightBlack else CrispWhite,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isReminded) copy.favedAlertsOn else copy.favButton,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isReminded) MidnightBlack else CrispWhite
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    TeamBadgeCircle(fixture.homeTeam, fixture.homeLogoUrl, 28.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = fixture.homeTeam.replace("[SAMPLE]", "").trim(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = CrispWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    color = MidnightCardAlt,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = if (status == FixtureStatus.UPCOMING) "VS" else "${fixture.homeScore} : ${fixture.awayScore}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = if (status == FixtureStatus.LIVE) NeonLime else CrispWhite,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = fixture.awayTeam.replace("[SAMPLE]", "").trim(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = CrispWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TeamBadgeCircle(fixture.awayTeam, fixture.awayLogoUrl, 28.dp)
                }
            }

            if (fixture.venue.isNotBlank() || userPick != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = fixture.venue.ifBlank { fixture.sportCategory },
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (userPick != null) {
                        Surface(color = VipAmberContainer, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                text = "Pick: $userPick",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = VipAmber,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun rememberKickoffCountdown(kickoffMs: Long, status: FixtureStatus, fallback: String): String {
    if (status != FixtureStatus.UPCOMING || kickoffMs <= 0L) return ""
    val diff = kickoffMs - System.currentTimeMillis()
    if (diff <= 0L) return fallback
    val totalMin = diff / 60_000L
    val hours = totalMin / 60L
    val mins = totalMin % 60L
    return when {
        hours >= 24L -> "${hours / 24L}d"
        hours > 0L -> "${hours}h ${mins}m"
        else -> "${mins}m"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchCenterBottomSheet(
    fixture: FixtureItem,
    isLoadingDetail: Boolean,
    isReminded: Boolean,
    userPrediction: String?,
    copy: DyfootCopy,
    onDismiss: () -> Unit,
    onToggleReminder: () -> Unit,
    onSelectPrediction: (String) -> Unit,
    onOpenTips: () -> Unit,
    onOpenLiveArena: () -> Unit,
    matchBundle: MatchDetailBundle? = null,
    isKickoffReminded: Boolean = false,
    onToggleKickoffReminder: (() -> Unit)? = null,
    isSwahili: Boolean = false
) {
    val context = LocalContext.current
    val status = FixtureStatus.safeFrom(fixture.status)
    var activeTab by remember(fixture.id) {
        mutableIntStateOf(if (status == FixtureStatus.UPCOMING) 2 else 0)
    }
    val hasStats = fixture.possessionHome.isNotBlank() ||
        fixture.shotsHome.isNotBlank() ||
        fixture.cornersHome.isNotBlank() ||
        fixture.yellowCardsHome.isNotBlank() ||
        fixture.foulsHome.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MidnightElevated
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(fixture.competition, style = MaterialTheme.typography.labelLarge, color = NeonLime, modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (onToggleKickoffReminder != null && status != FixtureStatus.FINISHED) {
                        IconButton(
                            onClick = onToggleKickoffReminder,
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("sheet_remind_btn_${fixture.id}")
                        ) {
                            Icon(
                                imageVector = if (isKickoffReminded) Icons.Filled.NotificationsActive else Icons.Outlined.Notifications,
                                contentDescription = if (isKickoffReminded) copy.reminded else copy.remindMe,
                                tint = if (isKickoffReminded) InfoCyan else CrispWhite
                            )
                        }
                    }
                    IconButton(
                        onClick = onToggleReminder,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("sheet_fav_btn_${fixture.id}")
                    ) {
                        Icon(
                            imageVector = if (isReminded) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (isReminded) copy.favedAlertsOn else copy.favButton,
                            tint = if (isReminded) NeonLime else CrispWhite
                        )
                    }
                    Surface(
                        color = if (status == FixtureStatus.LIVE) LiveRedContainer else MidnightCard,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (status == FixtureStatus.LIVE) "LIVE ${fixture.minuteLabel}" else fixture.minuteLabel.ifBlank { status.label },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (status == FixtureStatus.LIVE) LiveRed else CrispWhite,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Scoreboard Header Card with Animated Score Ticker
            Surface(
                color = MidnightCard,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, if (status == FixtureStatus.LIVE) LiveRed.copy(alpha = 0.5f) else MidnightBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        TeamBadgeCircle(fixture.homeTeam, fixture.homeLogoUrl, 46.dp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = fixture.homeTeam.replace("[SAMPLE]", "").trim(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = CrispWhite,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val scoreLabel = if (status == FixtureStatus.UPCOMING) "VS" else "${fixture.homeScore} - ${fixture.awayScore}"
                        AnimatedContent(
                            targetState = scoreLabel,
                            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.88f)) togetherWith fadeOut() },
                            label = "animated_scoreboard"
                        ) { targetScore ->
                            Text(
                                text = targetScore,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (status == FixtureStatus.LIVE) NeonLime else CrispWhite
                            )
                        }
                        Text(
                            text = fixture.sportCategory,
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateText
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        TeamBadgeCircle(fixture.awayTeam, fixture.awayLogoUrl, 46.dp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = fixture.awayTeam.replace("[SAMPLE]", "").trim(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = CrispWhite,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }
            }

            if (isLoadingDetail) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(color = NeonLime, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSwahili) "Inasawazisha kikosi, H2H, kona na matukio..." else "Syncing squad XI, H2H, possession & events...",
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateText
                    )
                }
            }

            // 4-Tab Interactive Match Center Switcher (Visualizer | Squad XI | H2H & Stats | Predict)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val tabLabels = listOf(
                    0 to (if (isSwahili) "Kionyeshi" else "Visualizer"),
                    1 to (if (isSwahili) "Kikosi" else "Squad XI"),
                    2 to (if (isSwahili) "H2H" else "H2H"),
                    3 to (if (isSwahili) "Utabiri" else "Predict")
                )
                tabLabels.forEach { (idx, label) ->
                    val selected = activeTab == idx
                    Surface(
                        onClick = { activeTab = idx },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) NeonLime else MidnightCard,
                        border = BorderStroke(1.dp, if (selected) NeonLime else MidnightBorder),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("match_sheet_tab_$idx")
                    ) {
                        Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (selected) MidnightBlack else SlateText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            when (activeTab) {
                0 -> {
                    // Animated Live Visualizer + Match Stats + Goals/Cards Timeline
                    if (matchBundle != null) {
                        LiveMatchVisualizerCard(bundle = matchBundle, isSwahili = isSwahili)
                    }
                    if (hasStats) {
                        Surface(
                            color = MidnightCard,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MidnightBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = copy.matchStatsTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = NeonLime
                                )
                                if (fixture.possessionHome.isNotBlank() || fixture.possessionAway.isNotBlank()) {
                                    MatchStatComparisonRow(copy.possession, fixture.possessionHome, fixture.possessionAway)
                                }
                                if (fixture.shotsHome.isNotBlank() || fixture.shotsAway.isNotBlank()) {
                                    MatchStatComparisonRow(copy.totalShots, fixture.shotsHome, fixture.shotsAway)
                                }
                                if (fixture.shotsOnTargetHome.isNotBlank() || fixture.shotsOnTargetAway.isNotBlank()) {
                                    MatchStatComparisonRow(copy.shotsOnTarget, fixture.shotsOnTargetHome, fixture.shotsOnTargetAway)
                                }
                                if (fixture.cornersHome.isNotBlank() || fixture.cornersAway.isNotBlank()) {
                                    MatchStatComparisonRow("🚩 ${copy.corners}", fixture.cornersHome, fixture.cornersAway)
                                }
                                if (fixture.yellowCardsHome.isNotBlank() || fixture.yellowCardsAway.isNotBlank()) {
                                    MatchStatComparisonRow("🟨 ${copy.yellowCards}", fixture.yellowCardsHome, fixture.yellowCardsAway)
                                }
                                if (fixture.redCardsHome.isNotBlank() || fixture.redCardsAway.isNotBlank()) {
                                    MatchStatComparisonRow("🟥 ${copy.redCards}", fixture.redCardsHome, fixture.redCardsAway)
                                }
                                if (fixture.foulsHome.isNotBlank() || fixture.foulsAway.isNotBlank()) {
                                    MatchStatComparisonRow(copy.fouls, fixture.foulsHome, fixture.foulsAway)
                                }
                                if (fixture.offsidesHome.isNotBlank() || fixture.offsidesAway.isNotBlank()) {
                                    MatchStatComparisonRow(copy.offsides, fixture.offsidesHome, fixture.offsidesAway)
                                }
                                if (fixture.passesHome.isNotBlank() || fixture.passesAway.isNotBlank()) {
                                    MatchStatComparisonRow(copy.passes, fixture.passesHome, fixture.passesAway)
                                }
                            }
                        }
                    }
                    if (fixture.eventsSummary.isNotBlank()) {
                        Surface(
                            color = MidnightCard,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MidnightBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(copy.timelineTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = NeonLime)
                                Text(fixture.eventsSummary, style = MaterialTheme.typography.bodySmall, color = CrispWhite, lineHeight = 20.sp)
                            }
                        }
                    }
                }

                1 -> {
                    // Animated 2D Squad Formation Pitch & Lineups
                    if (matchBundle != null) {
                        SquadFormationVisualizerCard(bundle = matchBundle, isSwahili = isSwahili)
                    } else if (fixture.lineupsSummary.isNotBlank()) {
                        Surface(
                            color = MidnightCardAlt,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MidnightBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Groups, contentDescription = null, tint = InfoCyan, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(copy.lineupsTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = InfoCyan)
                                }
                                Text(fixture.lineupsSummary, style = MaterialTheme.typography.bodySmall, color = SlateText)
                            }
                        }
                    }
                }

                2 -> {
                    // Pre-Match Statistical & Head-to-Head (H2H) Analysis
                    if (matchBundle != null) {
                        PreMatchH2HAnalysisCard(bundle = matchBundle, isSwahili = isSwahili)
                    }
                    if (hasStats) {
                        Surface(
                            color = MidnightCard,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MidnightBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(copy.matchStatsTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = NeonLime)
                                if (fixture.possessionHome.isNotBlank() || fixture.possessionAway.isNotBlank()) {
                                    MatchStatComparisonRow(copy.possession, fixture.possessionHome, fixture.possessionAway)
                                }
                                if (fixture.shotsHome.isNotBlank() || fixture.shotsAway.isNotBlank()) {
                                    MatchStatComparisonRow(copy.totalShots, fixture.shotsHome, fixture.shotsAway)
                                }
                                if (fixture.cornersHome.isNotBlank() || fixture.cornersAway.isNotBlank()) {
                                    MatchStatComparisonRow("🚩 ${copy.corners}", fixture.cornersHome, fixture.cornersAway)
                                }
                            }
                        }
                    }
                }

                else -> {
                    // Venue/Referee, Kickoff Reminder Toggle & Fan Predictor (1/X/2)
                    if (fixture.venue.isNotBlank() || fixture.referee.isNotBlank()) {
                        Surface(
                            color = MidnightCardAlt,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MidnightBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (fixture.venue.isNotBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Stadium, contentDescription = null, tint = InfoCyan, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${copy.venueLabel}: ${fixture.venue}" +
                                                if (fixture.attendance.isNotBlank()) " (${fixture.attendance})" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = CrispWhite
                                        )
                                    }
                                }
                                if (fixture.referee.isNotBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Sports, contentDescription = null, tint = VipAmber, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("${copy.refereeLabel}: ${fixture.referee}", style = MaterialTheme.typography.bodySmall, color = SlateText)
                                    }
                                }
                            }
                        }
                    }

                    if (onToggleKickoffReminder != null && status != FixtureStatus.FINISHED) {
                        Surface(
                            onClick = onToggleKickoffReminder,
                            color = if (isKickoffReminded) InfoCyanContainer else MidnightCard,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, if (isKickoffReminded) InfoCyan else MidnightBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = if (isKickoffReminded) Icons.Filled.NotificationsActive else Icons.Outlined.Notifications,
                                        contentDescription = null,
                                        tint = InfoCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = if (isSwahili) "Ukumbusho wa Kuanza Mechi (Dakika 15)" else "15-Min Kickoff Reminder Alarm",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = CrispWhite
                                        )
                                        Text(
                                            text = if (isKickoffReminded) {
                                                if (isSwahili) "Utapokea arifa dakika 15 kabla ya mechi." else "Scheduled to alert you 15 minutes before kickoff."
                                            } else {
                                                if (isSwahili) "Gusa kuweka arifa ya kuanza mechi." else "Tap to set a pre-match kickoff notification."
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SlateText
                                        )
                                    }
                                }
                                Text(
                                    text = if (isKickoffReminded) copy.reminded else copy.remindMe,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = InfoCyan
                                )
                            }
                        }
                    }

                    // Fan Match Predictor (1 / X / 2)
                    Surface(
                        color = MidnightCard,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, VipAmber.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.TrendingUp, contentDescription = null, tint = VipAmber, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(copy.fanPredictionTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = CrispWhite)
                                }
                                if (userPrediction != null) {
                                    IconButton(
                                        onClick = {
                                            val share = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(
                                                    Intent.EXTRA_TEXT,
                                                    "My DYFOOT Pick: ${fixture.homeTeam} vs ${fixture.awayTeam} -> $userPrediction (${fixture.competition})"
                                                )
                                            }
                                            context.startActivity(Intent.createChooser(share, copy.sharePrediction))
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Filled.Share, contentDescription = copy.sharePrediction, tint = NeonLime, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf("1" to copy.pickHome, "X" to copy.pickDraw, "2" to copy.pickAway).forEach { (code, label) ->
                                    val picked = userPrediction == code
                                    Surface(
                                        onClick = { onSelectPrediction(code) },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (picked) NeonLime else MidnightCardAlt,
                                        border = BorderStroke(1.dp, if (picked) NeonLime else MidnightBorder),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (picked) MidnightBlack else CrispWhite,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MidnightBorder)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onOpenTips,
                    border = BorderStroke(1.dp, NeonLime),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.ConfirmationNumber, contentDescription = null, tint = NeonLime, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(copy.navTips, color = NeonLime)
                }
                Button(
                    onClick = onOpenLiveArena,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = MidnightBlack),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.LiveTv, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Live Arena", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun MatchStatComparisonRow(
    label: String,
    homeRaw: String,
    awayRaw: String
) {
    val hNum = homeRaw.replace("%", "").trim().toFloatOrNull() ?: 0f
    val aNum = awayRaw.replace("%", "").trim().toFloatOrNull() ?: 0f
    val total = (hNum + aNum).coerceAtLeast(1f)
    val homeRatio = (hNum / total).coerceIn(0.05f, 0.95f)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(homeRaw.ifBlank { "0" }, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = NeonLime)
            Text(label, style = MaterialTheme.typography.labelSmall, color = SlateText)
            Text(awayRaw.ifBlank { "0" }, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = InfoCyan)
        }
        LinearProgressIndicator(
            progress = { if (hNum == 0f && aNum == 0f) 0.5f else homeRatio },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = NeonLime,
            trackColor = InfoCyanContainer
        )
    }
}

@Composable
fun OwnerSocialFooter(
    copy: DyfootCopy,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightCard),
        border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("owner_social_footer")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(MidnightCard, Color(0xFF0A1221))
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = copy.connectOwnerEyebrow,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = NeonLime,
                letterSpacing = 1.2.sp
            )
            Text(
                text = copy.connectOwnerTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = CrispWhite
            )
            Text(
                text = copy.connectOwnerSub,
                style = MaterialTheme.typography.bodySmall,
                color = SlateText
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SocialLinkChip(
                    platform = "TikTok",
                    handle = OWNER_TIKTOK_HANDLE,
                    accent = NeonLime,
                    onClick = { openUrl(OWNER_TIKTOK_URL) },
                    modifier = Modifier.weight(1f).testTag("social_tiktok")
                )
                SocialLinkChip(
                    platform = "Instagram",
                    handle = OWNER_INSTAGRAM_HANDLE,
                    accent = VipAmber,
                    onClick = { openUrl(OWNER_INSTAGRAM_URL) },
                    modifier = Modifier.weight(1f).testTag("social_instagram")
                )
                SocialLinkChip(
                    platform = "YouTube",
                    handle = OWNER_YOUTUBE_HANDLE,
                    accent = LiveRed,
                    onClick = { openUrl(OWNER_YOUTUBE_URL) },
                    modifier = Modifier.weight(1f).testTag("social_youtube")
                )
            }
        }
    }
}

@Composable
private fun SocialLinkChip(
    platform: String,
    handle: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MidnightCardAlt,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayCircle,
                    contentDescription = platform,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = platform,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = CrispWhite,
                maxLines = 1
            )
            Text(
                text = handle,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
