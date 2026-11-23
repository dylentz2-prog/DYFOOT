package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Advertisement
import com.example.data.model.Announcement
import com.example.data.model.AppSettings
import com.example.data.model.FixtureItem
import com.example.data.model.FixtureStatus
import com.example.data.model.NewsArticle
import com.example.data.model.OFFICIAL_MERCHANT_NUMBER
import com.example.data.model.OFFICIAL_OWNER_EMAIL
import com.example.data.model.PaymentStatus
import com.example.data.model.PodcastEpisode
import com.example.data.model.RiskLevel
import com.example.data.model.TipItem
import com.example.data.model.TipStatus
import com.example.data.model.TipTier
import com.example.data.model.UserRole
import com.example.data.model.VideoItem
import com.example.ui.components.SectionHeader
import com.example.ui.components.SubScreenTopBar
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.InfoCyan
import com.example.ui.theme.LiveRed
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.MidnightCardAlt
import com.example.ui.theme.NeonLime
import com.example.ui.theme.NeonLimeContainer
import com.example.ui.theme.SlateText
import com.example.ui.theme.VipAmber
import com.example.ui.theme.VipAmberContainer
import com.example.ui.theme.WonEmerald
import com.example.ui.theme.WonEmeraldContainer
import com.example.viewmodel.DyfootViewModel

private enum class AdminSection(val label: String) {
    OVERVIEW("Overview"),
    TIPS("Tips"),
    NEWS("Local News"),
    MEDIA("Watch & Audio"),
    ADS_ALERTS("Ads & Alerts"),
    FIXTURES("Fixtures"),
    PAYMENTS("Payments"),
    USERS_MOD("Users & Reports"),
    SETTINGS("Settings")
}

@Composable
fun AdminStudioScreen(
    viewModel: DyfootViewModel,
    onBack: () -> Unit
) {
    val isAuthorized = viewModel.isAdminOrOwner()
    val isOwner = viewModel.isVerifiedOwner()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBlack)
            .navigationBarsPadding()
    ) {
        SubScreenTopBar(
            title = if (isOwner) "Owner Command Studio" else "Admin Studio",
            onBack = onBack
        )

        if (!isAuthorized) {
            BoxCenteredDenied(onBack = onBack)
            return
        }

        var selectedSection by remember { mutableStateOf(AdminSection.OVERVIEW) }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(AdminSection.entries) { section ->
                val selected = selectedSection == section
                FilterChip(
                    selected = selected,
                    onClick = { selectedSection = section },
                    label = { Text(section.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonLime,
                        selectedLabelColor = MidnightBlack,
                        containerColor = MidnightCard,
                        labelColor = SlateText
                    ),
                    modifier = Modifier.testTag("admin_tab_${section.name.lowercase()}")
                )
            }
        }

        when (selectedSection) {
            AdminSection.OVERVIEW -> AdminOverviewSection(
                viewModel = viewModel,
                isOwner = isOwner,
                onOpenLocalNews = { selectedSection = AdminSection.NEWS }
            )
            AdminSection.TIPS -> AdminTipsSection(viewModel = viewModel)
            AdminSection.NEWS -> AdminLocalNewsSection(viewModel = viewModel)
            AdminSection.MEDIA -> AdminMediaSection(viewModel = viewModel)
            AdminSection.ADS_ALERTS -> AdminAdsAndAlertsSection(viewModel = viewModel)
            AdminSection.FIXTURES -> AdminFixturesSection(viewModel = viewModel)
            AdminSection.PAYMENTS -> AdminPaymentsSection(viewModel = viewModel, isOwner = isOwner)
            AdminSection.USERS_MOD -> AdminUsersAndReportsSection(viewModel = viewModel, isOwner = isOwner)
            AdminSection.SETTINGS -> AdminSettingsSection(viewModel = viewModel)
        }
    }
}

