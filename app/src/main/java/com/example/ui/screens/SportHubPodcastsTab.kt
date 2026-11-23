package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.PodcastEpisode
import com.example.data.model.UiState
import com.example.ui.components.EmptyStateCard
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
import com.example.viewmodel.DyfootViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastsFeedTab(viewModel: DyfootViewModel) {
    val context = LocalContext.current
    val podcastsState by viewModel.publishedPodcasts.collectAsStateWithLifecycle()
    val recentIds by viewModel.recentlyPlayedPodcastIds.collectAsStateWithLifecycle()
    val activePodcast by viewModel.activePodcast.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val isPullRefreshing by viewModel.isPullRefreshing.collectAsStateWithLifecycle()
    val bookmarkedIds = bookmarks.map { it.contentId }.toSet()

    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var expandDescription by remember { mutableStateOf(false) }

    val audioPlayer = remember(context) { ExoPlayer.Builder(context).build() }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(1L) }
    var sliderPos by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        onDispose { audioPlayer.release() }
    }

    LaunchedEffect(activePodcast?.id) {
        val ep = activePodcast
        if (ep != null && ep.audioUrl.startsWith("http")) {
            audioPlayer.setMediaItem(MediaItem.fromUri(ep.audioUrl))
            audioPlayer.prepare()
            audioPlayer.setPlaybackSpeed(playbackSpeed)
            audioPlayer.playWhenReady = true
            isPlaying = true
        }
    }

    LaunchedEffect(playbackSpeed) {
        audioPlayer.setPlaybackSpeed(playbackSpeed)
    }

    LaunchedEffect(activePodcast?.id, isPlaying) {
        while (activePodcast != null) {
            isPlaying = audioPlayer.isPlaying
            durationMs = audioPlayer.duration.coerceAtLeast(1L)
            positionMs = audioPlayer.currentPosition.coerceAtLeast(0L)
            sliderPos = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            delay(400L)
        }
    }

    when (val state = podcastsState) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NeonLime)
            }
        }

        is UiState.Error -> {
            Text(state.message, color = LiveRed, modifier = Modifier.padding(16.dp))
        }

        is UiState.Success -> {
            val all = state.data
            val categories = listOf("ALL") + all.map { it.category }.distinct()
            val filtered = all.filter { ep ->
                (selectedCategory == "ALL" || ep.category == selectedCategory) &&
                    (query.isBlank() || ep.title.contains(query, true) || ep.hostName.contains(query, true) || ep.showName.contains(query, true))
            }
            val recent = recentIds.mapNotNull { id -> all.firstOrNull { it.id == id } }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MidnightBlack)
            ) {
                // YouTube Search & Filter Header
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search DYFOOT Podcasts & Hosts") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = SlateText) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonLime,
                        unfocusedBorderColor = MidnightBorder,
                        focusedContainerColor = MidnightCard,
                        unfocusedContainerColor = MidnightCard,
                        focusedTextColor = CrispWhite,
                        unfocusedTextColor = CrispWhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { cat ->
                        val selected = selectedCategory == cat
                        FilterChip(
                            selected = selected,
                            onClick = { selectedCategory = cat },
                            label = {
                                Text(
                                    text = if (cat == "ALL") "All" else cat,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CrispWhite,
                                selectedLabelColor = MidnightBlack,
                                containerColor = MidnightCardAlt,
                                labelColor = CrispWhite
                            )
                        )
                    }
                }

                // YouTube 16:9 Theater Player when an episode is selected
                activePodcast?.let { ep ->
                    YouTubeTheaterAudioPlayer(
                        episode = ep,
                        isPlaying = isPlaying,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        sliderPos = sliderPos,
                        playbackSpeed = playbackSpeed,
                        isBookmarked = ep.id in bookmarkedIds,
                        expandDescription = expandDescription,
                        onTogglePlay = {
                            if (audioPlayer.isPlaying) audioPlayer.pause() else audioPlayer.play()
                            isPlaying = !isPlaying
                        },
                        onSeekFraction = { frac ->
                            sliderPos = frac
                            audioPlayer.seekTo((frac * durationMs).toLong())
                        },
                        onRewind10 = {
                            audioPlayer.seekTo((audioPlayer.currentPosition - 10_000L).coerceAtLeast(0L))
                        },
                        onForward10 = {
                            audioPlayer.seekTo((audioPlayer.currentPosition + 10_000L).coerceAtMost(durationMs))
                        },
                        onCycleSpeed = {
                            playbackSpeed = when (playbackSpeed) {
                                1.0f -> 1.25f
                                1.25f -> 1.5f
                                1.5f -> 2.0f
                                else -> 1.0f
                            }
                        },
                        onBookmark = {
                            viewModel.toggleBookmark("PODCAST", ep.id, ep.title, ep.showName)
                        },
                        onShare = {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Listen to ${ep.title} (${ep.showName}) on DYFOOT")
                            }
                            context.startActivity(Intent.createChooser(share, "Share Podcast"))
                        },
                        onToggleDescription = { expandDescription = !expandDescription },
                        onClose = {
                            audioPlayer.pause()
                            viewModel.clearActivePodcast()
                        }
                    )
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
                        .testTag("podcasts_pull_refresh")
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 112.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                    if (recent.isNotEmpty() && activePodcast == null) {
                        item {
                            Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                                Text(
                                    text = "Continue Listening",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = CrispWhite
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(recent, key = { "rec_${it.id}" }) { ep ->
                                        Surface(
                                            onClick = { viewModel.selectPodcast(ep) },
                                            shape = RoundedCornerShape(12.dp),
                                            color = MidnightCard,
                                            border = BorderStroke(1.dp, MidnightBorder)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Filled.Headphones, contentDescription = null, tint = VipAmber, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = ep.title,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = CrispWhite,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.width(150.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (filtered.isEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(16.dp)) {
                                EmptyStateCard(
                                    icon = Icons.Filled.Headphones,
                                    title = "No Podcasts Published",
                                    subtitle = "Owner can upload or link authorized football podcast episodes in Admin Studio."
                                )
                            }
                        }
                    } else {
                        items(filtered, key = { it.id }) { ep ->
                            YouTubeEpisodeCard(
                                episode = ep,
                                isActive = activePodcast?.id == ep.id,
                                onPlay = { viewModel.selectPodcast(ep) },
                                onShare = {
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "Listen on DYFOOT Podcasts: ${ep.title} (${ep.showName})")
                                    }
                                    context.startActivity(Intent.createChooser(share, "Share Podcast"))
                                }
                            )
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun YouTubeTheaterAudioPlayer(
    episode: PodcastEpisode,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    sliderPos: Float,
    playbackSpeed: Float,
    isBookmarked: Boolean,
    expandDescription: Boolean,
    onTogglePlay: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onRewind10: () -> Unit,
    onForward10: () -> Unit,
    onCycleSpeed: () -> Unit,
    onBookmark: () -> Unit,
    onShare: () -> Unit,
    onToggleDescription: () -> Unit,
    onClose: () -> Unit
) {
    val eqTransition = rememberInfiniteTransition(label = "yt_eq")
    val bar1 by eqTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "bar1"
    )
    val bar2 by eqTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse),
        label = "bar2"
    )
    val bar3 by eqTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(380, easing = LinearEasing), RepeatMode.Reverse),
        label = "bar3"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MidnightCardAlt)
    ) {
        // 16:9 Widescreen Video-Style Theater Audio Stage
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MidnightBlack)
        ) {
            if (episode.coverUrl.startsWith("http")) {
                AsyncImage(
                    model = episode.coverUrl,
                    contentDescription = episode.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.img_podcast_cover),
                    contentDescription = episode.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MidnightBlack.copy(alpha = 0.45f),
                                MidnightBlack.copy(alpha = 0.55f),
                                MidnightBlack.copy(alpha = 0.92f)
                            )
                        )
                    )
            )

            // Top stage controls (NOW PLAYING + Speed + Close)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(color = LiveRed, shape = RoundedCornerShape(6.dp)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = CrispWhite, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isPlaying) "PLAYING" else "PAUSED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = CrispWhite
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = onCycleSpeed,
                        color = MidnightBlack.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "${playbackSpeed}x",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = NeonLime,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(MidnightBlack.copy(alpha = 0.7f))
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close player", tint = CrispWhite, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Center Transport Controls (-10s, Play/Pause, +10s)
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onRewind10,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MidnightBlack.copy(alpha = 0.55f))
                ) {
                    Icon(Icons.Filled.Replay10, contentDescription = "Rewind 10s", tint = CrispWhite, modifier = Modifier.size(26.dp))
                }
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(NeonLime)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play or pause",
                        tint = MidnightBlack,
                        modifier = Modifier.size(34.dp)
                    )
                }
                IconButton(
                    onClick = onForward10,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MidnightBlack.copy(alpha = 0.55f))
                ) {
                    Icon(Icons.Filled.Forward10, contentDescription = "Forward 10s", tint = CrispWhite, modifier = Modifier.size(26.dp))
                }
            }

            // Bottom Equalizer + Scrubber inside Theater Stage
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${formatPodcastSec(positionMs / 1000L)} / ${formatPodcastSec((durationMs / 1000L).coerceAtLeast(episode.durationSec))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CrispWhite
                    )
                    // Animated Equalizer Bars
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.height(16.dp)
                    ) {
                        val scales = if (isPlaying) listOf(bar1, bar2, bar3, bar2) else listOf(0.3f, 0.3f, 0.3f, 0.3f)
                        scales.forEach { s ->
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height((16 * s).dp)
                                    .background(NeonLime, RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
                Slider(
                    value = sliderPos,
                    onValueChange = onSeekFraction,
                    colors = SliderDefaults.colors(
                        thumbColor = LiveRed,
                        activeTrackColor = LiveRed,
                        inactiveTrackColor = CrispWhite.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.height(24.dp)
                )
            }
        }

        // YouTube Channel & Episode Title Strip
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleDescription() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CrispWhite,
                    maxLines = if (expandDescription) 4 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expandDescription) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = "Toggle details",
                    tint = SlateText
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(NeonLimeContainer)
                            .border(1.dp, NeonLime.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = episode.showName.take(2).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = NeonLime
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = episode.showName,
                                style = MaterialTheme.typography.titleSmall,
                                color = CrispWhite
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Filled.Verified, contentDescription = null, tint = InfoCyan, modifier = Modifier.size(13.dp))
                        }
                        Text(
                            text = "Hosted by ${episode.hostName} • ${episode.category}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateText
                        )
                    }
                }
                Row {
                    IconButton(onClick = onBookmark) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = "Save episode",
                            tint = if (isBookmarked) NeonLime else CrispWhite
                        )
                    }
                    IconButton(onClick = onShare) {
                        Icon(Icons.Filled.Share, contentDescription = "Share episode", tint = CrispWhite)
                    }
                }
            }

            AnimatedVisibility(visible = expandDescription && episode.description.isNotBlank()) {
                Surface(
                    color = MidnightCard,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = episode.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateText,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
        HorizontalDivider(color = MidnightBorder)
    }
}

