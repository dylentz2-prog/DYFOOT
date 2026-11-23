package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.NewsArticle
import com.example.data.model.hasBilingualContent
import com.example.data.model.localizedContent
import com.example.data.model.localizedSummary
import com.example.data.model.localizedTitle
import com.example.data.model.supportsEnglish
import com.example.data.model.supportsSwahili
import com.example.ui.components.AdBannerCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.handleAdDestinationClick
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.LiveRed
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.MidnightCardAlt
import com.example.ui.theme.MidnightElevated
import com.example.ui.theme.NeonLime
import com.example.ui.theme.SlateText
import com.example.ui.theme.VipAmber
import com.example.ui.theme.VipAmberContainer
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.NeonLimeContainer
import com.example.ui.i18n.AppLanguage
import com.example.viewmodel.DyfootViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsFeedTab(
    viewModel: DyfootViewModel,
    onNavigateVip: () -> Unit = {},
    onNavigateTips: () -> Unit = {},
    onNavigateLiveScores: () -> Unit = {},
    onNavigateLiveArena: () -> Unit = {},
    onNavigatePodcasts: () -> Unit = {}
) {
    val context = LocalContext.current
    val news by viewModel.combinedNews.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val ads by viewModel.activeAds.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()

    var selectedCategory by remember { mutableStateOf("ALL") }
    var selectedLangFilter by remember {
        mutableStateOf(if (appLanguage == AppLanguage.SWAHILI) "SW" else "EN")
    } // ALL, SW, EN
    LaunchedEffect(appLanguage) {
        selectedLangFilter = if (appLanguage == AppLanguage.SWAHILI) "SW" else "EN"
    }
    var newsSearchQuery by remember { mutableStateOf("") }
    var readingArticle by remember { mutableStateOf<NewsArticle?>(null) }
    var commentingArticle by remember { mutableStateOf<NewsArticle?>(null) }
    var commentDraft by remember { mutableStateOf("") }
    var locallyLikedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val categories = remember(settings, news) {
        (listOf("ALL", "TANZANIA_LIGI", "TANZANIA_SOCIAL", "AFRICA_CAF", "SWAHILI") +
            settings.categories +
            news.map { it.category }.filter { it.isNotBlank() })
            .distinct()
    }
    val langScopedNews = remember(news, selectedLangFilter) {
        val scoped = when (selectedLangFilter) {
            "SW" -> news.filter { it.supportsSwahili() }
            "EN" -> news.filter { it.supportsEnglish() }
            else -> news
        }
        scoped.ifEmpty { news }
    }
    val filtered = remember(news, selectedCategory, selectedLangFilter, newsSearchQuery) {
        val q = newsSearchQuery.trim()
        news.filter { article ->
            val catMatch = selectedCategory == "ALL" || article.category.equals(selectedCategory, ignoreCase = true)
            val langMatch = when (selectedLangFilter) {
                "SW" -> article.supportsSwahili()
                "EN" -> article.supportsEnglish()
                else -> true
            }
            val queryMatch = q.isBlank() ||
                article.title.contains(q, ignoreCase = true) ||
                article.titleSw.contains(q, ignoreCase = true) ||
                article.summary.contains(q, ignoreCase = true) ||
                article.summarySw.contains(q, ignoreCase = true) ||
                article.leagueTag.contains(q, ignoreCase = true) ||
                article.sourceName.contains(q, ignoreCase = true) ||
                article.category.contains(q, ignoreCase = true)
            catMatch && langMatch && queryMatch
        }
    }
    val isSwMode = selectedLangFilter == "SW" || (selectedLangFilter == "ALL" && appLanguage == AppLanguage.SWAHILI)
    val bookmarkedIds = bookmarks.map { it.contentId }.toSet()
    val hubAds = ads.filter { it.placement == "SPORT_HUB" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBlack),
        contentPadding = PaddingValues(bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Instagram Story Circles Tray (Matching Screenshots 8 & 9)
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(categories) { cat ->
                    val selected = selectedCategory == cat
                    val label = when (cat) {
                        "ALL" -> if (appLanguage == AppLanguage.SWAHILI) "Zote" else "All"
                        "TANZANIA_LIGI" -> "Ligi TZ"
                        "TANZANIA_SOCIAL" -> "Kijamii TZ"
                        "AFRICA_CAF" -> "Afrika CAF"
                        else -> cat.replace("_", " ").lowercase()
                            .replaceFirstChar { it.uppercaseChar() }
                    }
                    val code = when (cat) {
                        "ALL" -> "AL"
                        "TANZANIA_LIGI" -> "TZ"
                        "TANZANIA_SOCIAL" -> "KJ"
                        "AFRICA_CAF" -> "AF"
                        else -> cat.take(2).uppercase()
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(66.dp)
                            .clickable { selectedCategory = cat }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .clip(CircleShape)
                                .border(
                                    width = if (selected) 2.5.dp else 1.2.dp,
                                    color = if (selected) NeonLime else MidnightBorder,
                                    shape = CircleShape
                                )
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(MidnightCard),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = code,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = NeonLime
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) CrispWhite else SlateText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                items(langScopedNews.take(4), key = { "story_${it.id}" }) { story ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(66.dp)
                            .clickable { readingArticle = story }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .clip(CircleShape)
                                .border(
                                    width = 2.dp,
                                    brush = Brush.sweepGradient(listOf(NeonLime, VipAmber, LiveRed, NeonLime)),
                                    shape = CircleShape
                                )
                                .padding(3.dp)
                                .clip(CircleShape)
                        ) {
                            if (story.imageUrl.startsWith("http")) {
                                AsyncImage(
                                    model = story.imageUrl,
                                    contentDescription = story.localizedTitle(isSwMode),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Image(
                                    painter = painterResource(id = R.drawable.img_news_tactics),
                                    contentDescription = story.localizedTitle(isSwMode),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = story.sourceName,
                            style = MaterialTheme.typography.labelSmall,
                            color = CrispWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Inline Sport News Search Bar
        item {
            OutlinedTextField(
                value = newsSearchQuery,
                onValueChange = { newsSearchQuery = it },
                placeholder = {
                    Text(
                        text = if (appLanguage == AppLanguage.SWAHILI) "Tafuta habari za michezo, klabu au chanzo..." else "Search world sports news, clubs or sources...",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateText
                    )
                },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = NeonLime, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (newsSearchQuery.isNotBlank()) {
                        IconButton(onClick = { newsSearchQuery = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search", tint = SlateText, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
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
                    .padding(horizontal = 16.dp)
                    .testTag("news_search_input")
            )
        }

        // Swahili & English Language Filter Pills
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val langOptions = listOf(
                    "ALL" to if (appLanguage == AppLanguage.SWAHILI) "Zote (EN+SW)" else "All (EN+SW)",
                    "SW" to "Kiswahili (SW)",
                    "EN" to "English (EN)"
                )
                langOptions.forEach { (code, label) ->
                    val active = selectedLangFilter == code
                    Surface(
                        onClick = { selectedLangFilter = code },
                        shape = RoundedCornerShape(16.dp),
                        color = if (active) NeonLime else MidnightCard,
                        border = BorderStroke(1.dp, if (active) NeonLime else MidnightBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(modifier = Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (active) MidnightBlack else SlateText,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Breaking World Sports Wire Horizontal Strip
        if (langScopedNews.isNotEmpty() && newsSearchQuery.isBlank()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, tint = NeonLime, modifier = Modifier.size(16.dp))
                        Text(
                            text = if (selectedLangFilter == "SW" || appLanguage == AppLanguage.SWAHILI) {
                                "HABARI ZA SOKA TZ, AFRIKA & DUNIANI"
                            } else {
                                "BREAKING WORLD SPORTS WIRE"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = NeonLime
                        )
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(langScopedNews.take(6), key = { "wire_${it.id}" }) { wire ->
                            Surface(
                                onClick = { readingArticle = wire },
                                shape = RoundedCornerShape(14.dp),
                                color = MidnightCard,
                                border = BorderStroke(1.dp, MidnightBorder),
                                modifier = Modifier.width(235.dp)
                            ) {
                                Column(modifier = Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "${wire.leagueTag.ifBlank { wire.sourceName }} • ${wire.language}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonLime,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = wire.localizedTitle(isSwMode),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = CrispWhite,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (hubAds.isNotEmpty()) {
            if (hubAds.size == 1) {
                val ad = hubAds.first()
                item(key = "hub_ad_${ad.id}") {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        AdBannerCard(
                            ad = ad,
                            onClick = {
                                handleAdDestinationClick(
                                    context = context,
                                    destinationUrl = ad.destinationUrl,
                                    onNavigateVip = onNavigateVip,
                                    onNavigateTips = onNavigateTips,
                                    onNavigateSportHub = { tab -> if (tab == 1) onNavigatePodcasts() },
                                    onNavigateLiveScores = onNavigateLiveScores,
                                    onNavigateLiveArena = onNavigateLiveArena
                                )
                            }
                        )
                    }
                }
            } else {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(hubAds, key = { it.id }) { ad ->
                            AdBannerCard(
                                ad = ad,
                                onClick = {
                                    handleAdDestinationClick(
                                        context = context,
                                        destinationUrl = ad.destinationUrl,
                                        onNavigateVip = onNavigateVip,
                                        onNavigateTips = onNavigateTips,
                                        onNavigateSportHub = { tab -> if (tab == 1) onNavigatePodcasts() },
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

        if (filtered.isEmpty()) {
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    EmptyStateCard(
                        icon = Icons.Filled.Newspaper,
                        title = if (appLanguage == AppLanguage.SWAHILI) "Hakuna habari kwenye kipengele hiki" else "No Stories in $selectedCategory",
                        subtitle = if (appLanguage == AppLanguage.SWAHILI) {
                            "Jaribu kuchagua 'Zote' au gusa Sasisha kuvuta habari za Ligi Kuu Tanzania na Afrika."
                        } else {
                            "Try selecting All categories or tap refresh to sync live sports news inside DYFOOT."
                        }
                    )
                }
            }
        } else {
            items(filtered, key = { it.id }) { article ->
                val isLiked = article.id in locallyLikedIds
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    InstagramPostCard(
                        article = article,
                        displaySwahili = isSwMode,
                        isLiked = isLiked,
                        isBookmarked = article.id in bookmarkedIds,
                        onReadMore = { readingArticle = article },
                        onDoubleTapLike = {
                            if (article.id !in locallyLikedIds) {
                                locallyLikedIds = locallyLikedIds + article.id
                                if (!article.isExternalRss) viewModel.likeNews(article.id)
                            }
                        },
                        onToggleLike = {
                            val nowLiked = article.id !in locallyLikedIds
                            locallyLikedIds = if (nowLiked) locallyLikedIds + article.id else locallyLikedIds - article.id
                            if (nowLiked && !article.isExternalRss) viewModel.likeNews(article.id)
                        },
                        onComment = {
                            if (!article.isExternalRss) {
                                commentingArticle = article
                            } else {
                                readingArticle = article
                            }
                        },
                        onBookmark = {
                            viewModel.toggleBookmark("NEWS", article.id, article.localizedTitle(isSwMode), article.sourceName)
                        },
                        onShare = {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "${article.localizedTitle(isSwMode)}\n${article.localizedSummary(isSwMode)}\n— ${article.sourceName} (DYFOOT)"
                                )
                            }
                            context.startActivity(Intent.createChooser(share, "Share Football Story"))
                        }
                    )
                }
            }
        }
    }

    readingArticle?.let { article ->
        val isLiked = article.id in locallyLikedIds
        val readerSw = if (article.hasBilingualContent()) {
            isSwMode
        } else {
            (appLanguage == AppLanguage.SWAHILI && article.supportsSwahili()) ||
                article.language.equals("SW", ignoreCase = true)
        }
        InAppArticleReaderSheet(
            article = article,
            isSwahili = readerSw,
            isLiked = isLiked,
            isBookmarked = article.id in bookmarkedIds,
            onToggleLike = {
                val nowLiked = article.id !in locallyLikedIds
                locallyLikedIds = if (nowLiked) locallyLikedIds + article.id else locallyLikedIds - article.id
                if (nowLiked && !article.isExternalRss) viewModel.likeNews(article.id)
            },
            onToggleBookmark = {
                viewModel.toggleBookmark("NEWS", article.id, article.localizedTitle(readerSw), article.sourceName)
            },
            onDismiss = { readingArticle = null }
        )
    }

    commentingArticle?.let { article ->
        val comments by viewModel.observeNewsComments(article.id)
            .collectAsStateWithLifecycle(initialValue = emptyList())
        ModalBottomSheet(
            onDismissRequest = { commentingArticle = null },
            containerColor = MidnightElevated
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Comments (${comments.size})", style = MaterialTheme.typography.titleMedium, color = CrispWhite)
                comments.forEach { c ->
                    Surface(color = MidnightCard, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(c.authorName, style = MaterialTheme.typography.labelSmall, color = NeonLime)
                            Text(c.text, style = MaterialTheme.typography.bodySmall, color = CrispWhite)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = commentDraft,
                        onValueChange = { commentDraft = it },
                        placeholder = { Text("Add a comment...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonLime,
                            unfocusedBorderColor = MidnightBorder,
                            focusedTextColor = CrispWhite,
                            unfocusedTextColor = CrispWhite
                        )
                    )
                    IconButton(onClick = {
                        if (commentDraft.isNotBlank()) {
                            viewModel.addNewsComment(article.id, commentDraft)
                            commentDraft = ""
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send comment", tint = NeonLime)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun InstagramPostCard(
    article: NewsArticle,
    displaySwahili: Boolean = false,
    isLiked: Boolean,
    isBookmarked: Boolean,
    onReadMore: () -> Unit,
    onDoubleTapLike: () -> Unit,
    onToggleLike: () -> Unit,
    onComment: () -> Unit,
    onBookmark: () -> Unit,
    onShare: () -> Unit
) {
    var showHeartBurst by remember { mutableStateOf(false) }
    val heartScale by animateFloatAsState(
        targetValue = if (isLiked) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "ig_like_scale"
    )

    LaunchedEffect(showHeartBurst) {
        if (showHeartBurst) {
            delay(650L)
            showHeartBurst = false
        }
    }

    val displayTitle = article.localizedTitle(displaySwahili)
    val displaySummary = article.localizedSummary(displaySwahili)
    val displayContent = article.localizedContent(displaySwahili)
    val handle = article.sourceName.lowercase().replace(" ", "")
    val isSample = displayTitle.contains("[SAMPLE]")
    val initials = article.sourceName.trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { "DF" }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightCard),
        border = BorderStroke(1.dp, MidnightBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(bottom = 14.dp)) {
            // Card Header Row Matching Screenshots 8 & 9
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(1.8.dp, NeonLime, CircleShape)
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(MidnightCardAlt),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = CrispWhite
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = article.sourceName.ifBlank { "DYFOOT Desk" },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = CrispWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.Verified,
                                contentDescription = "Verified",
                                tint = NeonLime,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = "@$handle • ${article.publishedDateLabel.ifBlank { "Latest" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isSample) {
                        Surface(
                            color = VipAmberContainer.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, VipAmber.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = "SAMPLE",
                                style = MaterialTheme.typography.labelSmall,
                                color = VipAmber,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Surface(
                        color = MidnightCardAlt,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MidnightBorder)
                    ) {
                        Text(
                            text = article.leagueTag.ifBlank { article.category.replace("_", " ") }.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (article.localFootball) NeonLime else SlateText,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Full-bleed Photo with Double-Tap Heart Burst
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.18f)
                    .background(MidnightCardAlt)
                    .pointerInput(article.id) {
                        detectTapGestures(
                            onDoubleTap = {
                                showHeartBurst = true
                                onDoubleTapLike()
                            },
                            onTap = { onReadMore() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (article.imageUrl.startsWith("http")) {
                    AsyncImage(
                        model = article.imageUrl,
                        contentDescription = displayTitle,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.img_hero_stadium),
                        contentDescription = displayTitle,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = showHeartBurst,
                    enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = LiveRed.copy(alpha = 0.92f),
                        modifier = Modifier.size(92.dp)
                    )
                }
            }

            // Instagram Action Row (Heart, Comment, Send, right-pinned Bookmark)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleLike) {
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (isLiked) NeonLime else CrispWhite,
                            modifier = Modifier.scale(heartScale)
                        )
                    }
                    IconButton(onClick = onComment) {
                        Icon(Icons.Filled.ChatBubbleOutline, contentDescription = "Comments", tint = CrispWhite)
                    }
                    IconButton(onClick = onShare) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Share", tint = CrispWhite)
                    }
                }
                IconButton(onClick = onBookmark) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = "Save article",
                        tint = if (isBookmarked) NeonLime else CrispWhite
                    )
                }
            }

            // Likes + Headline + Summary + View Comments
            Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                val displayLikes = article.likesCount + if (isLiked && article.isExternalRss) 1 else 0
                Text(
                    text = "$displayLikes ${if (displayLikes == 1L) "like" else "likes"}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = CrispWhite
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = CrispWhite,
                    modifier = Modifier.clickable { onReadMore() }
                )
                if (displaySummary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = displaySummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                val wordCount = (displayContent.ifBlank { displaySummary }).split(" ").count { it.isNotBlank() }
                val readMins = (wordCount / 140).coerceAtLeast(1)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!article.isExternalRss) {
                        Text(
                            text = if (article.commentsCount > 0) "View all ${article.commentsCount} comments" else "Add a comment...",
                            style = MaterialTheme.typography.labelMedium,
                            color = SlateText,
                            modifier = Modifier.clickable { onComment() }
                        )
                    }
                    Text(
                        text = "~$readMins min read",
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateText
                    )
                    Text(
                        text = if (displaySwahili || article.language.equals("SW", ignoreCase = true)) "Soma Ndani ya App" else "Read In App",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = NeonLime,
                        modifier = Modifier.clickable { onReadMore() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InAppArticleReaderSheet(
    article: NewsArticle,
    isSwahili: Boolean,
    isLiked: Boolean,
    isBookmarked: Boolean,
    onToggleLike: () -> Unit,
    onToggleBookmark: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val displayTitle = article.localizedTitle(isSwahili)
    val bodyText = article.localizedContent(isSwahili).ifBlank { article.localizedSummary(isSwahili) }
    val wordCount = bodyText.split(" ").count { it.isNotBlank() }
    val readMins = (wordCount / 140).coerceAtLeast(1)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MidnightElevated
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp)
                .testTag("inapp_article_reader_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = NeonLimeContainer,
                    border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MenuBook,
                            contentDescription = null,
                            tint = NeonLime,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSwahili) "SOMA NDANI YA APP" else "IN-APP EXCLUSIVE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = NeonLime
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = if (isSwahili) "Funga" else "Close",
                        tint = SlateText
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MidnightCardAlt)
            ) {
                if (article.imageUrl.startsWith("http")) {
                    AsyncImage(
                        model = article.imageUrl,
                        contentDescription = displayTitle,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.img_hero_stadium),
                        contentDescription = displayTitle,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MidnightCard,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MidnightBorder)
                ) {
                    Text(
                        text = article.leagueTag.ifBlank { article.category.replace("_", " ") }.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = NeonLime,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Surface(
                    color = MidnightCard,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MidnightBorder)
                ) {
                    Text(
                        text = "${article.language.uppercase()} • ~$readMins min",
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = CrispWhite
            )

            Text(
                text = buildString {
                    append(article.sourceName.ifBlank { "DYFOOT Desk" })
                    if (article.publishedDateLabel.isNotBlank()) {
                        append(" • ${article.publishedDateLabel}")
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = NeonLime
            )

            HorizontalDivider(color = MidnightBorder)

            bodyText.split("\n\n").filter { it.isNotBlank() }.forEach { paragraph ->
                Text(
                    text = paragraph.trim(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = CrispWhite.copy(alpha = 0.92f),
                    lineHeight = 23.sp
                )
            }

            HorizontalDivider(color = MidnightBorder)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = onToggleLike,
                        shape = RoundedCornerShape(12.dp),
                        color = MidnightCard,
                        border = BorderStroke(1.dp, if (isLiked) NeonLime else MidnightBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Like",
                                tint = if (isLiked) NeonLime else CrispWhite,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSwahili) "Penda" else "Like",
                                style = MaterialTheme.typography.labelMedium,
                                color = CrispWhite
                            )
                        }
                    }

                    Surface(
                        onClick = onToggleBookmark,
                        shape = RoundedCornerShape(12.dp),
                        color = MidnightCard,
                        border = BorderStroke(1.dp, if (isBookmarked) NeonLime else MidnightBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = "Bookmark",
                                tint = if (isBookmarked) NeonLime else CrispWhite,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSwahili) "Hifadhi" else "Save",
                                style = MaterialTheme.typography.labelMedium,
                                color = CrispWhite
                            )
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = MidnightBlack),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isSwahili) "Funga" else "Close",
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}
