package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Adjust
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.Advertisement
import com.example.data.model.FixtureItem
import com.example.data.model.FixtureStatus
import com.example.data.model.RiskLevel
import com.example.data.model.TipItem
import com.example.data.model.TipStatus
import com.example.data.model.TipTier
import com.example.data.model.VipTipSecret
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.InfoCyan
import com.example.ui.theme.InfoCyanContainer
import com.example.ui.theme.LiveRed
import com.example.ui.theme.LiveRedContainer
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.MidnightCardAlt
import com.example.ui.theme.MidnightGlass
import com.example.ui.theme.MutedText
import com.example.ui.theme.NeonLime
import com.example.ui.theme.NeonLimeContainer
import com.example.ui.theme.SlateText
import com.example.ui.theme.VipAmber
import com.example.ui.theme.VipAmberContainer
import com.example.ui.theme.WonEmerald
import com.example.ui.theme.WonEmeraldContainer
import com.example.ui.i18n.DyfootCopy
import kotlin.math.absoluteValue

enum class MainTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    TIPS("tips", "Tips", Icons.Filled.Adjust, Icons.Outlined.Adjust),
    WATCH("watch", "Watch", Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle),
    HUB("sport_hub", "Hub", Icons.Filled.Newspaper, Icons.Outlined.Newspaper),
    CHAT("groups", "Chat", Icons.Filled.ChatBubbleOutline, Icons.Outlined.ChatBubbleOutline)
}

@Composable
fun DyfootFloatingBottomBar(
    currentRoute: String?,
    onSelectTab: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    copy: DyfootCopy? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = MidnightGlass,
            border = BorderStroke(1.dp, MidnightBorder),
            shadowElevation = 18.dp,
            modifier = Modifier
                .fillMaxWidth()
                .shadow(18.dp, RoundedCornerShape(30.dp), ambientColor = NeonLime.copy(alpha = 0.16f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MainTab.entries.forEach { tab ->
                    val displayLabel = when (tab) {
                        MainTab.HOME -> copy?.navHome ?: tab.label
                        MainTab.TIPS -> copy?.navTips ?: tab.label
                        MainTab.WATCH -> copy?.navWatch ?: tab.label
                        MainTab.HUB -> copy?.navHub ?: tab.label
                        MainTab.CHAT -> copy?.navChat ?: tab.label
                    }
                    val selected = currentRoute == tab.route
                    val pillColor by animateColorAsState(
                        targetValue = if (selected) NeonLime else Color.Transparent,
                        animationSpec = spring(),
                        label = "nav_pill"
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (selected) MidnightBlack else SlateText,
                        animationSpec = spring(),
                        label = "nav_content"
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(pillColor)
                            .clickable { onSelectTab(tab) }
                            .minimumInteractiveComponentSize()
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                            .testTag("nav_${tab.route}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = displayLabel,
                            tint = contentColor,
                            modifier = Modifier.size(21.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = displayLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = contentColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DyfootTopBar(
    unreadCount: Int,
    userPhotoUrl: String,
    userName: String,
    onNotificationsClick: () -> Unit,
    onProfileClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    val initials = userName.trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { "DY" }

    Surface(
        color = MidnightBlack.copy(alpha = 0.96f),
        border = BorderStroke(0.5.dp, MidnightBorder.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(NeonLime),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "DY",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = MidnightBlack
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = CrispWhite, fontWeight = FontWeight.Black)) {
                            append("DY")
                        }
                        withStyle(SpanStyle(color = NeonLime, fontWeight = FontWeight.Black)) {
                            append("FOOT")
                        }
                    },
                    fontSize = 20.sp,
                    letterSpacing = 0.5.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { onNotificationsClick() }
                        .testTag("top_notifications_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = "Notifications",
                        tint = CrispWhite,
                        modifier = Modifier.size(22.dp)
                    )
                    if (unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-3).dp, y = 4.dp)
                                .size(17.dp)
                                .clip(CircleShape)
                                .background(NeonLime),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MidnightBlack
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MidnightCardAlt)
                        .border(1.5.dp, MidnightBorder, CircleShape)
                        .clickable { onProfileClick() }
                        .testTag("top_profile_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    if (userPhotoUrl.startsWith("http")) {
                        AsyncImage(
                            model = userPhotoUrl,
                            contentDescription = "Profile",
                            modifier = Modifier.size(34.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = NeonLime
                        )
                    }
                }

                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.testTag("top_menu_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = "Menu",
                        tint = CrispWhite
                    )
                }
            }
        }
    }
}

