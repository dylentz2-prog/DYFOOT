package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FixtureStatus
import com.example.data.remote.DYFOOT_LIVE_LEAGUES
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HotTimetableCard
import com.example.ui.components.MatchCard
import com.example.ui.components.MatchCenterBottomSheet
import com.example.ui.components.SegmentedPillSwitcher
import com.example.ui.components.SubScreenTopBar
import com.example.ui.components.TeamBadgeCircle
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.dyfootStrings
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.InfoCyan
import com.example.ui.theme.LiveRed
import com.example.ui.theme.LiveRedContainer
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.NeonLime
import com.example.ui.theme.NeonLimeContainer
import com.example.ui.theme.SlateText
import com.example.viewmodel.DyfootViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScoresScreen(
    viewModel: DyfootViewModel,
    onBack: () -> Unit,
    onOpenLiveArena: () -> Unit
) {
    val context = LocalContext.current
    val language by viewModel.appLanguage.collectAsStateWithLifecycle()
    val copy = remember(language) { dyfootStrings(language) }

    val fixtures by viewModel.combinedFixtures.collectAsStateWithLifecycle()
    val liveStatus by viewModel.liveFeedStatus.collectAsStateWithLifecycle()
    val updatedAt by viewModel.liveFeedUpdatedAt.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncingLiveScores.collectAsStateWithLifecycle()
    val isPullRefreshing by viewModel.isPullRefreshing.collectAsStateWithLifecycle()
    val leagueFilter by viewModel.liveLeagueFilter.collectAsStateWithLifecycle()
    val dateOffset by viewModel.liveDateOffset.collectAsStateWithLifecycle()
    val standings by viewModel.standings.collectAsStateWithLifecycle()
    val standingsStatus by viewModel.standingsStatus.collectAsStateWithLifecycle()
    val matchReminders by viewModel.matchReminders.collectAsStateWithLifecycle()
    val kickoffAlarms by viewModel.kickoffAlarmIds.collectAsStateWithLifecycle()
    val matchPredictions by viewModel.matchPredictions.collectAsStateWithLifecycle()
    val inspectedFixture by viewModel.inspectedFixtureDetail.collectAsStateWithLifecycle()
    val inspectedBundle by viewModel.inspectedMatchBundle.collectAsStateWithLifecycle()
    val isLoadingMatchDetail by viewModel.isLoadingMatchDetail.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) } // 0=Live, 1=Timetable, 2=Finished, 3=Tables
    var selectedTableLeague by remember { mutableStateOf("PL") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var matchSearchQuery by remember { mutableStateOf("") }
    var pendingFavFixture by remember { mutableStateOf<com.example.data.model.FixtureItem?>(null) }
    var pendingAlarmFixture by remember { mutableStateOf<com.example.data.model.FixtureItem?>(null) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        pendingFavFixture?.let { viewModel.toggleMatchReminder(it) }
        pendingFavFixture = null
        pendingAlarmFixture?.let { viewModel.toggleKickoffAlarm(it) }
        pendingAlarmFixture = null
    }

    fun toggleFavoriteWithPerm(fixture: com.example.data.model.FixtureItem) {
        val adding = fixture.id !in matchReminders
        if (adding &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingFavFixture = fixture
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.toggleMatchReminder(fixture)
        }
    }

    fun toggleKickoffWithPerm(fixture: com.example.data.model.FixtureItem) {
        val adding = fixture.id !in kickoffAlarms
        if (adding &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingAlarmFixture = fixture
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.toggleKickoffAlarm(fixture)
        }
    }

    val liveCount = fixtures.count { it.status == FixtureStatus.LIVE.name }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBlack)
            .navigationBarsPadding()
    ) {
        SubScreenTopBar(
            title = copy.liveScoresTitle,
            onBack = onBack,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenLiveArena) {
                        Icon(Icons.Filled.LiveTv, contentDescription = "Live Arena", tint = NeonLime)
                    }
                    IconButton(onClick = { viewModel.syncLiveFootballScores(leagueFilter, dateOffset) }) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                color = NeonLime,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = copy.refresh, tint = CrispWhite)
                        }
                    }
                }
            }
        )

        // Status + Live Count Strip
        Surface(
            color = MidnightCard,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, if (liveCount > 0) LiveRed.copy(alpha = 0.5f) else MidnightBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (liveCount > 0) LiveRed else NeonLime)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = buildString {
                            append(liveStatus ?: "Worldwide Sports Live Center")
                            if (!updatedAt.isNullOrBlank()) append(" • $updatedAt")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = CrispWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (liveCount > 0) {
                    Surface(color = LiveRedContainer, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = "$liveCount LIVE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = LiveRed,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        // Main Segment Switcher (Live | Timetable | Finished | Tables)
        SegmentedPillSwitcher(
            options = listOf(
                copy.liveTab to null,
                copy.timetableTab to null,
                copy.finishedTab to null,
                copy.tablesTab to null
            ),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        val pullRefreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isPullRefreshing,
            onRefresh = { viewModel.refreshAllLiveContent() },
            state = pullRefreshState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullRefreshState,
                    isRefreshing = isPullRefreshing,
                    containerColor = MidnightCard,
                    color = NeonLime,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("live_scores_pull_refresh")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
        if (selectedTab < 3) {
            OutlinedTextField(
                value = matchSearchQuery,
                onValueChange = { matchSearchQuery = it },
                placeholder = {
                    Text(
                        text = if (language == AppLanguage.SWAHILI) "Tafuta mechi, timu, ligi au uwanja..." else "Search matches, teams, leagues or venues...",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateText
                    )
                },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = NeonLime, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (matchSearchQuery.isNotBlank()) {
                        IconButton(onClick = { matchSearchQuery = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search", tint = SlateText, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonLime,
                    unfocusedBorderColor = MidnightBorder,
                    focusedTextColor = CrispWhite,
                    unfocusedTextColor = CrispWhite,
                    focusedContainerColor = MidnightCard,
                    unfocusedContainerColor = MidnightCard
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("live_matches_search_input")
            )
            // Date Filter Pills (Yesterday | Today | Tomorrow)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(-1 to copy.yesterday, 0 to copy.today, 1 to copy.tomorrow).forEach { (offset, label) ->
                    val active = dateOffset == offset
                    Surface(
                        onClick = { viewModel.syncLiveFootballScores(leagueFilter, offset) },
                        shape = RoundedCornerShape(18.dp),
                        color = if (active) NeonLime else MidnightCard,
                        border = BorderStroke(1.dp, if (active) NeonLime else MidnightBorder),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("date_filter_$offset")
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) MidnightBlack else SlateText
                            )
                        }
                    }
                }
            }

            // Worldwide League & Sport Filter Strip (+ Favorites filter chip)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = favoritesOnly,
                        onClick = { favoritesOnly = !favoritesOnly },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        label = {
                            Text(
                                "${copy.favoritesFilter} (${matchReminders.size})",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonLimeContainer,
                            selectedLabelColor = NeonLime,
                            selectedLeadingIconColor = NeonLime,
                            containerColor = MidnightCard,
                            labelColor = SlateText
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = favoritesOnly,
                            borderColor = MidnightBorder,
                            selectedBorderColor = NeonLime
                        ),
                        modifier = Modifier.testTag("live_favorites_filter_chip")
                    )
                }
                items(DYFOOT_LIVE_LEAGUES, key = { it.code }) { league ->
                    val selected = leagueFilter == league.code
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.syncLiveFootballScores(league.code, dateOffset) },
                        label = { Text(league.label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonLimeContainer,
                            selectedLabelColor = NeonLime,
                            containerColor = MidnightCard,
                            labelColor = SlateText
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = MidnightBorder,
                            selectedBorderColor = NeonLime
                        )
                    )
                }
            }

            val leagueMatched = fixtures.filter {
                val matchesLeague = leagueFilter == "ALL" ||
                    it.leagueCode.equals(leagueFilter, ignoreCase = true) ||
                    it.competition.contains(
                        DYFOOT_LIVE_LEAGUES.firstOrNull { l -> l.code == leagueFilter }?.label ?: "",
                        ignoreCase = true
                    )
                val matchesFav = !favoritesOnly || it.id in matchReminders
                val q = matchSearchQuery.trim()
                val matchesQuery = q.isBlank() ||
                    it.homeTeam.contains(q, ignoreCase = true) ||
                    it.awayTeam.contains(q, ignoreCase = true) ||
                    it.competition.contains(q, ignoreCase = true) ||
                    it.venue.contains(q, ignoreCase = true) ||
                    it.sportCategory.contains(q, ignoreCase = true)
                matchesLeague && matchesFav && matchesQuery
            }
            val filtered = when (selectedTab) {
                0 -> leagueMatched.filter { it.status == FixtureStatus.LIVE.name }
                    .ifEmpty { if (dateOffset == 0 || matchSearchQuery.isNotBlank()) leagueMatched else emptyList() }
                1 -> leagueMatched.sortedWith(
                    compareBy<com.example.data.model.FixtureItem>({ if (it.isHotMatch) 0 else 1 }, { it.kickoffEpochMs })
                )
                else -> leagueMatched.filter { it.status == FixtureStatus.FINISHED.name }
            }

            if (filtered.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    EmptyStateCard(
                        icon = Icons.Filled.SportsSoccer,
                        title = "No Matches in This Filter",
                        subtitle = "Pull down to refresh or switch between ${copy.yesterday} / ${copy.today} / ${copy.tomorrow}."
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.id }) { fixture ->
                        if (selectedTab == 1) {
                            HotTimetableCard(
                                fixture = fixture,
                                isReminded = fixture.id in matchReminders,
                                userPick = matchPredictions[fixture.id],
                                copy = copy,
                                onToggleReminder = { toggleFavoriteWithPerm(fixture) },
                                onClick = { viewModel.inspectFixture(fixture) },
                                isKickoffReminded = fixture.id in kickoffAlarms,
                                onToggleKickoffReminder = { toggleKickoffWithPerm(fixture) }
                            )
                        } else {
                            MatchCard(
                                fixture = fixture,
                                onClick = { viewModel.inspectFixture(fixture) },
                                modifier = Modifier.fillMaxWidth(),
                                isFavorite = fixture.id in matchReminders,
                                onToggleFavorite = { toggleFavoriteWithPerm(fixture) },
                                isReminded = fixture.id in kickoffAlarms,
                                onToggleReminder = { toggleKickoffWithPerm(fixture) }
                            )
                        }
                    }
                }
            }
        } else {
            val tableLeagues = DYFOOT_LIVE_LEAGUES.filter { it.sportPath.startsWith("soccer/") }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tableLeagues, key = { it.code }) { league ->
                    val selected = selectedTableLeague == league.code
                    FilterChip(
                        selected = selected,
                        onClick = {
                            selectedTableLeague = league.code
                            viewModel.loadStandings(league.code)
                        },
                        label = { Text(league.label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonLimeContainer,
                            selectedLabelColor = NeonLime,
                            containerColor = MidnightCard,
                            labelColor = SlateText
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = MidnightBorder,
                            selectedBorderColor = NeonLime
                        )
                    )
                }
            }

            if (standings.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    EmptyStateCard(
                        icon = Icons.Filled.SportsSoccer,
                        title = "Loading League Table",
                        subtitle = standingsStatus ?: "Pull down to refresh or select a competition above."
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!standingsStatus.isNullOrBlank()) {
                        item {
                            Text(
                                text = standingsStatus.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = InfoCyan,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("#   Club", style = MaterialTheme.typography.labelSmall, color = SlateText, modifier = Modifier.weight(1f))
                            Text("P   W   D   L   GD   PTS", style = MaterialTheme.typography.labelSmall, color = SlateText)
                        }
                    }
                    items(standings, key = { "${it.position}_${it.teamName}" }) { row ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MidnightCard),
                            border = BorderStroke(
                                1.dp,
                                if (row.position <= 4) NeonLime.copy(alpha = 0.35f) else MidnightBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${row.position}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (row.position <= 4) NeonLime else SlateText,
                                    modifier = Modifier.width(24.dp)
                                )
                                TeamBadgeCircle(
                                    teamName = row.teamName,
                                    logoUrl = row.teamLogoUrl,
                                    size = 24.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = row.teamName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = CrispWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${row.played}  ${row.won}  ${row.drawn}  ${row.lost}  ${row.goalDiff}  ",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SlateText
                                )
                                Text(
                                    text = row.points.toString(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = NeonLime
                                )
                            }
                        }
                    }
                }
            }
        }
            }
        }
    }

    inspectedFixture?.let { f ->
        MatchCenterBottomSheet(
            fixture = f,
            isLoadingDetail = isLoadingMatchDetail,
            isReminded = f.id in matchReminders,
            userPrediction = matchPredictions[f.id],
            copy = copy,
            onDismiss = { viewModel.clearInspectedFixture() },
            onToggleReminder = { toggleFavoriteWithPerm(f) },
            onSelectPrediction = { pick -> viewModel.setMatchPrediction(f.id, pick) },
            onOpenTips = {
                viewModel.clearInspectedFixture()
                onBack()
            },
            onOpenLiveArena = {
                viewModel.clearInspectedFixture()
                onOpenLiveArena()
            },
            matchBundle = inspectedBundle,
            isKickoffReminded = f.id in kickoffAlarms,
            onToggleKickoffReminder = { toggleKickoffWithPerm(f) },
            isSwahili = language == AppLanguage.SWAHILI
        )
    }
}