@Composable
private fun BoxCenteredDenied(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = LiveRed, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text("Restricted Owner & Admin Area", style = MaterialTheme.typography.titleLarge, color = CrispWhite)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Only the verified DYFOOT owner ($OFFICIAL_OWNER_EMAIL) and owner-authorized admins may access Admin Studio. All writes are enforced by Firestore Security Rules.",
            style = MaterialTheme.typography.bodyMedium,
            color = SlateText
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = MidnightBlack)
        ) {
            Text("Return to App", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AdminOverviewSection(
    viewModel: DyfootViewModel,
    isOwner: Boolean,
    onOpenLocalNews: () -> Unit = {}
) {
    val tips by viewModel.adminTips.collectAsStateWithLifecycle()
    val news by viewModel.adminNews.collectAsStateWithLifecycle()
    val videos by viewModel.adminVideos.collectAsStateWithLifecycle()
    val podcasts by viewModel.adminPodcasts.collectAsStateWithLifecycle()
    val fixtures by viewModel.adminFixtures.collectAsStateWithLifecycle()
    val users by viewModel.adminUsers.collectAsStateWithLifecycle()
    val payments by viewModel.adminPayments.collectAsStateWithLifecycle()
    val reports by viewModel.adminReports.collectAsStateWithLifecycle()
    val logs by viewModel.adminLogs.collectAsStateWithLifecycle()

    val pendingPayments = payments.count { it.status == PaymentStatus.PENDING_VERIFICATION.name }
    val openReports = reports.count { it.status == "OPEN" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightCard),
                border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = NeonLime)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isOwner) "Verified Owner Authority Active" else "Authorized Admin Active",
                            style = MaterialTheme.typography.titleMedium,
                            color = CrispWhite
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Platform analytics & content totals reflect live Firestore records.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateText
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onOpenLocalNews,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = MidnightBlack),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_overview_local_news_btn")
                    ) {
                        Text("Open Bilingual Local Football News (EN/SW)", fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.refreshAllLiveContent() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_sync_live_feeds_btn")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, tint = NeonLime)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sync All Real-Time Feeds Now", color = NeonLime)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val dsActive = viewModel.hasDataSportsApiKey()
                    val lfaActive = viewModel.hasLiveFootballApiKey()
                    val fdActive = viewModel.hasFootballDataApiKey()
                    Text(
                        text = buildString {
                            append("Live APIs: ")
                            append(if (dsActive) "DataSportsAPI ✓" else "DataSportsAPI (No Key)")
                            append(" • ")
                            append(if (lfaActive) "Live Football API ✓" else "Live Football API (No Key)")
                            append(" • ")
                            append(if (fdActive) "football-data ✓" else "football-data (No Key)")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateText
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("Tips (${tips.size})", "${tips.count { it.tier == TipTier.VIP.name }} VIP", NeonLime, Modifier.weight(1f))
                StatTile("Users", "${users.size}", InfoCyan, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("Pending Pay", "$pendingPayments", VipAmber, Modifier.weight(1f))
                StatTile("Open Reports", "$openReports", LiveRed, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("News / Fixtures", "${news.size} / ${fixtures.size}", CrispWhite, Modifier.weight(1f))
                StatTile("Watch / Pods", "${videos.size} / ${podcasts.size}", CrispWhite, Modifier.weight(1f))
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            SectionHeader(title = "Immutable Admin Audit Logs (${logs.size})")
        }

        items(logs.take(25), key = { it.id }) { log ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MidnightCard,
                border = BorderStroke(1.dp, MidnightBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("${log.action} • ${log.targetType}", style = MaterialTheme.typography.titleSmall, color = NeonLime)
                    Text(log.details, style = MaterialTheme.typography.bodySmall, color = CrispWhite)
                    Text("Actor: ${log.actorEmail}", style = MaterialTheme.typography.labelSmall, color = SlateText)
                }
            }
        }
    }
}

@Composable
private fun StatTile(title: String, value: String, accent: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightCard),
        border = BorderStroke(1.dp, MidnightBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = SlateText)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, color = accent)
        }
    }
}

