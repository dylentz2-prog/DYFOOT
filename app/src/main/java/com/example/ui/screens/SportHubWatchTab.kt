package com.example.ui.screens

import android.content.Intent
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.model.UiState
import com.example.data.model.VideoItem
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.InfoCyan
import com.example.ui.theme.LiveRed
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.MidnightCardAlt
import com.example.ui.theme.MidnightElevated
import com.example.ui.theme.NeonLime
import com.example.ui.theme.SlateText
import com.example.viewmodel.DyfootViewModel
import kotlinx.coroutines.delay

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@OptIn(UnstableApi::class)
@Composable
fun WatchFeedTab(viewModel: DyfootViewModel) {
    val context = LocalContext.current
    val videosState by viewModel.publishedVideos.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val bookmarkedIds = bookmarks.map { it.contentId }.toSet()

    var feedMode by remember { mutableIntStateOf(1) } // 0 = Following, 1 = For You
    var followedCreators by remember { mutableStateOf<Set<String>>(emptySet()) }
    var likedVideoIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    when (val state = videosState) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NeonLime)
            }
        }

        is UiState.Error -> {
            Text(state.message, color = LiveRed, modifier = Modifier.padding(16.dp))
        }

        is UiState.Success -> {
            val allVideos = state.data
            val videos = remember(allVideos, feedMode, followedCreators) {
                if (feedMode == 0 && followedCreators.isNotEmpty()) {
                    allVideos.filter { it.creatorName in followedCreators }.ifEmpty { allVideos }
                } else {
                    allVideos
                }
            }

            if (videos.isEmpty()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    EmptyStateCard(
                        icon = Icons.Filled.PlayCircle,
                        title = "No Watch Videos Yet",
                        subtitle = "Owner can publish vertical sports highlights & reels from Admin Studio."
                    )
                }
                return
            }

            val pagerState = rememberPagerState(pageCount = { videos.size })
            var isMuted by remember { mutableStateOf(false) }
            var isPaused by remember { mutableStateOf(false) }
            var playbackProgress by remember { mutableFloatStateOf(0f) }
            var showHeartBurst by remember { mutableStateOf(false) }
            var commentingVideo by remember { mutableStateOf<VideoItem?>(null) }
            var commentDraft by remember { mutableStateOf("") }

            val exoPlayer = remember(context) {
                ExoPlayer.Builder(context).build().apply {
                    repeatMode = Player.REPEAT_MODE_ONE
                }
            }

            DisposableEffect(Unit) {
                onDispose { exoPlayer.release() }
            }

            val currentVideo = videos.getOrNull(pagerState.currentPage)
            LaunchedEffect(currentVideo?.id, currentVideo?.videoUrl) {
                if (currentVideo != null && currentVideo.videoUrl.startsWith("http")) {
                    exoPlayer.setMediaItem(MediaItem.fromUri(currentVideo.videoUrl))
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = !isPaused
                }
            }
            LaunchedEffect(isMuted) {
                exoPlayer.volume = if (isMuted) 0f else 1f
            }
            LaunchedEffect(isPaused) {
                exoPlayer.playWhenReady = !isPaused
            }
            LaunchedEffect(currentVideo?.id, isPaused) {
                while (!isPaused) {
                    val dur = exoPlayer.duration.coerceAtLeast(1L)
                    val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                    playbackProgress = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                    delay(250L)
                }
            }
            LaunchedEffect(showHeartBurst) {
                if (showHeartBurst) {
                    delay(650L)
                    showHeartBurst = false
                }
            }

            val infiniteTransition = rememberInfiniteTransition(label = "vinyl_spin")
            val discRotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 4500, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "disc_rotation"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MidnightBlack)
                    .padding(bottom = 86.dp)
            ) {
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val video = videos[page]
                    val isCurrent = page == pagerState.currentPage
                    val isFollowed = video.creatorName in followedCreators
                    val isLiked = video.id in likedVideoIds

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MidnightBlack)
                            .pointerInput(video.id) {
                                detectTapGestures(
                                    onTap = { isPaused = !isPaused },
                                    onDoubleTap = {
                                        showHeartBurst = true
                                        if (video.id !in likedVideoIds) {
                                            likedVideoIds = likedVideoIds + video.id
                                            viewModel.likeVideo(video.id)
                                        }
                                    }
                                )
                            }
                    ) {
                        if (isCurrent && video.videoUrl.startsWith("http")) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        useController = false
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        player = exoPlayer
                                    }
                                },
                                update = { it.player = exoPlayer },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.img_hero_stadium),
                                contentDescription = video.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        // Dark edge gradients for readability
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            MidnightBlack.copy(alpha = 0.45f),
                                            MidnightBlack.copy(alpha = 0.05f),
                                            MidnightBlack.copy(alpha = 0.25f),
                                            MidnightBlack.copy(alpha = 0.92f)
                                        )
                                    )
                                )
                        )

                        // Center Play Indicator when paused
                        if (isPaused && isCurrent) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(MidnightBlack.copy(alpha = 0.65f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Play",
                                    tint = NeonLime,
                                    modifier = Modifier.size(42.dp)
                                )
                            }
                        }

                        // Double-Tap Heart Animation
                        AnimatedVisibility(
                            visible = showHeartBurst && isCurrent,
                            modifier = Modifier.align(Alignment.Center),
                            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                            exit = scaleOut() + fadeOut()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = null,
                                tint = LiveRed.copy(alpha = 0.92f),
                                modifier = Modifier.size(100.dp)
                            )
                        }

                        // TikTok Right Action Rail (Avatar + Follow badge, Like, Comments, Bookmark, Share, Mute, Vinyl Disc)
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 12.dp, bottom = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Creator Avatar + Overlapping Follow '+' Badge
                            Box(
                                modifier = Modifier
                                    .padding(bottom = 6.dp)
                                    .clickable {
                                        followedCreators = if (isFollowed) {
                                            followedCreators - video.creatorName
                                        } else {
                                            Toast.makeText(context, "Following @${video.creatorName}", Toast.LENGTH_SHORT).show()
                                            followedCreators + video.creatorName
                                        }
                                    },
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MidnightCardAlt)
                                        .border(2.dp, CrispWhite, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = video.creatorName.take(2).uppercase(),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonLime
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .offset(y = 7.dp)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(if (isFollowed) CrispWhite else LiveRed),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isFollowed) Icons.Filled.Check else Icons.Filled.Add,
                                        contentDescription = "Follow creator",
                                        tint = if (isFollowed) MidnightBlack else CrispWhite,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    onClick = {
                                        if (video.id !in likedVideoIds) {
                                            likedVideoIds = likedVideoIds + video.id
                                        }
                                        viewModel.likeVideo(video.id)
                                    },
                                    shape = CircleShape,
                                    color = MidnightBlack.copy(alpha = 0.58f),
                                    border = BorderStroke(1.dp, CrispWhite.copy(alpha = 0.22f)),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.Favorite,
                                            contentDescription = "Like video",
                                            tint = if (isLiked) LiveRed else CrispWhite,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = if (video.likesCount > 0) "${video.likesCount}" else "Like",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CrispWhite,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    onClick = { commentingVideo = video },
                                    shape = CircleShape,
                                    color = MidnightBlack.copy(alpha = 0.58f),
                                    border = BorderStroke(1.dp, CrispWhite.copy(alpha = 0.22f)),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.ChatBubbleOutline,
                                            contentDescription = "Comments",
                                            tint = CrispWhite,
                                            modifier = Modifier.size(23.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "${video.commentsCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CrispWhite,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    onClick = {
                                        viewModel.toggleBookmark("VIDEO", video.id, video.title, video.creatorName)
                                    },
                                    shape = CircleShape,
                                    color = MidnightBlack.copy(alpha = 0.58f),
                                    border = BorderStroke(1.dp, CrispWhite.copy(alpha = 0.22f)),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (video.id in bookmarkedIds) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                            contentDescription = "Save video",
                                            tint = if (video.id in bookmarkedIds) NeonLime else CrispWhite,
                                            modifier = Modifier.size(23.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text("Save", style = MaterialTheme.typography.labelSmall, color = CrispWhite)
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    onClick = {
                                        val share = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, "Watch on DYFOOT: ${video.title}\n${video.videoUrl}")
                                        }
                                        context.startActivity(Intent.createChooser(share, "Share Video"))
                                    },
                                    shape = CircleShape,
                                    color = MidnightBlack.copy(alpha = 0.58f),
                                    border = BorderStroke(1.dp, CrispWhite.copy(alpha = 0.22f)),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.Share,
                                            contentDescription = "Share video",
                                            tint = CrispWhite,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text("Share", style = MaterialTheme.typography.labelSmall, color = CrispWhite)
                            }

                            // Rotating Vinyl Sound Disc
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .rotate(if (!isPaused && isCurrent) discRotation else 0f)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(listOf(NeonLime, MidnightCardAlt, MidnightBlack))
                                    )
                                    .border(2.dp, SlateText.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MusicNote,
                                    contentDescription = "Soundtrack",
                                    tint = CrispWhite,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // TikTok Bottom-Left Sport Category Badge, Creator, Headline & Sound Ticker (Matches Screenshot 6)
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 16.dp, end = 86.dp, bottom = 22.dp)
                        ) {
                            Surface(
                                color = NeonLime,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = video.category.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MidnightBlack,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = video.title.replace("[SAMPLE]", "").trim(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = CrispWhite
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = buildAnnotatedString {
                                    if (video.caption.isNotBlank()) {
                                        append(video.caption)
                                        append(" ")
                                    }
                                    withStyle(SpanStyle(color = NeonLime, fontWeight = FontWeight.Bold)) {
                                        append("#DYFOOT #${video.category.replace(" ", "")}")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = CrispWhite.copy(alpha = 0.88f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    tint = NeonLime,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Source: @${video.creatorName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SlateText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Filled.Verified,
                                    contentDescription = null,
                                    tint = InfoCyan,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }

                        // Slim Bottom Playback Progress Bar
                        LinearProgressIndicator(
                            progress = { if (isCurrent) playbackProgress else 0f },
                            color = NeonLime,
                            trackColor = CrispWhite.copy(alpha = 0.22f),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(2.5.dp)
                        )
                    }
                }

                // TikTok Top "Following | For You" Overlay Header + Mute Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MidnightBlack.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CrispWhite.copy(alpha = 0.15f))
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1}/${videos.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CrispWhite,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { feedMode = 0 }
                        ) {
                            Text(
                                text = "Following",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (feedMode == 0) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (feedMode == 0) CrispWhite else CrispWhite.copy(alpha = 0.6f)
                            )
                            if (feedMode == 0) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Box(
                                    modifier = Modifier
                                        .width(26.dp)
                                        .height(2.5.dp)
                                        .background(NeonLime, RoundedCornerShape(2.dp))
                                )
                            }
                        }
                        Text("|", color = CrispWhite.copy(alpha = 0.35f))
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { feedMode = 1 }
                        ) {
                            Text(
                                text = "For You",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (feedMode == 1) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (feedMode == 1) CrispWhite else CrispWhite.copy(alpha = 0.6f)
                            )
                            if (feedMode == 1) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Box(
                                    modifier = Modifier
                                        .width(26.dp)
                                        .height(2.5.dp)
                                        .background(NeonLime, RoundedCornerShape(2.dp))
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { isMuted = !isMuted },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MidnightBlack.copy(alpha = 0.45f))
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Toggle mute",
                            tint = CrispWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            commentingVideo?.let { vid ->
                val comments by viewModel.observeVideoComments(vid.id)
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                ModalBottomSheet(
                    onDismissRequest = { commentingVideo = null },
                    containerColor = MidnightElevated
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Video Comments (${comments.size})", style = MaterialTheme.typography.titleMedium, color = CrispWhite)
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
                                    viewModel.addVideoComment(vid.id, commentDraft)
                                    commentDraft = ""
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = NeonLime)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}