@Composable
private fun YouTubeEpisodeCard(
    episode: PodcastEpisode,
    isActive: Boolean,
    onPlay: () -> Unit,
    onShare: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
    ) {
        // 16:9 Widescreen YouTube Thumbnail
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MidnightCard)
        ) {
            if (episode.coverUrl.startsWith("http")) {
                AsyncImage(
                    model = episode.coverUrl,
                    contentDescription = episode.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.img_podcast_cover),
                    contentDescription = episode.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            if (isActive) {
                Surface(
                    color = NeonLime,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                ) {
                    Text(
                        text = "NOW PLAYING",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MidnightBlack,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Bottom-Right Dark Duration Pill (YouTube Style)
            Surface(
                color = MidnightBlack.copy(alpha = 0.85f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
            ) {
                Text(
                    text = formatPodcastSec(episode.durationSec),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = CrispWhite,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }

        // YouTube Video Metadata Row (Circular Channel Avatar + Title + Channel Metadata)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MidnightCardAlt)
                    .border(1.dp, if (isActive) NeonLime else MidnightBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = episode.showName.take(2).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = NeonLime
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isActive) NeonLime else CrispWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${episode.showName} • ${episode.hostName} • ${episode.category}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = "Share episode", tint = SlateText, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun formatPodcastSec(totalSec: Long): String {
    val s = totalSec.coerceAtLeast(0L)
    val mins = s / 60
    val rem = s % 60
    return "%d:%02d".format(mins, rem)
}