@Composable
private fun AdminTipsSection(viewModel: DyfootViewModel) {
    val tips by viewModel.adminTips.collectAsStateWithLifecycle()

    var editingId by remember { mutableStateOf("") }
    var editingAuthorId by remember { mutableStateOf("") }
    var homeTeam by remember { mutableStateOf("") }
    var awayTeam by remember { mutableStateOf("") }
    var competition by remember { mutableStateOf("Premier League") }
    var kickoffLabel by remember { mutableStateOf("Today 20:00 EAT") }
    var tier by remember { mutableStateOf(TipTier.FREE) }
    var market by remember { mutableStateOf("1X2 / Match Result") }
    var selection by remember { mutableStateOf("") }
    var odds by remember { mutableStateOf("1.85") }
    var bookmakerName by remember { mutableStateOf("SportPesa") }
    var bookmakerCode by remember { mutableStateOf("") }
    var predictedScore by remember { mutableStateOf("") }
    var analysis by remember { mutableStateOf("") }
    var riskLevel by remember { mutableStateOf(RiskLevel.MEDIUM) }
    var status by remember { mutableStateOf(TipStatus.PENDING) }
    var published by remember { mutableStateOf(true) }
    var featured by remember { mutableStateOf(true) }

    fun resetForm() {
        editingId = ""
        editingAuthorId = ""
        homeTeam = ""
        awayTeam = ""
        selection = ""
        bookmakerCode = ""
        predictedScore = ""
        analysis = ""
        status = TipStatus.PENDING
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightCard),
                border = BorderStroke(1.dp, MidnightBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (editingId.isBlank()) "Publish New Football Tip" else "Editing Tip",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonLime
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = homeTeam, onValueChange = { homeTeam = it }, label = { Text("Home Team") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = awayTeam, onValueChange = { awayTeam = it }, label = { Text("Away Team") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = competition, onValueChange = { competition = it }, label = { Text("Competition") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = kickoffLabel, onValueChange = { kickoffLabel = it }, label = { Text("Kickoff") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = tier == TipTier.FREE,
                            onClick = { tier = TipTier.FREE },
                            label = { Text("FREE") }
                        )
                        FilterChip(
                            selected = tier == TipTier.VIP,
                            onClick = { tier = TipTier.VIP },
                            label = { Text("VIP (Protected)") }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = market, onValueChange = { market = it }, label = { Text("Market") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = odds, onValueChange = { odds = it }, label = { Text("Odds") }, modifier = Modifier.weight(0.5f), singleLine = true)
                    }
                    OutlinedTextField(value = selection, onValueChange = { selection = it }, label = { Text("Selection (e.g. Over 2.5 Goals)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = bookmakerName, onValueChange = { bookmakerName = it }, label = { Text("Bookmaker") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = bookmakerCode, onValueChange = { bookmakerCode = it }, label = { Text("Slip Code") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    OutlinedTextField(value = predictedScore, onValueChange = { predictedScore = it }, label = { Text("Predicted Score (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = analysis, onValueChange = { analysis = it }, label = { Text("Tactical Analysis") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(RiskLevel.entries) { r ->
                            FilterChip(selected = riskLevel == r, onClick = { riskLevel = r }, label = { Text(r.label) })
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(TipStatus.entries) { s ->
                            FilterChip(selected = status == s, onClick = { status = s }, label = { Text(s.label) })
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Published", color = CrispWhite)
                        Switch(checked = published, onCheckedChange = { published = it })
                        Text("Featured", color = CrispWhite)
                        Switch(checked = featured, onCheckedChange = { featured = it })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (homeTeam.isBlank() || awayTeam.isBlank() || selection.isBlank()) return@Button
                                viewModel.saveTip(
                                    TipItem(
                                        id = editingId,
                                        authorId = editingAuthorId,
                                        matchName = "${homeTeam.trim()} vs ${awayTeam.trim()}",
                                        homeTeam = homeTeam.trim(),
                                        awayTeam = awayTeam.trim(),
                                        competition = competition.trim(),
                                        kickoffLabel = kickoffLabel.trim(),
                                        kickoffEpochMs = System.currentTimeMillis(),
                                        tier = tier.name,
                                        market = market.trim(),
                                        selection = selection.trim(),
                                        odds = odds.trim(),
                                        bookmakerName = bookmakerName.trim(),
                                        bookmakerCode = bookmakerCode.trim(),
                                        predictedScore = predictedScore.trim(),
                                        analysis = analysis.trim(),
                                        riskLevel = riskLevel.name,
                                        status = status.name,
                                        published = published,
                                        featured = featured
                                    )
                                ) { resetForm() }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = MidnightBlack),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (editingId.isBlank()) "Publish Tip" else "Update Tip", fontWeight = FontWeight.Bold)
                        }
                        if (editingId.isNotBlank()) {
                            OutlinedButton(onClick = { resetForm() }) { Text("Cancel") }
                        }
                    }
                }
            }
        }

        item { SectionHeader(title = "All Tips (${tips.size})") }

        items(tips, key = { it.id }) { tip ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightCard),
                border = BorderStroke(1.dp, MidnightBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${tip.tier} • ${tip.matchName}",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (tip.tier == TipTier.VIP.name) VipAmber else NeonLime,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            editingId = tip.id
                            editingAuthorId = tip.authorId
                            homeTeam = tip.homeTeam
                            awayTeam = tip.awayTeam
                            competition = tip.competition
                            kickoffLabel = tip.kickoffLabel
                            tier = TipTier.safeFrom(tip.tier)
                            market = tip.market
                            selection = tip.selection
                            odds = tip.odds
                            bookmakerName = tip.bookmakerName
                            bookmakerCode = tip.bookmakerCode
                            predictedScore = tip.predictedScore
                            analysis = tip.analysis
                            riskLevel = RiskLevel.safeFrom(tip.riskLevel)
                            status = TipStatus.safeFrom(tip.status)
                            published = tip.published
                            featured = tip.featured
                        }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit tip", tint = InfoCyan)
                        }
                        IconButton(onClick = { viewModel.deleteTip(tip.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete tip", tint = LiveRed)
                        }
                    }
                    Text("${tip.market} • Odds ${tip.odds} • Status: ${tip.status} • Published=${tip.published}", style = MaterialTheme.typography.bodySmall, color = SlateText)
                }
            }
        }
    }
}