@Composable
fun SegmentedPillSwitcher(
    options: List<Pair<String, ImageVector?>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MidnightCard,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MidnightBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEachIndexed { index, (label, icon) ->
                val selected = index == selectedIndex
                val bg by animateColorAsState(
                    targetValue = if (selected) NeonLime else Color.Transparent,
                    animationSpec = spring(),
                    label = "seg_bg_$index"
                )
                val fg by animateColorAsState(
                    targetValue = if (selected) MidnightBlack else SlateText,
                    animationSpec = spring(),
                    label = "seg_fg_$index"
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(bg)
                        .clickable { onSelect(index) }
                        .padding(vertical = 10.dp, horizontal = 8.dp)
                        .testTag("seg_tab_${label.lowercase().replace(" ", "_")}"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = fg,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = fg,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun SubScreenTopBar(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("subscreen_back_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = CrispWhite
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = CrispWhite
            )
        }
        trailing()
    }
}

@Composable
fun AdBannerCard(
    ad: Advertisement,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasUploadedBanner = ad.imageUrl.startsWith("https://") || ad.imageUrl.startsWith("http://")
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightCard),
        border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("dyfoot_ad_banner_${ad.id.ifBlank { ad.placement }}")
    ) {
        if (hasUploadedBanner) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(152.dp)
                        .background(MidnightCardAlt)
                ) {
                    AsyncImage(
                        model = ad.imageUrl,
                        contentDescription = ad.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(152.dp),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(152.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MidnightBlack.copy(alpha = 0.12f),
                                        Color.Transparent,
                                        MidnightCard.copy(alpha = 0.92f)
                                    )
                                )
                            )
                    )
                    Surface(
                        color = NeonLime,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "SPONSORED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MidnightBlack,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    if (ad.destinationUrl.isNotBlank()) {
                        Surface(
                            color = MidnightBlack.copy(alpha = 0.78f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Open promotion",
                                tint = NeonLime,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(14.dp)
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(MidnightCard, NeonLimeContainer.copy(alpha = 0.45f))
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = ad.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = CrispWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = ad.promoText,
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(MidnightCard, NeonLimeContainer.copy(alpha = 0.65f))
                        )
                    )
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_vip_crown_banner),
                        contentDescription = ad.title,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = NeonLime,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "SPONSORED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MidnightBlack,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = ad.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = CrispWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = ad.promoText,
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
}

fun handleAdDestinationClick(
    context: Context,
    destinationUrl: String,
    onNavigateVip: () -> Unit = {},
    onNavigateTips: () -> Unit = {},
    onNavigateSportHub: (Int) -> Unit = {},
    onNavigateLiveScores: () -> Unit = {},
    onNavigateLiveArena: () -> Unit = {}
) {
    val clean = destinationUrl.trim()
    if (clean.isBlank()) return
    val lower = clean.lowercase()
    when {
        lower.startsWith("dyfoot://vip") -> onNavigateVip()
        lower.startsWith("dyfoot://tips") -> onNavigateTips()
        lower.startsWith("dyfoot://podcast") -> onNavigateSportHub(1)
        lower.startsWith("dyfoot://hub") || lower.startsWith("dyfoot://news") -> onNavigateSportHub(0)
        lower.startsWith("dyfoot://live") || lower.startsWith("dyfoot://scores") -> onNavigateLiveScores()
        lower.startsWith("dyfoot://arena") -> onNavigateLiveArena()
        lower.startsWith("https://") || lower.startsWith("http://") -> {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(clean)))
            }
        }
    }
}

private val badgePalette = listOf(
    Color(0xFFD93644), // Crimson Red
    Color(0xFFD98A12), // Warm Amber
    Color(0xFF8B5CF6), // Vibrant Violet
    Color(0xFF0D7C8A), // Deep Teal
    Color(0xFF1E7CF0), // Electric Blue
    Color(0xFF17996B)  // Pitch Emerald
)

