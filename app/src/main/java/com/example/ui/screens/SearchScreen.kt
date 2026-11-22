package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FixtureItem
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MatchCard
import com.example.ui.components.MatchCenterBottomSheet
import com.example.ui.components.SubScreenTopBar
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.dyfootStrings
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.NeonLime
import com.example.ui.theme.NeonLimeContainer
import com.example.ui.theme.SlateText
import com.example.viewmodel.DyfootViewModel

@Composable
fun SearchScreen(
    viewModel: DyfootViewModel,
    onBack: () -> Unit,
    onNavigateTips: () -> Unit,
    onNavigateSportHub: (Int) -> Unit,
    onNavigateLiveScores: () -> Unit,
    onNavigateGroups: () -> Unit
) {
    val context = LocalContext.current
    val language by viewModel.appLanguage.collectAsStateWithLifecycle()
    val copy = remember(language) { dyfootStrings(language) }
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val matchReminders by viewModel.matchReminders.collectAsStateWithLifecycle()
    val kickoffAlarms by viewModel.kickoffAlarmIds.collectAsStateWithLifecycle()
    val matchPredictions by viewModel.matchPredictions.collectAsStateWithLifecycle()
    val inspectedFixture by viewModel.inspectedFixtureDetail.collectAsStateWithLifecycle()
    val inspectedBundle by viewModel.inspectedMatchBundle.collectAsStateWithLifecycle()
    val isLoadingMatchDetail by viewModel.isLoadingMatchDetail.collectAsStateWithLifecycle()

    var filter by remember { mutableStateOf("ALL") }
    var pendingFavFixture by remember { mutableStateOf<FixtureItem?>(null) }
    var pendingAlarmFixture by remember { mutableStateOf<FixtureItem?>(null) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        pendingFavFixture?.let { viewModel.toggleMatchReminder(it) }
        pendingFavFixture = null
        pendingAlarmFixture?.let { viewModel.toggleKickoffAlarm(it) }
        pendingAlarmFixture = null
    }

    fun toggleFavoriteWithPerm(fixture: FixtureItem) {
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

    fun toggleKickoffWithPerm(fixture: FixtureItem) {
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

    val filters = listOf("ALL", "TEAMS", "MATCHES", "TIPS", "NEWS", "MEDIA", "COMMUNITY")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBlack)
            .navigationBarsPadding()
    ) {
        SubScreenTopBar(title = "Global Search", onBack = onBack)

        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.updateSearchQuery(it) },
            placeholder = { Text("Search teams, leagues, matches, news, tips, users...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = NeonLime) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = SlateText)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonLime,
                unfocusedBorderColor = MidnightBorder,
                focusedTextColor = CrispWhite,
                unfocusedTextColor = CrispWhite
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("global_search_input")
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filters) { f ->
                val selected = filter == f
                FilterChip(
                    selected = selected,
                    onClick = { filter = f },
                    label = { Text(f, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonLimeContainer,
                        selectedLabelColor = NeonLime,
                        containerColor = MidnightCard,
                        labelColor = SlateText
                    )
                )
            }
        }

        if (query.isBlank()) {
            Column(modifier = Modifier.padding(16.dp)) {
                EmptyStateCard(
                    icon = Icons.Filled.Search,
                    title = "Search Across DYFOOT",
                    subtitle = "Find football teams, competitions, live matches, H2H analysis, tips, news, videos, podcasts, users, and groups."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if ((filter == "ALL" || filter == "MATCHES") && results.matches.isNotEmpty()) {
                    item { Text("Matches (Tap for Visualizer, Squad XI & H2H)", style = MaterialTheme.typography.titleSmall, color = NeonLime) }
                    items(results.matches, key = { "m_${it.id}" }) { m ->
                        MatchCard(
                            fixture = m,
                            onClick = { viewModel.inspectFixture(m) },
                            modifier = Modifier.fillMaxWidth(),
                            isFavorite = m.id in matchReminders,
                            onToggleFavorite = { toggleFavoriteWithPerm(m) },
                            isReminded = m.id in kickoffAlarms,
                            onToggleReminder = { toggleKickoffWithPerm(m) }
                        )
                    }
                }
                if ((filter == "ALL" || filter == "TEAMS") && results.teamsAndCompetitions.isNotEmpty()) {
                    item { Text("Teams & Competitions", style = MaterialTheme.typography.titleSmall, color = NeonLime) }
                    items(results.teamsAndCompetitions) { name ->
                        SearchResultRow(title = name, subtitle = "Team / Competition", onClick = onNavigateLiveScores)
                    }
                }
                if ((filter == "ALL" || filter == "TIPS") && results.tips.isNotEmpty()) {
                    item { Text("Football Tips", style = MaterialTheme.typography.titleSmall, color = NeonLime) }
                    items(results.tips, key = { "t_${it.id}" }) { t ->
                        SearchResultRow(t.matchName, "${t.tier} • ${t.market} (${t.odds})", onNavigateTips)
                    }
                }
                if ((filter == "ALL" || filter == "NEWS") && results.news.isNotEmpty()) {
                    item { Text("News", style = MaterialTheme.typography.titleSmall, color = NeonLime) }
                    items(results.news, key = { "n_${it.id}" }) { n ->
                        SearchResultRow(n.title, "${n.sourceName} • ${n.category}", { onNavigateSportHub(0) })
                    }
                }
                if ((filter == "ALL" || filter == "MEDIA") && (results.videos.isNotEmpty() || results.podcasts.isNotEmpty())) {
                    item { Text("Watch & Podcasts", style = MaterialTheme.typography.titleSmall, color = NeonLime) }
                    items(results.videos, key = { "v_${it.id}" }) { v ->
                        SearchResultRow(v.title, "Watch • ${v.creatorName}", { onNavigateSportHub(1) })
                    }
                    items(results.podcasts, key = { "p_${it.id}" }) { p ->
                        SearchResultRow(p.title, "Podcast • ${p.showName}", { onNavigateSportHub(2) })
                    }
                }
                if ((filter == "ALL" || filter == "COMMUNITY") && (results.users.isNotEmpty() || results.groups.isNotEmpty())) {
                    item { Text("Users & Groups", style = MaterialTheme.typography.titleSmall, color = NeonLime) }
                    items(results.users, key = { "u_${it.userId}" }) { u ->
                        SearchResultRow(u.displayName, "@${u.username} • ${u.parsedRole().label}", onNavigateGroups)
                    }
                    items(results.groups, key = { "g_${it.id}" }) { g ->
                        SearchResultRow(g.title, g.description.ifBlank { "Community Group" }, onNavigateGroups)
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
                onNavigateTips()
            },
            onOpenLiveArena = {
                viewModel.clearInspectedFixture()
                onNavigateLiveScores()
            },
            matchBundle = inspectedBundle,
            isKickoffReminded = f.id in kickoffAlarms,
            onToggleKickoffReminder = { toggleKickoffWithPerm(f) },
            isSwahili = language == AppLanguage.SWAHILI
        )
    }
}

@Composable
private fun SearchResultRow(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightCard),
        border = BorderStroke(1.dp, MidnightBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = CrispWhite)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SlateText)
        }
    }
}