@Composable
private fun AdminMediaSection(viewModel: DyfootViewModel) {
    val videos by viewModel.adminVideos.collectAsStateWithLifecycle()
    val podcasts by viewModel.adminPodcasts.collectAsStateWithLifecycle()
    var subMode by remember { mutableIntStateOf(0) } // 0 = Videos, 1 = Podcasts

    var vTitle by remember { mutableStateOf("") }
    var vCaption by remember { mutableStateOf("") }
    var vCategory by remember { mutableStateOf("Highlights") }
    var vUrl by remember { mutableStateOf("") }

    var pTitle by remember { mutableStateOf("") }
    var pShow by remember { mutableStateOf("DYFOOT Pitchside") }
    var pHost by remember { mutableStateOf("DYFOOT Host") }
    var pUrl by remember { mutableStateOf("") }
    var pDesc by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = subMode == 0, onClick = { subMode = 0 }, label = { Text("Watch Videos (${videos.size})") })
                FilterChip(selected = subMode == 1, onClick = { subMode = 1 }, label = { Text("Podcasts (${podcasts.size})") })
            }
        }

        if (subMode == 0) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MidnightCard),
                    border = BorderStroke(1.dp, MidnightBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Publish Watch Highlight / Short", style = MaterialTheme.typography.titleMedium, color = NeonLime)
                        OutlinedTextField(value = vTitle, onValueChange = { vTitle = it }, label = { Text("Video Title") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = vCaption, onValueChange = { vCaption = it }, label = { Text("Caption & Hashtags") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = vCategory, onValueChange = { vCategory = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = vUrl, onValueChange = { vUrl = it }, label = { Text("Video MP4/HLS Stream URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Button(
                            onClick = {
                                if (vTitle.isBlank() || vUrl.isBlank()) return@Button
                                viewModel.saveVideo(
                                    VideoItem(
                                        title = vTitle.trim(),
                                        caption = vCaption.trim(),
                                        creatorName = "DYFOOT TV",
                                        category = vCategory.trim().ifBlank { "Highlights" },
                                        videoUrl = vUrl.trim(),
                                        durationSec = 60L,
                                        published = true
                                    )
                                ) { vTitle = ""; vCaption = ""; vUrl = "" }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = MidnightBlack),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Publish Video", fontWeight = FontWeight.Bold) }
                    }
                }
            }
            items(videos, key = { it.id }) { vid ->
                Surface(shape = RoundedCornerShape(12.dp), color = MidnightCard, border = BorderStroke(1.dp, MidnightBorder), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(vid.title, style = MaterialTheme.typography.titleSmall, color = CrispWhite)
                            Text(vid.videoUrl, style = MaterialTheme.typography.bodySmall, color = SlateText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { viewModel.deleteVideo(vid.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete video", tint = LiveRed)
                        }
                    }
                }
            }
        } else {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MidnightCard),
                    border = BorderStroke(1.dp, MidnightBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Publish Podcast Episode", style = MaterialTheme.typography.titleMedium, color = VipAmber)
                        OutlinedTextField(value = pTitle, onValueChange = { pTitle = it }, label = { Text("Episode Title") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = pShow, onValueChange = { pShow = it }, label = { Text("Show Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = pHost, onValueChange = { pHost = it }, label = { Text("Host Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = pUrl, onValueChange = { pUrl = it }, label = { Text("Audio MP3/AAC URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = pDesc, onValueChange = { pDesc = it }, label = { Text("Episode Notes") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                        Button(
                            onClick = {
                                if (pTitle.isBlank() || pUrl.isBlank()) return@Button
                                viewModel.savePodcast(
                                    PodcastEpisode(
                                        title = pTitle.trim(),
                                        showName = pShow.trim().ifBlank { "DYFOOT Audio" },
                                        hostName = pHost.trim().ifBlank { "DYFOOT Host" },
                                        description = pDesc.trim(),
                                        audioUrl = pUrl.trim(),
                                        durationSec = 600L,
                                        published = true
                                    )
                                ) { pTitle = ""; pUrl = ""; pDesc = "" }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = VipAmber, contentColor = MidnightBlack),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Publish Podcast", fontWeight = FontWeight.Bold) }
                    }
                }
            }
            items(podcasts, key = { it.id }) { pod ->
                Surface(shape = RoundedCornerShape(12.dp), color = MidnightCard, border = BorderStroke(1.dp, MidnightBorder), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(pod.title, style = MaterialTheme.typography.titleSmall, color = CrispWhite)
                            Text("${pod.showName} • ${pod.hostName}", style = MaterialTheme.typography.bodySmall, color = SlateText)
                        }
                        IconButton(onClick = { viewModel.deletePodcast(pod.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete podcast", tint = LiveRed)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminFixturesSection(viewModel: DyfootViewModel) {
    val fixtures by viewModel.adminFixtures.collectAsStateWithLifecycle()
    var home by remember { mutableStateOf("") }
    var away by remember { mutableStateOf("") }
    var comp by remember { mutableStateOf("NBC Premier League") }
    var homeScore by remember { mutableStateOf("0") }
    var awayScore by remember { mutableStateOf("0") }
    var status by remember { mutableStateOf(FixtureStatus.UPCOMING) }
    var minuteLabel by remember { mutableStateOf("16:00 EAT") }
    var venue by remember { mutableStateOf("") }
    var eventsSummary by remember { mutableStateOf("") }
    var streamScheduled by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MidnightCard), border = BorderStroke(1.dp, MidnightBorder)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Publish Official Match / Live Arena Schedule", style = MaterialTheme.typography.titleMedium, color = NeonLime)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = home, onValueChange = { home = it }, label = { Text("Home") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = away, onValueChange = { away = it }, label = { Text("Away") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = comp, onValueChange = { comp = it }, label = { Text("Competition") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = minuteLabel, onValueChange = { minuteLabel = it }, label = { Text("Time / Min") }, modifier = Modifier.weight(0.7f), singleLine = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = homeScore, onValueChange = { homeScore = it }, label = { Text("Home Score") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = awayScore, onValueChange = { awayScore = it }, label = { Text("Away Score") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(FixtureStatus.entries) { st ->
                            FilterChip(selected = status == st, onClick = { status = st }, label = { Text(st.name) })
                        }
                    }
                    OutlinedTextField(value = venue, onValueChange = { venue = it }, label = { Text("Venue (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = eventsSummary, onValueChange = { eventsSummary = it }, label = { Text("Match Events / Lineups Summary") }, modifier = Modifier.fillMaxWidth(), maxLines = 2)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Schedule on Live Arena", color = CrispWhite, modifier = Modifier.weight(1f))
                        Switch(checked = streamScheduled, onCheckedChange = { streamScheduled = it })
                    }
                    Button(
                        onClick = {
                            if (home.isBlank() || away.isBlank()) return@Button
                            viewModel.saveFixture(
                                FixtureItem(
                                    homeTeam = home.trim(),
                                    awayTeam = away.trim(),
                                    competition = comp.trim(),
                                    homeScore = homeScore.toLongOrNull()?.coerceIn(0L, 99L) ?: 0L,
                                    awayScore = awayScore.toLongOrNull()?.coerceIn(0L, 99L) ?: 0L,
                                    status = status.name,
                                    minuteLabel = minuteLabel.trim(),
                                    kickoffEpochMs = System.currentTimeMillis(),
                                    venue = venue.trim(),
                                    sourceLabel = "DYFOOT Official",
                                    streamScheduled = streamScheduled,
                                    streamStatus = if (streamScheduled) "SCHEDULED" else "COMING_SOON",
                                    eventsSummary = eventsSummary.trim(),
                                    published = true
                                )
                            ) { home = ""; away = ""; eventsSummary = "" }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = MidnightBlack),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save Fixture", fontWeight = FontWeight.Bold) }
                }
            }
        }

        items(fixtures, key = { it.id }) { fix ->
            Surface(shape = RoundedCornerShape(12.dp), color = MidnightCard, border = BorderStroke(1.dp, MidnightBorder), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${fix.homeTeam} ${fix.homeScore}-${fix.awayScore} ${fix.awayTeam}", style = MaterialTheme.typography.titleSmall, color = CrispWhite)
                        Text("${fix.competition} • ${fix.status} (${fix.minuteLabel})", style = MaterialTheme.typography.bodySmall, color = SlateText)
                    }
                    IconButton(onClick = { viewModel.deleteFixture(fix.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete fixture", tint = LiveRed)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminPaymentsSection(viewModel: DyfootViewModel, isOwner: Boolean) {
    val payments by viewModel.adminPayments.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(title = "Tanzania Mobile Money Verification ($OFFICIAL_MERCHANT_NUMBER)")
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isOwner) "Verify confirmed M-Pesa / Tigo Pesa / Airtel Money / HaloPesa receipts to activate VIP access." else "Only the verified Owner account may list & approve global payments.",
                style = MaterialTheme.typography.bodySmall,
                color = SlateText
            )
        }

        items(payments, key = { it.id }) { p ->
            val pending = p.status == PaymentStatus.PENDING_VERIFICATION.name
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightCard),
                border = BorderStroke(1.dp, if (pending) VipAmber.copy(alpha = 0.6f) else MidnightBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${p.userDisplayName.ifBlank { p.userId }} • ${p.planName}", style = MaterialTheme.typography.titleSmall, color = CrispWhite)
                        Surface(
                            color = if (pending) VipAmberContainer else WonEmeraldContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = p.status,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (pending) VipAmber else WonEmerald,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${p.amountTzs} TZS via ${p.provider} • From ${p.payerPhone}", style = MaterialTheme.typography.bodySmall, color = SlateText)
                    Text("Ref: ${p.transactionRef}", style = MaterialTheme.typography.labelMedium, color = NeonLime)
                    if (pending && isOwner) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.verifyPayment(p, approve = true) },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = MidnightBlack),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("approve_payment_${p.id}")
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Approve & Activate VIP", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { viewModel.verifyPayment(p, approve = false) },
                                modifier = Modifier.testTag("reject_payment_${p.id}")
                            ) {
                                Text("Reject", color = LiveRed)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminUsersAndReportsSection(viewModel: DyfootViewModel, isOwner: Boolean) {
    val users by viewModel.adminUsers.collectAsStateWithLifecycle()
    val reports by viewModel.adminReports.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(title = "User Roles & Account Control (${users.size})")
            if (!isOwner) {
                Text("Only the verified Owner may promote/demote Admins.", style = MaterialTheme.typography.bodySmall, color = VipAmber)
            }
        }

        items(users, key = { it.userId }) { user ->
            val role = user.parsedRole()
            val isRowOwner = role == UserRole.OWNER
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightCard),
                border = BorderStroke(1.dp, MidnightBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.displayName, style = MaterialTheme.typography.titleSmall, color = CrispWhite)
                            Text("@${user.username} • ${role.label} • Suspended=${user.suspended}", style = MaterialTheme.typography.bodySmall, color = SlateText)
                        }
                    }
                    if (!isRowOwner) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (isOwner) {
                                item {
                                    SmallActionChip(
                                        label = if (role == UserRole.ADMIN) "Remove Admin" else "Make Admin",
                                        onClick = {
                                            viewModel.updateUserRole(
                                                user,
                                                if (role == UserRole.ADMIN) UserRole.FREE_MEMBER else UserRole.ADMIN,
                                                user.suspended
                                            )
                                        }
                                    )
                                }
                            }
                            item {
                                SmallActionChip(
                                    label = if (role == UserRole.VIP_MEMBER) "Set Free" else "Grant VIP",
                                    onClick = {
                                        viewModel.updateUserRole(
                                            user,
                                            if (role == UserRole.VIP_MEMBER) UserRole.FREE_MEMBER else UserRole.VIP_MEMBER,
                                            user.suspended
                                        )
                                    }
                                )
                            }
                            item {
                                SmallActionChip(
                                    label = if (user.suspended) "Restore" else "Suspend",
                                    isDanger = !user.suspended,
                                    onClick = { viewModel.updateUserRole(user, role, !user.suspended) }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isOwner) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                SectionHeader(title = "Reported Community Content (${reports.size})")
            }
            items(reports, key = { it.id }) { report ->
                Surface(shape = RoundedCornerShape(12.dp), color = MidnightCard, border = BorderStroke(1.dp, MidnightBorder), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${report.targetType} • ${report.status}", style = MaterialTheme.typography.titleSmall, color = VipAmber)
                            Text(report.reason, style = MaterialTheme.typography.bodySmall, color = CrispWhite)
                        }
                        if (report.status != "RESOLVED") {
                            TextButton(onClick = { viewModel.resolveReport(report.id, "RESOLVED") }) {
                                Text("Resolve", color = NeonLime)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallActionChip(
    label: String,
    isDanger: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isDanger) LiveRed.copy(alpha = 0.2f) else MidnightCardAlt,
        border = BorderStroke(1.dp, if (isDanger) LiveRed else MidnightBorder),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isDanger) LiveRed else NeonLime,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun AdminSettingsSection(viewModel: DyfootViewModel) {
    val current by viewModel.appSettings.collectAsStateWithLifecycle()

    var daily by remember { mutableStateOf(current.vipDailyTzs.toString()) }
    var weekly by remember { mutableStateOf(current.vipWeeklyTzs.toString()) }
    var monthly by remember { mutableStateOf(current.vipMonthlyTzs.toString()) }
    var annual by remember { mutableStateOf(current.vipAnnualTzs.toString()) }
    var merchantNum by remember { mutableStateOf(current.merchantNumber) }
    var merchantName by remember { mutableStateOf(current.merchantName) }
    var categoriesCsv by remember { mutableStateOf(current.categories.joinToString(", ")) }
    var liveBanner by remember { mutableStateOf(current.liveUpdateBanner) }
    var syncSec by remember { mutableStateOf(current.liveSyncIntervalSec.toString()) }

    LaunchedEffect(current) {
        daily = current.vipDailyTzs.toString()
        weekly = current.vipWeeklyTzs.toString()
        monthly = current.vipMonthlyTzs.toString()
        annual = current.vipAnnualTzs.toString()
        merchantNum = current.merchantNumber
        merchantName = current.merchantName
        categoriesCsv = current.categories.joinToString(", ")
        liveBanner = current.liveUpdateBanner
        syncSec = current.liveSyncIntervalSec.toString()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MidnightCard), border = BorderStroke(1.dp, MidnightBorder)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("VIP Pricing, Live Sync & Over-the-Air Updates", style = MaterialTheme.typography.titleMedium, color = NeonLime)
                    Text(
                        text = "Live Config Version: v${current.configVersion} • Changes push directly to all active devices without downloading a new APK.",
                        style = MaterialTheme.typography.labelSmall,
                        color = InfoCyan
                    )
                    OutlinedTextField(
                        value = liveBanner,
                        onValueChange = { liveBanner = it },
                        label = { Text("Live In-App Broadcast Banner (pushes without APK update)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_live_banner_input"),
                        maxLines = 2
                    )
                    OutlinedTextField(
                        value = syncSec,
                        onValueChange = { input -> syncSec = input.filter { it.isDigit() } },
                        label = { Text("Live Score Auto-Refresh Cadence (sec, 15–300)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_sync_interval_input"),
                        singleLine = true
                    )
                    OutlinedTextField(value = daily, onValueChange = { daily = it }, label = { Text("ROCK PACKAGE • Per Day (TSH)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = weekly, onValueChange = { weekly = it }, label = { Text("STAR PACKAGE • Per Week (TSH)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = monthly, onValueChange = { monthly = it }, label = { Text("PRO PACKAGE • Per Month (TSH)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = annual, onValueChange = { annual = it }, label = { Text("LEGEND PACKAGE • Per Year (TSH)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = merchantNum, onValueChange = { merchantNum = it }, label = { Text("Receiving Merchant Number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = merchantName, onValueChange = { merchantName = it }, label = { Text("Merchant Display Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = categoriesCsv, onValueChange = { categoriesCsv = it }, label = { Text("News Categories (comma-separated)") }, modifier = Modifier.fillMaxWidth(), maxLines = 2)
                    Button(
                        onClick = {
                            val cats = categoriesCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }.take(20)
                            val parsedSync = syncSec.toLongOrNull()?.coerceIn(15L, 300L) ?: current.liveSyncIntervalSec
                            viewModel.saveAppSettings(
                                AppSettings(
                                    vipDailyTzs = daily.toLongOrNull() ?: current.vipDailyTzs,
                                    vipWeeklyTzs = weekly.toLongOrNull() ?: current.vipWeeklyTzs,
                                    vipMonthlyTzs = monthly.toLongOrNull() ?: current.vipMonthlyTzs,
                                    vipQuarterlyTzs = current.vipQuarterlyTzs,
                                    vipAnnualTzs = annual.toLongOrNull() ?: current.vipAnnualTzs,
                                    merchantNumber = merchantNum.trim(),
                                    merchantName = merchantName.trim(),
                                    categories = cats,
                                    liveUpdateBanner = liveBanner.trim(),
                                    liveSyncIntervalSec = parsedSync,
                                    configVersion = current.configVersion + 1L
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = MidnightBlack),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_save_settings_btn")
                    ) { Text("Save & Push Live Update", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
