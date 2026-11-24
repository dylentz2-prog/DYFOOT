package com.example.ui.screens

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.model.FixtureItem
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SubScreenTopBar
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.LiveRed
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.MidnightCardAlt
import com.example.ui.theme.NeonLime
import com.example.ui.theme.SlateText
import com.example.ui.theme.VipAmber
import com.example.ui.theme.VipAmberContainer
import com.example.ui.theme.WonEmerald
import com.example.ui.theme.WonEmeraldContainer
import com.example.viewmodel.DyfootViewModel

@OptIn(UnstableApi::class)
@Composable
fun LiveArenaScreen(
    viewModel: DyfootViewModel,
    onBack: () -> Unit,
    onUpgradeVipClick: () -> Unit
) {
    val context = LocalContext.current
    val fixtures by viewModel.combinedFixtures.collectAsStateWithLifecycle()
    val scheduled = fixtures.filter { it.streamScheduled }
    val hasVip = viewModel.hasActiveVipAccess()
    val secretStatus = remember { viewModel.providerSecretStatus() }
    val defaultFeedUrl = remember { viewModel.resolveLivestreamUrl() }
    val livestreamApiKey = remember { viewModel.livestreamApiKeyOrEmpty() }

    var activeFixture by remember(scheduled) {
        mutableStateOf(
            scheduled.firstOrNull { it.streamUrl.startsWith("https://") }
                ?: scheduled.firstOrNull()
        )
    }
    val activeStreamUrl = remember(activeFixture, defaultFeedUrl) {
        viewModel.resolveLivestreamUrl(activeFixture?.streamUrl.orEmpty())
    }
    var isMuted by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }

    val exoPlayer = remember(context, livestreamApiKey) {
        val httpFactory = DefaultHttpDataSource.Factory().apply {
            if (livestreamApiKey.isNotEmpty()) {
                setDefaultRequestProperties(mapOf("Authorization" to "Bearer $livestreamApiKey"))
            }
        }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    LaunchedEffect(activeStreamUrl, hasVip) {
        if (hasVip && activeStreamUrl.startsWith("https://")) {
            exoPlayer.setMediaItem(MediaItem.fromUri(activeStreamUrl))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = !isPaused
        } else {
            exoPlayer.stop()
        }
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(isPaused) {
        exoPlayer.playWhenReady = !isPaused
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBlack)
            .navigationBarsPadding()
    ) {
        SubScreenTopBar(title = "Live Arena", onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (hasVip && activeStreamUrl.startsWith("https://")) {
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, LiveRed),
                        colors = CardDefaults.cardColors(containerColor = MidnightCard),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("live_arena_player_card")
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                                    .background(MidnightBlack)
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        PlayerView(ctx).apply {
                                            useController = false
                                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
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

                                Surface(
                                    color = LiveRed,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = "LIVE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = CrispWhite,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(shape = RoundedCornerShape(10.dp), color = MidnightBlack.copy(alpha = 0.72f)) {
                                        IconButton(onClick = { isPaused = !isPaused }) {
                                            Icon(
                                                imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                                contentDescription = if (isPaused) "Play live stream" else "Pause live stream",
                                                tint = CrispWhite
                                            )
                                        }
                                    }
                                    Surface(shape = RoundedCornerShape(10.dp), color = MidnightBlack.copy(alpha = 0.72f)) {
                                        IconButton(onClick = { isMuted = !isMuted }) {
                                            Icon(
                                                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                                contentDescription = if (isMuted) "Unmute stream" else "Mute stream",
                                                tint = CrispWhite
                                            )
                                        }
                                    }
                                }
                            }
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = activeFixture?.let { "${it.homeTeam} vs ${it.awayTeam}" } ?: "DYFOOT Licensed Live Broadcast",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = CrispWhite
                                )
                                Text(
                                    text = activeFixture?.competition ?: "Connected via LIVESTREAM_FEED_URL",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NeonLime
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, LiveRed.copy(alpha = 0.55f)),
                        colors = CardDefaults.cardColors(containerColor = MidnightCard),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Image(
                                painter = painterResource(id = R.drawable.img_hero_stadium),
                                contentDescription = "Live Arena",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(190.dp),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(190.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(MidnightBlack.copy(alpha = 0.25f), MidnightBlack.copy(alpha = 0.94f))
                                        )
                                    )
                            )
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(16.dp)
                            ) {
                                Surface(
                                    color = if (activeStreamUrl.isNotEmpty() && !hasVip) LiveRed else VipAmber,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = when {
                                            activeStreamUrl.isNotEmpty() && !hasVip -> "VIP LOCKED"
                                            secretStatus.livestreamKeyConfigured -> "TOKEN READY"
                                            else -> "COMING SOON"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (activeStreamUrl.isNotEmpty() && !hasVip) CrispWhite else MidnightBlack,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("DYFOOT Live Arena", style = MaterialTheme.typography.headlineSmall, color = CrispWhite)
                                Text(
                                    text = if (activeStreamUrl.isNotEmpty() && !hasVip) {
                                        "Licensed live stream is active. Upgrade to VIP to unlock full HD playback."
                                    } else {
                                        "Licensed live match streaming requires official broadcasting rights & LIVESTREAM_FEED_URL / LIVESTREAM_API_KEY in Secrets."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateText
                                )
                            }
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MidnightCard,
                    border = BorderStroke(1.dp, MidnightBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (hasVip) Icons.Filled.VerifiedUser else Icons.Filled.Lock,
                            contentDescription = null,
                            tint = if (hasVip) NeonLime else VipAmber
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (hasVip) "VIP Streaming Pass Ready" else "Premium Access Control",
                                style = MaterialTheme.typography.titleSmall,
                                color = CrispWhite
                            )
                            Text(
                                text = if (hasVip) {
                                    "Your account has active VIP access when licensed broadcasts launch."
                                } else {
                                    "Live Arena streams will be gated to verified VIP members."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateText
                            )
                        }
                        if (!hasVip) {
                            Button(
                                onClick = onUpgradeVipClick,
                                colors = ButtonDefaults.buttonColors(containerColor = VipAmber, contentColor = MidnightBlack),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("VIP", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MidnightCardAlt,
                    border = BorderStroke(1.dp, MidnightBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Stream Secrets & Feed Status",
                                style = MaterialTheme.typography.labelLarge,
                                color = CrispWhite
                            )
                            Text(
                                text = "Token: ${if (secretStatus.livestreamKeyConfigured) "Set" else "Missing"} • Feed: ${if (secretStatus.livestreamFeedUrlConfigured) "Set" else "Per-match / Missing"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SlateText
                            )
                        }
                        Surface(
                            color = if (activeStreamUrl.isNotEmpty() || secretStatus.hasLivestreamReady) WonEmeraldContainer else VipAmberContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (activeStreamUrl.isNotEmpty()) "STREAM READY" else if (secretStatus.hasLivestreamReady) "KEYS SET" else "AWAITING SECRETS",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (activeStreamUrl.isNotEmpty() || secretStatus.hasLivestreamReady) WonEmerald else VipAmber,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            item {
                Text("Scheduled Broadcast Calendar", style = MaterialTheme.typography.titleMedium, color = CrispWhite)
            }

            if (scheduled.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Filled.LiveTv,
                        title = "No Licensed Broadcasts Connected",
                        subtitle = "No copyrighted streams are embedded. Owner can schedule upcoming matches in Admin Studio once a licensed provider is integrated."
                    )
                }
            } else {
                items(scheduled, key = { it.id }) { match ->
                    val matchHasStream = match.streamUrl.startsWith("https://") || defaultFeedUrl.isNotEmpty()
                    Card(
                        onClick = {
                            if (matchHasStream) {
                                activeFixture = match
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MidnightCard),
                        border = BorderStroke(
                            1.dp,
                            if (activeFixture?.id == match.id && matchHasStream) NeonLime else MidnightBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(match.competition, style = MaterialTheme.typography.labelSmall, color = NeonLime)
                                Text("${match.homeTeam} vs ${match.awayTeam}", style = MaterialTheme.typography.titleSmall, color = CrispWhite)
                                Text(match.minuteLabel.ifBlank { match.venue }, style = MaterialTheme.typography.bodySmall, color = SlateText)
                            }
                            Surface(
                                color = if (matchHasStream) WonEmeraldContainer else VipAmberContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (matchHasStream) "WATCH LIVE" else "COMING SOON",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (matchHasStream) WonEmerald else VipAmber,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