@Composable
fun TeamBadgeCircle(
    teamName: String,
    logoUrl: String = "",
    size: Dp = 30.dp
) {
    val cleanName = teamName.replace("[SAMPLE]", "").trim()
    val words = cleanName.split(" ").filter { it.isNotBlank() }
    val initials = when {
        words.size >= 2 -> "${words[0].first().uppercaseChar()}${words[1].first().uppercaseChar()}"
        cleanName.length >= 2 -> cleanName.take(2).uppercase()
        else -> "FC"
    }
    val bgColor = badgePalette[cleanName.hashCode().absoluteValue % badgePalette.size]

    if (logoUrl.startsWith("http")) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MidnightCardAlt)
                .border(1.dp, MidnightBorder, CircleShape)
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = logoUrl,
                contentDescription = teamName,
                modifier = Modifier.size(size - 6.dp).clip(CircleShape),
                contentScale = ContentScale.Fit
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                fontSize = if (size >= 34.dp) 12.sp else 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = CrispWhite
            )
        }
    }
}

@Composable
fun MatchCard(
    fixture: FixtureItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    isReminded: Boolean = false,
    onToggleReminder: (() -> Unit)? = null
) {
    val status = FixtureStatus.safeFrom(fixture.status)
    val isSample = fixture.homeTeam.contains("[SAMPLE]") || fixture.awayTeam.contains("[SAMPLE]") ||
        fixture.sourceLabel.contains("Sample", ignoreCase = true)
    val statusColor = when (status) {
        FixtureStatus.LIVE -> LiveRed
        FixtureStatus.FINISHED -> SlateText
        FixtureStatus.POSTPONED -> VipAmber
        FixtureStatus.UPCOMING -> NeonLime
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightCard),
        border = BorderStroke(
            1.dp,
            when {
                status == FixtureStatus.LIVE -> LiveRed.copy(alpha = 0.65f)
                isFavorite -> NeonLime.copy(alpha = 0.65f)
                else -> MidnightBorder
            }
        ),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = fixture.competition.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateText,
                    letterSpacing = 0.6.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                if (isSample) {
                    Surface(
                        color = VipAmberContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, VipAmber.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "SAMPLE",
                            style = MaterialTheme.typography.labelSmall,
                            color = VipAmber,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Surface(
                        color = if (status == FixtureStatus.LIVE) LiveRedContainer else MidnightCardAlt,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (status == FixtureStatus.LIVE) "LIVE ${fixture.minuteLabel}" else fixture.minuteLabel.ifBlank { status.label },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                if (onToggleReminder != null && status != FixtureStatus.FINISHED) {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onToggleReminder,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("remind_match_${fixture.id}")
                    ) {
                        Icon(
                            imageVector = if (isReminded) Icons.Filled.NotificationsActive else Icons.Outlined.Notifications,
                            contentDescription = if (isReminded) "Cancel kickoff reminder" else "Set kickoff reminder",
                            tint = if (isReminded) InfoCyan else SlateText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (onToggleFavorite != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("fav_match_${fixture.id}")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove favorite match" else "Favorite match for live alerts",
                            tint = if (isFavorite) NeonLime else SlateText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Home Team Stacked Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    TeamBadgeCircle(
                        teamName = fixture.homeTeam,
                        logoUrl = fixture.homeLogoUrl,
                        size = 28.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = fixture.homeTeam.replace("[SAMPLE]", "").trim(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = CrispWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (status != FixtureStatus.UPCOMING) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = fixture.homeScore.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (status == FixtureStatus.LIVE) NeonLime else CrispWhite
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Away Team Stacked Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    TeamBadgeCircle(
                        teamName = fixture.awayTeam,
                        logoUrl = fixture.awayLogoUrl,
                        size = 28.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = fixture.awayTeam.replace("[SAMPLE]", "").trim(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = CrispWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (status != FixtureStatus.UPCOMING) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = fixture.awayScore.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (status == FixtureStatus.LIVE) NeonLime else CrispWhite
                    )
                }
            }

            if (fixture.possessionHome.isNotBlank() || fixture.cornersHome.isNotBlank() || fixture.yellowCardsHome.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (fixture.possessionHome.isNotBlank()) {
                        Surface(color = MidnightCardAlt, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                text = "Poss ${fixture.possessionHome}/${fixture.possessionAway}",
                                style = MaterialTheme.typography.labelSmall,
                                color = InfoCyan,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (fixture.cornersHome.isNotBlank()) {
                        Surface(color = MidnightCardAlt, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                text = "🚩 ${fixture.cornersHome}-${fixture.cornersAway}",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonLime,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (fixture.yellowCardsHome.isNotBlank()) {
                        Surface(color = MidnightCardAlt, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                text = "🟨 ${fixture.yellowCardsHome}-${fixture.yellowCardsAway}",
                                style = MaterialTheme.typography.labelSmall,
                                color = VipAmber,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (status == FixtureStatus.UPCOMING) "Kickoff • ${fixture.minuteLabel}" else fixture.sourceLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (fixture.eventsSummary.isNotBlank()) "Goals & Stats" else "Match Center",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonLime
                )
            }
        }
    }
}

@Composable
fun TipSlipCard(
    tip: TipItem,
    vipSecret: VipTipSecret?,
    hasVipAccess: Boolean,
    isBookmarked: Boolean,
    onBookmarkToggle: () -> Unit,
    onUpgradeVipClick: () -> Unit,
    context: Context,
    modifier: Modifier = Modifier,
    isInCombo: Boolean = false,
    onToggleCombo: (() -> Unit)? = null
) {
    val isVip = tip.tier == TipTier.VIP.name
    val unlocked = !isVip || (hasVipAccess && vipSecret != null)
    val tipStatus = TipStatus.safeFrom(tip.status)
    val risk = RiskLevel.safeFrom(tip.riskLevel)
    val isSample = tip.matchName.contains("[SAMPLE]")

    val confidenceBars = when (risk) {
        RiskLevel.LOW -> 4
        RiskLevel.MEDIUM -> 3
        RiskLevel.HIGH -> 2
    }

    val statusColor = when (tipStatus) {
        TipStatus.WON -> WonEmerald
        TipStatus.LOST -> LiveRed
        TipStatus.VOID, TipStatus.POSTPONED -> SlateText
        TipStatus.PENDING -> VipAmber
    }
    val statusBg = when (tipStatus) {
        TipStatus.WON -> WonEmeraldContainer
        TipStatus.LOST -> LiveRedContainer
        TipStatus.VOID, TipStatus.POSTPONED -> MidnightCardAlt
        TipStatus.PENDING -> VipAmberContainer
    }

    val homeName = tip.homeTeam.ifBlank { tip.matchName.substringBefore(" vs ", tip.matchName) }.replace("[SAMPLE]", "").trim()
    val awayName = tip.awayTeam.ifBlank { tip.matchName.substringAfter(" vs ", "") }.replace("[SAMPLE]", "").trim()

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightCard),
        border = BorderStroke(1.dp, if (isVip) VipAmber.copy(alpha = 0.45f) else MidnightBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Meta Row: Tier pill + Competition • Kickoff + Status pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = if (isVip) VipAmber else NeonLime,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isVip) {
                                Icon(
                                    imageVector = Icons.Filled.WorkspacePremium,
                                    contentDescription = null,
                                    tint = MidnightBlack,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                            }
                            Text(
                                text = if (isVip) "VIP" else "FREE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MidnightBlack
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${tip.competition} • ${tip.kickoffLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.55f))
                ) {
                    Text(
                        text = if (isSample) "SAMPLE" else tipStatus.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Team Badges + Match Row (Matches Screenshot 3, 4, 5)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    TeamBadgeCircle(teamName = homeName, size = 34.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = homeName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = CrispWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "vs",
                    style = MaterialTheme.typography.labelMedium,
                    color = SlateText,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = awayName.ifBlank { "Opponent" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = CrispWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TeamBadgeCircle(teamName = awayName.ifBlank { "OP" }, size = 34.dp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (unlocked) {
                val selectionText = if (isVip) vipSecret?.selection.orEmpty() else tip.selection
                val codeText = if (isVip) vipSecret?.bookmakerCode.orEmpty() else tip.bookmakerCode
                val scoreText = if (isVip) vipSecret?.predictedScore.orEmpty() else tip.predictedScore
                val analysisText = if (isVip) vipSecret?.analysis.orEmpty() else tip.analysis

                if (analysisText.isNotBlank()) {
                    Text(
                        text = analysisText.replace("[SAMPLE]", "").trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Inner Selection Container (Screenshot dark inset card)
                Surface(
                    color = Color(0xFF0A101D),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MidnightBorder.copy(alpha = 0.75f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tip.market.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = SlateText,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = selectionText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = CrispWhite
                            )
                            if (scoreText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Predicted Score: $scoreText • ${risk.label}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InfoCyan
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Surface(
                            color = NeonLimeContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = tip.odds,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = NeonLime,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Copyable Slip Code Pills (Matching Screenshot 3 & 4)
                if (tip.bookmakerName.isNotBlank() || codeText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "SLIP CODES",
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateText,
                        letterSpacing = 0.6.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val copyVal = codeText.ifBlank { "${tip.bookmakerName}-${tip.odds}" }
                        Surface(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("DYFOOT Slip Code", copyVal))
                                Toast.makeText(context, "Copied slip code: $copyVal", Toast.LENGTH_SHORT).show()
                            },
                            color = Color(0xFF0A101D),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, MidnightBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tip.bookmakerName.ifBlank { "Bookmaker" },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SlateText
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = copyVal,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = CrispWhite
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Copy slip code",
                                    tint = NeonLime,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Confidence Dots + Combo Toggle + Actions + Responsible Betting Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (seg in 1..5) {
                            Box(
                                modifier = Modifier
                                    .width(14.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (seg <= confidenceBars) NeonLime else MidnightCardAlt)
                            )
                        }
                        if (onToggleCombo != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                onClick = onToggleCombo,
                                shape = RoundedCornerShape(10.dp),
                                color = if (isInCombo) NeonLime else MidnightCardAlt,
                                border = BorderStroke(1.dp, if (isInCombo) NeonLime else MidnightBorder)
                            ) {
                                Text(
                                    text = if (isInCombo) "Combo ✓" else "+ Combo",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isInCombo) MidnightBlack else NeonLime,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBookmarkToggle,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = "Save tip",
                                tint = if (isBookmarked) NeonLime else SlateText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                val shareText = buildString {
                                    append("DYFOOT Tip: ${tip.matchName}\n")
                                    append("Market: ${tip.market} -> $selectionText\n")
                                    append("Odds: ${tip.odds} (${tip.bookmakerName} $codeText)\n")
                                    append("Status: ${tipStatus.label}")
                                }
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share DYFOOT Slip"))
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "Share tip",
                                tint = SlateText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "18+ • Bet responsibly",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedText,
                            fontSize = 10.sp
                        )
                    }
                }
            } else {
                Surface(
                    color = VipAmberContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, VipAmber.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "VIP Locked",
                            tint = VipAmber,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Protected VIP Selection • Odds ${tip.odds}",
                            style = MaterialTheme.typography.titleSmall,
                            color = CrispWhite
                        )
                        Text(
                            text = "Selection, slip code & tactical analysis are protected by server rules for active VIP members.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateText
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onUpgradeVipClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VipAmber,
                                contentColor = MidnightBlack
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("unlock_vip_btn_${tip.id}")
                        ) {
                            Text("Unlock VIP Access", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoChip(text: String) {
    Surface(
        color = InfoCyanContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = InfoCyan,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    eyebrow: String? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (!eyebrow.isNullOrBlank()) {
                Text(
                    text = eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = NeonLime,
                    letterSpacing = 1.2.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = CrispWhite
            )
        }
        if (actionLabel != null && onActionClick != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = NeonLime,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onActionClick() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightCard),
        border = BorderStroke(1.dp, MidnightBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = NeonLime,
                modifier = Modifier.size(34.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = CrispWhite)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SlateText)
        }
    }
}
