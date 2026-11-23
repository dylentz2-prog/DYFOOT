package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.FixtureStatus
import com.example.data.model.TipTier
import com.example.data.model.UiState
import com.example.data.model.hasBilingualContent
import com.example.data.model.localizedTitle
import com.example.data.model.supportsEnglish
import com.example.data.model.supportsSwahili
import com.example.ui.components.AdBannerCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.handleAdDestinationClick
import com.example.ui.components.HotTimetableCard
import com.example.ui.components.MatchCard
import com.example.ui.components.MatchCenterBottomSheet
import com.example.ui.components.OwnerSocialFooter
import com.example.ui.components.SectionHeader
import com.example.ui.components.TipSlipCard
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.dyfootStrings
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
import com.example.viewmodel.DyfootViewModel

private data class HeroPromoSlide(
    val badge: String,
    val secondaryBadge: String,
    val title: String,
    val subtitle: String,
    val ctaLabel: String,
    val imageRes: Int,
    val actionType: Int
)

@Composable
fun HomeScreen(
    viewModel: DyfootViewModel,
    onNavigateTips: () -> Unit,
    onNavigateVip: () -> Unit,
    onNavigateLiveScores: () -> Unit,
    onNavigateLiveArena: () -> Unit,
    onNavigateWatch: () -> Unit,
    onNavigateSportHub: (Int) -> Unit
) {
    val context = LocalContext.current
    val language by viewModel.appLanguage.collectAsStateWithLifecycle()
    val copy = remember(language) { dyfootStrings(language) }

    val ads by viewModel.activeAds.collectAsStateWithLifecycle()
    val announcements by viewModel.announcements.collectAsStateWithLifecycle()
    val fixtures by viewModel.combinedFixtures.collectAsStateWithLifecycle()
    val liveFeedStatus by viewModel.liveFeedStatus.collectAsStateWithLifecycle()
    val liveFeedUpdatedAt by viewModel.liveFeedUpdatedAt.collectAsStateWithLifecycle()
    val tipsState by viewModel.publishedTips.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val news by viewModel.combinedNews.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val matchReminders by viewModel.matchReminders.collectAsStateWithLifecycle()
    val kickoffAlarms by viewModel.kickoffAlarmIds.collectAsStateWithLifecycle()
    val matchPredictions by viewModel.matchPredictions.collectAsStateWithLifecycle()
    val inspectedFixture by viewModel.inspectedFixtureDetail.collectAsStateWithLifecycle()
    val inspectedBundle by viewModel.inspectedMatchBundle.collectAsStateWithLifecycle()
    val isLoadingMatchDetail by viewModel.isLoadingMatchDetail.collectAsStateWithLifecycle()

    var selectedSport by remember { mutableStateOf("All") }
    var pendingFavFixture by remember { mutableStateOf<com.example.data.model.FixtureItem?>(null) }
    var pendingAlarmFixture by remember { mutableStateOf<com.example.data.model.FixtureItem?>(null) }
    var homeReadingArticle by remember { mutableStateOf<com.example.data.model.NewsArticle?>(null) }
    var homeLocallyLikedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        pendingFavFixture?.let { viewModel.toggleMatchReminder(it) }
        pendingFavFixture = null
        pendingAlarmFixture?.let { viewModel.toggleKickoffAlarm(it) }
        pendingAlarmFixture = null
    }

    fun toggleFavoriteWithNotificationPerm(fixture: com.example.data.model.FixtureItem) {
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

    fun toggleKickoffWithNotificationPerm(fixture: com.example.data.model.FixtureItem) {
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

    val allTips = (tipsState as? UiState.Success)?.data.orEmpty()
    val freeTips = allTips.filter { it.tier == TipTier.FREE.name }.take(2)
    val vipTips = allTips.filter { it.tier == TipTier.VIP.name }
    val hasVip = viewModel.hasActiveVipAccess()
    val bookmarkedIds = bookmarks.map { it.contentId }.toSet()

    val topAds = ads.filter { it.placement == "HOME_TOP" || it.placement.isBlank() }
    val sportFilteredFixtures = remember(fixtures, selectedSport, matchReminders) {
        when (selectedSport) {
            "All" -> fixtures
            "Favorites" -> fixtures.filter { it.id in matchReminders }
            else -> fixtures.filter {
                it.sportCategory.equals(selectedSport, ignoreCase = true) ||
                    (selectedSport == "Football" && it.sportCategory.isBlank())
            }
        }
    }
    val liveMatches = sportFilteredFixtures.filter { it.status == FixtureStatus.LIVE.name }
    val upcomingMatches = sportFilteredFixtures.filter { it.status == FixtureStatus.UPCOMING.name }
    val finishedMatches = sportFilteredFixtures.filter { it.status == FixtureStatus.FINISHED.name }

    // Hot Matches Timetable: live & upcoming marquee games prioritized first
    val hotTimetableFixtures = remember(sportFilteredFixtures) {
        (sportFilteredFixtures.filter { it.status == FixtureStatus.LIVE.name } +
            sportFilteredFixtures.filter { it.status == FixtureStatus.UPCOMING.name && it.isHotMatch } +
            sportFilteredFixtures.filter { it.status == FixtureStatus.UPCOMING.name && !it.isHotMatch })
            .distinctBy { it.id }
            .take(8)
    }

    val heroSlides = remember(settings, copy) {
        listOf(
            HeroPromoSlide(
                badge = "VIP",
                secondaryBadge = "${"%,d".format(settings.vipDailyTzs)} TSH+",
                title = copy.heroVipTitle,
                subtitle = copy.heroVipSub,
                ctaLabel = copy.heroVipCta,
                imageRes = R.drawable.img_hero_stadium,
                actionType = 0
            ),
            HeroPromoSlide(
                badge = "LIVE CENTER",
                secondaryBadge = "WORLDWIDE",
                title = copy.heroLiveTitle,
                subtitle = copy.heroLiveSub,
                ctaLabel = copy.heroLiveCta,
                imageRes = R.drawable.img_hero_stadium,
                actionType = 1
            ),
            HeroPromoSlide(
                badge = "WATCH",
                secondaryBadge = "REELS & PODCASTS",
                title = copy.heroWatchTitle,
                subtitle = copy.heroWatchSub,
                ctaLabel = copy.heroWatchCta,
                imageRes = R.drawable.img_news_tactics,
                actionType = 2
            )
        )
    }
    val heroPagerState = rememberPagerState(pageCount = { heroSlides.size })
    val sportFilters = remember(copy, matchReminders) {
        listOf(
            "All" to copy.allSports,
            "Favorites" to "${copy.favoritesFilter} (${matchReminders.size})",
            "Football" to copy.football,
            "Basketball" to copy.basketball,
            "Boxing & MMA" to copy.boxingMma,
            "Tennis" to copy.tennis,
            "Motorsport" to copy.motorsport
        )
    }

    val isSw = language == AppLanguage.SWAHILI
    val swahiliStoriesCount = remember(news) {
        news.count { it.supportsSwahili() }
    }
    val localizedNews = remember(news, language) {
        val scoped = if (language == AppLanguage.SWAHILI) {
            news.filter { it.supportsSwahili() }
        } else {
            news.filter { it.supportsEnglish() }
        }
        scoped.ifEmpty { news }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBlack),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Brand Tagline + Live Pulse Strip
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = copy.tagline,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = NeonLime,
                    letterSpacing = 1.4.sp
                )

                val tickerText = when {
                    liveMatches.isNotEmpty() -> {
                        val top = liveMatches.first()
                        val livePrefix = if (language == AppLanguage.SWAHILI) "MUBASHARA" else "LIVE"
                        "$livePrefix ${top.minuteLabel}: ${top.homeTeam} ${top.homeScore}-${top.awayScore} ${top.awayTeam} (${top.competition})"
                    }
                    localizedNews.isNotEmpty() -> "${localizedNews.first().sourceName}: ${localizedNews.first().localizedTitle(isSw)}"
                    else -> liveFeedStatus ?: "DYFOOT Worldwide Sports Direct"
                }

                Surface(
                    onClick = onNavigateLiveScores,
                    shape = RoundedCornerShape(12.dp),
                    color = MidnightCard,
                    border = BorderStroke(1.dp, if (liveMatches.isNotEmpty()) LiveRed.copy(alpha = 0.5f) else MidnightBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (liveMatches.isNotEmpty()) LiveRed else NeonLime)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tickerText,
                            style = MaterialTheme.typography.labelMedium,
                            color = CrispWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Filled.Public, contentDescription = null, tint = NeonLime, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Announcements (when published in Admin Studio)
        if (announcements.isNotEmpty()) {
            item {
                val topAnnouncement = announcements.first()
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (topAnnouncement.priority == "URGENT") NeonLimeContainer else MidnightCard,
                    border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Campaign,
                            contentDescription = "Announcement",
                            tint = NeonLime,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(topAnnouncement.title, style = MaterialTheme.typography.titleSmall, color = CrispWhite)
                            Text(
                                text = topAnnouncement.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateText,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Sponsored Banners if published (HOME_TOP)
        if (topAds.isNotEmpty()) {
            if (topAds.size == 1) {
                val ad = topAds.first()
                item(key = "home_ad_${ad.id}") {
                    AdBannerCard(
                        ad = ad,
                        onClick = {
                            handleAdDestinationClick(
                                context = context,
                                destinationUrl = ad.destinationUrl,
                                onNavigateVip = onNavigateVip,
                                onNavigateTips = onNavigateTips,
                                onNavigateSportHub = onNavigateSportHub,
                                onNavigateLiveScores = onNavigateLiveScores,
                                onNavigateLiveArena = onNavigateLiveArena
                            )
                        }
                    )
                }
            } else {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(topAds, key = { it.id }) { ad ->
                            AdBannerCard(
                                ad = ad,
                                onClick = {
                                    handleAdDestinationClick(
                                        context = context,
                                        destinationUrl = ad.destinationUrl,
                                        onNavigateVip = onNavigateVip,
                                        onNavigateTips = onNavigateTips,
                                        onNavigateSportHub = onNavigateSportHub,
                                        onNavigateLiveScores = onNavigateLiveScores,
                                        onNavigateLiveArena = onNavigateLiveArena
                                    )
                                },
                                modifier = Modifier.width(320.dp)
                            )
                        }
                    }
                }
            }
        }

        // Swipeable Hero Carousel + Pagination Dots
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HorizontalPager(
                    state = heroPagerState,
                    pageSpacing = 12.dp,
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    val slide = heroSlides[page]
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, MidnightBorder),
                        colors = CardDefaults.cardColors(containerColor = MidnightCard),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Image(
                                painter = painterResource(id = slide.imageRes),
                                contentDescription = slide.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                MidnightBlack.copy(alpha = 0.15f),
                                                MidnightBlack.copy(alpha = 0.55f),
                                                MidnightBlack.copy(alpha = 0.94f)
                                            )
                                        )
                                    )
                            )

                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = MidnightBlack.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MidnightBorder)
                                ) {
                                    Text(
                                        text = slide.badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = CrispWhite,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                Surface(
                                    color = NeonLimeContainer.copy(alpha = 0.78f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.45f))
                                ) {
                                    Text(
                                        text = slide.secondaryBadge,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonLime,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(18.dp)
                            ) {
                                Text(
                                    text = slide.title,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = CrispWhite
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = slide.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CrispWhite.copy(alpha = 0.85f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        when (slide.actionType) {
                                            0 -> onNavigateVip()
                                            1 -> onNavigateLiveScores()
                                            else -> onNavigateWatch()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonLime,
                                        contentColor = MidnightBlack
                                    ),
                                    shape = RoundedCornerShape(22.dp),
                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                                    modifier = Modifier.testTag("hero_cta_$page")
                                ) {
                                    Text(slide.ctaLabel, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(heroSlides.size) { idx ->
                        val active = heroPagerState.currentPage == idx
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(if (active) 22.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (active) NeonLime else MidnightBorder)
                        )
                    }
                }
            }
        }

        // Bilingual Swahili & English Live Pulse Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightCard),
                border = BorderStroke(1.dp, InfoCyan.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Translate, contentDescription = null, tint = InfoCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = copy.bilingualBriefEyebrow,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = InfoCyan
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = NeonLimeContainer,
                            border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = if (language == AppLanguage.SWAHILI) "KISWAHILI • MENYU" else "ENGLISH • MENU",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = NeonLime,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Text(
                        text = copy.bilingualBriefTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = CrispWhite
                    )
                    Text(
                        text = if (language == AppLanguage.SWAHILI) {
                            "Mechi ${liveMatches.size} zinaendelea mubashara, mechi ${upcomingMatches.size} zinasubiri muda wa kuanza, na habari $swahiliStoriesCount za Kiswahili zipo tayari kwenye Kituo cha Habari. Gusa mechi yoyote kuona magoli, kadi, kona na umiliki wa mpira."
                        } else {
                            "${liveMatches.size} matches are live now, ${upcomingMatches.size} upcoming on the worldwide timetable, plus $swahiliStoriesCount Swahili news updates ready in Sport Hub. Tap any match for goals, cards, corners & possession."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateText
                    )
                }
            }
        }

        // Worldwide Sport Filter Pills
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(sportFilters) { (sportKey, sportLabel) ->
                    val active = selectedSport == sportKey
                    Surface(
                        onClick = { selectedSport = sportKey },
                        shape = RoundedCornerShape(22.dp),
                        color = if (active) NeonLime else MidnightCard,
                        border = BorderStroke(1.dp, if (active) NeonLime else MidnightBorder),
                        modifier = Modifier.testTag("sport_filter_${sportKey.lowercase()}")
                    ) {
                        Text(
                            text = sportLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (active) MidnightBlack else SlateText,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }

        // MATCHDAY -> Fixtures & scores
        item {
            SectionHeader(
                eyebrow = copy.matchdayEyebrow,
                title = copy.fixturesAndScores,
                actionLabel = copy.seeAll,
                onActionClick = onNavigateLiveScores
            )
            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                onClick = { viewModel.syncLiveFootballScores() },
                shape = RoundedCornerShape(14.dp),
                color = MidnightCard,
                border = BorderStroke(1.dp, MidnightBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = NeonLime,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = buildString {
                            val defaultSync = if (language == AppLanguage.SWAHILI) {
                                "Mtandao wa Michezo Duniani umesawazishwa"
                            } else {
                                "Worldwide multi-sport feed synced"
                            }
                            append(liveFeedStatus ?: defaultSync)
                            if (!liveFeedUpdatedAt.isNullOrBlank()) append(" • $liveFeedUpdatedAt")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateText,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = copy.refresh,
                        tint = NeonLime,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val displayFixtures = (liveMatches + upcomingMatches + finishedMatches).take(10)
            if (displayFixtures.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Filled.SportsSoccer,
                    title = if (language == AppLanguage.SWAHILI) "Hakuna mechi za $selectedSport kwa sasa" else "No $selectedSport Matches Today",
                    subtitle = if (language == AppLanguage.SWAHILI) {
                        "Fungua Matokeo Mubashara kubadilisha siku au michezo mingine."
                    } else {
                        "Tap See all -> Live Scores to switch date, league, or worldwide sport."
                    }
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(displayFixtures, key = { it.id }) { fixture ->
                        MatchCard(
                            fixture = fixture,
                            onClick = { viewModel.inspectFixture(fixture) },
                            modifier = Modifier.width(265.dp),
                            isFavorite = fixture.id in matchReminders,
                            onToggleFavorite = { toggleFavoriteWithNotificationPerm(fixture) },
                            isReminded = fixture.id in kickoffAlarms,
                            onToggleReminder = { toggleKickoffWithNotificationPerm(fixture) }
                        )
                    }
                }
            }
        }

        // TODAY -> Free tips
        item {
            SectionHeader(
                eyebrow = copy.freeTipsEyebrow,
                title = copy.freeTipsTitle,
                actionLabel = copy.seeAll,
                onActionClick = onNavigateTips
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (freeTips.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Filled.ConfirmationNumber,
                    title = if (language == AppLanguage.SWAHILI) "Hakuna vidokezo vya bure bado" else "No Free Tips Yet",
                    subtitle = if (language == AppLanguage.SWAHILI) "Vidokezo rasmi vilivyotumwa na mmiliki vitaonekana hapa." else "Official free football slips published by the Owner will appear here."
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    freeTips.forEach { tip ->
                        TipSlipCard(
                            tip = tip,
                            vipSecret = null,
                            hasVipAccess = hasVip,
                            isBookmarked = tip.id in bookmarkedIds,
                            onBookmarkToggle = {
                                viewModel.toggleBookmark("TIP", tip.id, tip.matchName, "${tip.market} • ${tip.odds}")
                            },
                            onUpgradeVipClick = onNavigateVip,
                            context = context
                        )
                    }
                }
            }
        }

        // VIP preview warm dark-amber card
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF14130E)),
                border = BorderStroke(1.dp, VipAmber.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF1C180D),
                                    Color(0xFF0E131F)
                                )
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.WorkspacePremium,
                                    contentDescription = null,
                                    tint = VipAmber,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = copy.vipPreviewTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = CrispWhite
                                )
                            }
                            Surface(
                                color = VipAmberContainer,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, VipAmber.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = if (hasVip) {
                                        if (language == AppLanguage.SWAHILI) "VIP HAI" else "ACTIVE VIP"
                                    } else {
                                        "ROCK • STAR • PRO"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = VipAmber,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Text(
                            text = if (language == AppLanguage.SWAHILI) {
                                "Vidokezo ${vipTips.size} vya VIP vikiwa na uchambuzi na kodi za makampuni. Kuanzia ${"%,d".format(settings.vipDailyTzs)} TSH kwa siku."
                            } else {
                                "${vipTips.size} VIP tips published with premium analysis and bookmaker slip codes. Plans from ${"%,d".format(settings.vipDailyTzs)} TSH/day."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = SlateText
                        )

                        if (vipTips.isNotEmpty()) {
                            vipTips.take(2).forEach { vt ->
                                Surface(
                                    color = Color(0xFF0B101B),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, MidnightBorder.copy(alpha = 0.7f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = vt.matchName.replace("[SAMPLE]", "").trim(),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = CrispWhite,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = vt.competition,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = SlateText,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        } else {
                            Surface(
                                color = Color(0xFF0B101B),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MidnightBorder.copy(alpha = 0.7f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "ROCK ${"%,d".format(settings.vipDailyTzs)} • STAR ${"%,d".format(settings.vipWeeklyTzs)} • PRO ${"%,d".format(settings.vipMonthlyTzs)} • LEGEND ${"%,d".format(settings.vipAnnualTzs)} TSH",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = VipAmber,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        Button(
                            onClick = if (hasVip) onNavigateTips else onNavigateVip,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VipAmber,
                                contentColor = MidnightBlack
                            ),
                            shape = RoundedCornerShape(22.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                            modifier = Modifier.testTag("vip_preview_cta_btn")
                        ) {
                            Text(
                                text = if (hasVip) {
                                    if (language == AppLanguage.SWAHILI) "Fungua Mikeka ya VIP" else "Open VIP Slips"
                                } else {
                                    copy.heroVipTitle
                                },
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }

        // HOT MATCHES TIMETABLE (Big fixtures schedule — horizontal sliding pager under VIP preview)
        item {
            SectionHeader(
                eyebrow = copy.hotTimetableEyebrow,
                title = copy.hotTimetableTitle,
                actionLabel = copy.seeAll,
                onActionClick = onNavigateLiveScores
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = copy.hotTimetableSub,
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateText,
                    modifier = Modifier.weight(1f)
                )
                if (matchReminders.isNotEmpty()) {
                    Surface(color = NeonLimeContainer, shape = RoundedCornerShape(10.dp)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = NeonLime, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${copy.myWatchlist}: ${matchReminders.size}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = NeonLime
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            if (hotTimetableFixtures.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Filled.SportsSoccer,
                    title = if (language == AppLanguage.SWAHILI) "Ratiba inasasishwa" else "Syncing Hot Timetable",
                    subtitle = if (language == AppLanguage.SWAHILI) "Gusa Sasisha kupata ratiba ya mechi kubwa." else "Tap refresh above to pull upcoming global kickoffs."
                )
            } else {
                androidx.compose.runtime.key(selectedSport, hotTimetableFixtures.size) {
                    val timetablePagerState = rememberPagerState(pageCount = { hotTimetableFixtures.size })
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        HorizontalPager(
                            state = timetablePagerState,
                            contentPadding = PaddingValues(end = 28.dp),
                            pageSpacing = 12.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("hot_timetable_slider")
                        ) { page ->
                            val fixture = hotTimetableFixtures[page]
                            HotTimetableCard(
                                fixture = fixture,
                                isReminded = fixture.id in matchReminders,
                                userPick = matchPredictions[fixture.id],
                                copy = copy,
                                onToggleReminder = { toggleFavoriteWithNotificationPerm(fixture) },
                                onClick = { viewModel.inspectFixture(fixture) },
                                isKickoffReminded = fixture.id in kickoffAlarms,
                                onToggleKickoffReminder = { toggleKickoffWithNotificationPerm(fixture) }
                            )
                        }
                        if (hotTimetableFixtures.size > 1) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                repeat(hotTimetableFixtures.size) { idx ->
                                    val active = timetablePagerState.currentPage == idx
                                    Box(
                                        modifier = Modifier
                                            .height(6.dp)
                                            .width(if (active) 20.dp else 6.dp)
                                            .clip(CircleShape)
                                            .background(if (active) NeonLime else MidnightBorder)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // SPORT HUB -> Latest stories 2-Column Visual Grid
        item {
            SectionHeader(
                eyebrow = copy.sportHubEyebrow,
                title = copy.latestStoriesTitle,
                actionLabel = copy.seeAll,
                onActionClick = { onNavigateSportHub(0) }
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (localizedNews.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Filled.Campaign,
                    title = if (language == AppLanguage.SWAHILI) "Inapakia Habari..." else "Loading Headlines",
                    subtitle = if (language == AppLanguage.SWAHILI) {
                        "Inapakia habari za Ligi Kuu Tanzania, Afrika na ulimwengu mzima ndani ya app."
                    } else {
                        "Curating global football and multi-sport updates directly inside DYFOOT."
                    }
                )
            } else {
                val stories = localizedNews.take(4)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    stories.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            pair.forEach { article ->
                                Card(
                                    onClick = { homeReadingArticle = article },
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MidnightCard),
                                    border = BorderStroke(1.dp, MidnightBorder),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column {
                                        if (article.imageUrl.startsWith("http")) {
                                            AsyncImage(
                                                model = article.imageUrl,
                                                contentDescription = article.localizedTitle(isSw),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(1.35f),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Image(
                                                painter = painterResource(id = R.drawable.img_news_tactics),
                                                contentDescription = article.localizedTitle(isSw),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(1.35f),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "${article.language} • ${article.leagueTag.ifBlank { article.category.replace("_", " ") }}".uppercase(),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = NeonLime,
                                                maxLines = 1
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = article.localizedTitle(isSw),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = CrispWhite,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // Official Owner Social Media Links at the end of HomeScreen
        item {
            OwnerSocialFooter(copy = copy)
        }
    }

    // Rich Interactive Match Center Bottom Sheet (Visualizer, Squad XI, H2H, Possession, Corners, Cards, Predictor)
    inspectedFixture?.let { f ->
        MatchCenterBottomSheet(
            fixture = f,
            isLoadingDetail = isLoadingMatchDetail,
            isReminded = f.id in matchReminders,
            userPrediction = matchPredictions[f.id],
            copy = copy,
            onDismiss = { viewModel.clearInspectedFixture() },
            onToggleReminder = { toggleFavoriteWithNotificationPerm(f) },
            onSelectPrediction = { pick -> viewModel.setMatchPrediction(f.id, pick) },
            onOpenTips = {
                viewModel.clearInspectedFixture()
                onNavigateTips()
            },
            onOpenLiveArena = {
                viewModel.clearInspectedFixture()
                onNavigateLiveArena()
            },
            matchBundle = inspectedBundle,
            isKickoffReminded = f.id in kickoffAlarms,
            onToggleKickoffReminder = { toggleKickoffWithNotificationPerm(f) },
            isSwahili = language == AppLanguage.SWAHILI
        )
    }

    homeReadingArticle?.let { article ->
        val isLiked = article.id in homeLocallyLikedIds
        val readerSw = if (article.hasBilingualContent()) {
            isSw
        } else {
            (isSw && article.supportsSwahili()) || article.language.equals("SW", ignoreCase = true)
        }
        InAppArticleReaderSheet(
            article = article,
            isSwahili = readerSw,
            isLiked = isLiked,
            isBookmarked = article.id in bookmarkedIds,
            onToggleLike = {
                val nowLiked = article.id !in homeLocallyLikedIds
                homeLocallyLikedIds = if (nowLiked) homeLocallyLikedIds + article.id else homeLocallyLikedIds - article.id
                if (nowLiked && !article.isExternalRss) viewModel.likeNews(article.id)
            },
            onToggleBookmark = {
                viewModel.toggleBookmark("NEWS", article.id, article.localizedTitle(readerSw), article.sourceName)
            },
            onDismiss = { homeReadingArticle = null }
        )
    }
}
