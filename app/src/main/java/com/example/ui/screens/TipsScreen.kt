package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.TipItem
import com.example.data.model.TipStatus
import com.example.data.model.TipTier
import com.example.data.model.UiState
import com.example.ui.components.AdBannerCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SegmentedPillSwitcher
import com.example.ui.components.TipSlipCard
import com.example.ui.components.handleAdDestinationClick
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.InfoCyan
import com.example.ui.theme.LiveRed
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.MidnightCardAlt
import com.example.ui.theme.MidnightElevated
import com.example.ui.theme.NeonLime
import com.example.ui.theme.NeonLimeContainer
import com.example.ui.theme.SlateText
import com.example.ui.theme.VipAmber
import com.example.ui.theme.VipAmberContainer
import com.example.ui.theme.WonEmerald
import com.example.ui.i18n.dyfootStrings
import com.example.viewmodel.DyfootViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TipsScreen(
    viewModel: DyfootViewModel,
    onUpgradeVipClick: () -> Unit,
    onNavigateSportHub: (Int) -> Unit = {},
    onNavigateLiveScores: () -> Unit = {},
    onNavigateLiveArena: () -> Unit = {}
) {
    val context = LocalContext.current
    val tipsState by viewModel.publishedTips.collectAsStateWithLifecycle()
    val vipSecrets by viewModel.vipSecrets.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val ads by viewModel.activeAds.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val isPullRefreshing by viewModel.isPullRefreshing.collectAsStateWithLifecycle()
    val language by viewModel.appLanguage.collectAsStateWithLifecycle()
    val copy = remember(language) { dyfootStrings(language) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = FREE, 1 = VIP
    var statusFilter by remember { mutableStateOf("ALL") }
    var comboTipIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showComboSheet by remember { mutableStateOf(false) }
    var stakeInput by remember { mutableStateOf("5000") }

    val hasVip = viewModel.hasActiveVipAccess()
    val bookmarkedIds = bookmarks.map { it.contentId }.toSet()
    val tipsBannerAds = ads.filter { it.placement == "TIPS_BANNER" }
    val allTips = (tipsState as? UiState.Success)?.data.orEmpty()
    val comboTips = remember(allTips, comboTipIds) { allTips.filter { it.id in comboTipIds } }
    val combinedOdds = remember(comboTips) {
        if (comboTips.isEmpty()) 0.0
        else comboTips.fold(1.0) { acc, t -> acc * (t.odds.toDoubleOrNull()?.coerceAtLeast(1.0) ?: 1.0) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Matching Screenshot 4
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    text = copy.tipsHeader,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = CrispWhite
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = copy.tipsSub,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SlateText
                )
                Spacer(modifier = Modifier.height(14.dp))

                SegmentedPillSwitcher(
                    options = listOf(
                        "FREE" to null,
                        "VIP" to Icons.Filled.WorkspacePremium
                    ),
                    selectedIndex = selectedTab,
                    onSelect = { selectedTab = it }
                )
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf("ALL") + TipStatus.entries.map { it.name }
                items(filters) { f ->
                    val selected = statusFilter == f
                    FilterChip(
                        selected = selected,
                        onClick = { statusFilter = f },
                        label = { Text(f, style = MaterialTheme.typography.labelSmall) },
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
                    .testTag("tips_pull_refresh")
            ) {
                when (val state = tipsState) {
                    is UiState.Loading -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = NeonLime)
                        }
                    }

                    is UiState.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            Text(
                                text = state.message,
                                color = LiveRed
                            )
                        }
                    }

                is UiState.Success -> {
                    val targetTier = if (selectedTab == 0) TipTier.FREE.name else TipTier.VIP.name
                    val tierTips = state.data.filter { it.tier == targetTier }
                    val filtered = tierTips.filter { tip ->
                        statusFilter == "ALL" || tip.status == statusFilter
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 6.dp,
                            bottom = if (comboTips.isNotEmpty()) 170.dp else 112.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (tierTips.isNotEmpty()) {
                            item {
                                TipsterTrackRecordCard(
                                    tierLabel = if (selectedTab == 0) "Free Tips" else "VIP Slips",
                                    tips = tierTips
                                )
                            }
                        }

                        if (selectedTab == 1 && !hasVip) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = VipAmberContainer.copy(alpha = 0.55f)),
                                    border = BorderStroke(1.dp, VipAmber),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.WorkspacePremium,
                                            contentDescription = null,
                                            tint = VipAmber
                                        )
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 12.dp)
                                        ) {
                                            Text(
                                                text = "VIP Starts at ${"%,d".format(settings.vipDailyTzs)} TSH/Day",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = CrispWhite
                                            )
                                            Text(
                                                text = "ROCK (1k/day) • STAR (5k/wk) • PRO (15k/mo) • LEGEND (80k/yr)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = SlateText
                                            )
                                        }
                                        Button(
                                            onClick = onUpgradeVipClick,
                                            colors = ButtonDefaults.buttonColors(containerColor = VipAmber, contentColor = MidnightBlack),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.testTag("tips_upgrade_vip_btn")
                                        ) {
                                            Text("Packages", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        if (tipsBannerAds.isNotEmpty()) {
                            if (tipsBannerAds.size == 1) {
                                val ad = tipsBannerAds.first()
                                item(key = "tips_ad_${ad.id}") {
                                    AdBannerCard(
                                        ad = ad,
                                        onClick = {
                                            if (ad.destinationUrl.isBlank()) {
                                                onUpgradeVipClick()
                                            } else {
                                                handleAdDestinationClick(
                                                    context = context,
                                                    destinationUrl = ad.destinationUrl,
                                                    onNavigateVip = onUpgradeVipClick,
                                                    onNavigateSportHub = onNavigateSportHub,
                                                    onNavigateLiveScores = onNavigateLiveScores,
                                                    onNavigateLiveArena = onNavigateLiveArena
                                                )
                                            }
                                        }
                                    )
                                }
                            } else {
                                item {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        items(tipsBannerAds, key = { it.id }) { ad ->
                                            AdBannerCard(
                                                ad = ad,
                                                onClick = {
                                                    if (ad.destinationUrl.isBlank()) {
                                                        onUpgradeVipClick()
                                                    } else {
                                                        handleAdDestinationClick(
                                                            context = context,
                                                            destinationUrl = ad.destinationUrl,
                                                            onNavigateVip = onUpgradeVipClick,
                                                            onNavigateSportHub = onNavigateSportHub,
                                                            onNavigateLiveScores = onNavigateLiveScores,
                                                            onNavigateLiveArena = onNavigateLiveArena
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.width(320.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (filtered.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Filled.ConfirmationNumber,
                                    title = if (selectedTab == 0) "No Free Tips Available" else "No VIP Tips Available",
                                    subtitle = "Official betting slips published from Admin Studio will appear here immediately."
                                )
                            }
                        } else {
                            items(filtered, key = { it.id }) { tip ->
                                TipSlipCard(
                                    tip = tip,
                                    vipSecret = vipSecrets[tip.id],
                                    hasVipAccess = hasVip,
                                    isBookmarked = tip.id in bookmarkedIds,
                                    onBookmarkToggle = {
                                        viewModel.toggleBookmark("TIP", tip.id, tip.matchName, "${tip.market} • ${tip.odds}")
                                    },
                                    onUpgradeVipClick = onUpgradeVipClick,
                                    context = context,
                                    isInCombo = tip.id in comboTipIds,
                                    onToggleCombo = {
                                        comboTipIds = if (tip.id in comboTipIds) {
                                            comboTipIds - tip.id
                                        } else {
                                            comboTipIds + tip.id
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            }
        }

        if (comboTips.isNotEmpty()) {
            Surface(
                onClick = { showComboSheet = true },
                shape = RoundedCornerShape(18.dp),
                color = NeonLime,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 96.dp)
                    .testTag("combo_slip_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Layers, contentDescription = null, tint = MidnightBlack)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "COMBO SLIP (${comboTips.size} ${if (comboTips.size == 1) "Pick" else "Picks"})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MidnightBlack
                            )
                            Text(
                                text = "Combined Odds: ${String.format(Locale.US, "%.2f", combinedOdds)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MidnightBlack.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Surface(color = MidnightBlack, shape = RoundedCornerShape(10.dp)) {
                        Text(
                            text = "Open Calculator",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = NeonLime,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }

    if (showComboSheet && comboTips.isNotEmpty()) {
        val stakeTzs = stakeInput.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        val potentialReturnTzs = (stakeTzs * combinedOdds).toLong()

        ModalBottomSheet(
            onDismissRequest = { showComboSheet = false },
            containerColor = MidnightElevated
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Calculate, contentDescription = null, tint = NeonLime)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("DYFOOT Combo Slip Builder", style = MaterialTheme.typography.titleLarge, color = CrispWhite)
                    }
                    IconButton(onClick = {
                        comboTipIds = emptySet()
                        showComboSheet = false
                    }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear combo", tint = LiveRed)
                    }
                }

                comboTips.forEach { t ->
                    val sel = if (t.tier == TipTier.VIP.name) vipSecrets[t.id]?.selection ?: t.market else t.selection
                    Surface(
                        color = MidnightCard,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MidnightBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(t.matchName, style = MaterialTheme.typography.titleSmall, color = CrispWhite)
                                Text("${t.market}: $sel", style = MaterialTheme.typography.bodySmall, color = NeonLime)
                            }
                            Surface(color = NeonLimeContainer, shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    text = t.odds,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonLime,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MidnightBorder)

                OutlinedTextField(
                    value = stakeInput,
                    onValueChange = { stakeInput = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Stake Amount (TSH)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonLime,
                        unfocusedBorderColor = MidnightBorder,
                        focusedTextColor = CrispWhite,
                        unfocusedTextColor = CrispWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(2000L, 5000L, 10000L, 25000L).forEach { preset ->
                        OutlinedButton(
                            onClick = { stakeInput = preset.toString() },
                            border = BorderStroke(1.dp, MidnightBorder),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text("${preset / 1000}k", style = MaterialTheme.typography.labelSmall, color = CrispWhite)
                        }
                    }
                }

                Surface(
                    color = MidnightCardAlt,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Combined Odds", style = MaterialTheme.typography.labelSmall, color = SlateText)
                            Text(
                                text = String.format(Locale.US, "%.2f", combinedOdds),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = InfoCyan
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Potential Payout", style = MaterialTheme.typography.labelSmall, color = SlateText)
                            Text(
                                text = "${"%,d".format(potentialReturnTzs)} TSH",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = NeonLime
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        val slipText = buildString {
                            append("DYFOOT COMBO SLIP (${comboTips.size} Picks)\n")
                            comboTips.forEachIndexed { idx, t ->
                                val sel = if (t.tier == TipTier.VIP.name) vipSecrets[t.id]?.selection ?: t.market else t.selection
                                append("${idx + 1}. ${t.matchName} -> $sel (${t.odds})\n")
                            }
                            append("Combined Odds: ${String.format(Locale.US, "%.2f", combinedOdds)}\n")
                            append("Stake: ${"%,d".format(stakeTzs)} TSH -> Est. Payout: ${"%,d".format(potentialReturnTzs)} TSH")
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, slipText)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share DYFOOT Combo Slip"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = MidnightBlack),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share Combo Slip", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun TipsterTrackRecordCard(
    tierLabel: String,
    tips: List<TipItem>
) {
    val won = tips.count { it.status == TipStatus.WON.name }
    val lost = tips.count { it.status == TipStatus.LOST.name }
    val pending = tips.count { it.status == TipStatus.PENDING.name }
    val settled = won + lost
    val winRate = if (settled > 0) "${(won * 100) / settled}%" else "--"
    val numericOdds = tips.mapNotNull { it.odds.toDoubleOrNull() }
    val avgOdds = if (numericOdds.isNotEmpty()) String.format(Locale.US, "%.2f", numericOdds.average()) else "--"

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightCardAlt),
        border = BorderStroke(1.dp, MidnightBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = NeonLime, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$tierLabel Track Record (Live)",
                    style = MaterialTheme.typography.titleSmall,
                    color = CrispWhite
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TrackMetric("Win Rate", winRate, WonEmerald)
                TrackMetric("Won / Settled", "$won / $settled", CrispWhite)
                TrackMetric("Avg Odds", avgOdds, InfoCyan)
                TrackMetric("Active", "$pending", VipAmber)
            }
        }
    }
}

@Composable
private fun TrackMetric(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = SlateText)
    }
}
