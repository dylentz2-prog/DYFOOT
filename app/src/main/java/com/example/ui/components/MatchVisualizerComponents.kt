package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FixtureStatus
import com.example.data.model.MatchDetailBundle
import com.example.data.model.SquadPlayerNode
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.InfoCyan
import com.example.ui.theme.InfoCyanContainer
import com.example.ui.theme.LiveRed
import com.example.ui.theme.LiveRedContainer
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
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val PitchDark = Color(0xFF0B2218)
private val PitchStripe = Color(0xFF0E2B1E)
private val PitchLines = Color(0xFF396B52)

@Composable
fun LiveMatchVisualizerCard(
    bundle: MatchDetailBundle,
    isSwahili: Boolean,
    modifier: Modifier = Modifier
) {
    val fixture = bundle.fixture
    val status = FixtureStatus.safeFrom(fixture.status)
    val homeClean = fixture.homeTeam.replace("[SAMPLE]", "").trim()
    val awayClean = fixture.awayTeam.replace("[SAMPLE]", "").trim()

    val infinite = rememberInfiniteTransition(label = "live_match_visualizer")
    val pulsePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ball_wave_phase"
    )
    val radarAlpha by infinite.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_alpha"
    )
    val radarScale by infinite.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_scale"
    )

    val momentumHome = bundle.attackMomentumHomePct.coerceIn(20, 80)
    val animatedMomentum by animateFloatAsState(
        targetValue = momentumHome / 100f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "momentum_ratio"
    )

    Surface(
        color = MidnightCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (status == FixtureStatus.LIVE) NeonLime.copy(alpha = 0.55f) else MidnightBorder
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_match_visualizer_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(16.dp)) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .scale(radarScale)
                                .alpha(radarAlpha)
                                .clip(CircleShape)
                                .background(if (status == FixtureStatus.LIVE) LiveRed else NeonLime)
                        )
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (status == FixtureStatus.LIVE) LiveRed else NeonLime)
                        )
                    }
                    Text(
                        text = if (isSwahili) "KIONYESHI CHA MECHI MUBASHARA" else "LIVE MATCH VISUALIZER",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = NeonLime
                    )
                }
                Surface(
                    color = if (status == FixtureStatus.LIVE) LiveRedContainer else MidnightCardAlt,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (status == FixtureStatus.LIVE) "LIVE ${fixture.minuteLabel}" else status.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (status == FixtureStatus.LIVE) LiveRed else InfoCyan,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Animated Tactical Attack Pitch Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.9f)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, PitchLines, RoundedCornerShape(14.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    drawRect(PitchDark)
                    val stripes = 8
                    val stripeW = w / stripes
                    for (i in 0 until stripes step 2) {
                        drawRect(
                            color = PitchStripe,
                            topLeft = Offset(i * stripeW, 0f),
                            size = Size(stripeW, h)
                        )
                    }

                    // Highlight active attacking side with soft gradient glow
                    val attackRight = momentumHome >= 50
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = if (attackRight) {
                                listOf(Color.Transparent, NeonLime.copy(alpha = 0.16f))
                            } else {
                                listOf(InfoCyan.copy(alpha = 0.16f), Color.Transparent)
                            },
                            startX = if (attackRight) w * 0.48f else 0f,
                            endX = if (attackRight) w else w * 0.52f
                        )
                    )

                    // Pitch markings
                    val stroke = Stroke(width = 2.5f)
                    drawRect(PitchLines, topLeft = Offset(8f, 8f), size = Size(w - 16f, h - 16f), style = stroke)
                    drawLine(PitchLines, Offset(w / 2f, 8f), Offset(w / 2f, h - 8f), strokeWidth = 2.5f)
                    drawCircle(PitchLines, radius = h * 0.18f, center = Offset(w / 2f, h / 2f), style = stroke)
                    drawCircle(PitchLines, radius = 4f, center = Offset(w / 2f, h / 2f))

                    // Left & Right penalty boxes
                    val boxW = w * 0.15f
                    val boxH = h * 0.56f
                    val boxTop = (h - boxH) / 2f
                    drawRect(PitchLines, Offset(8f, boxTop), Size(boxW, boxH), style = stroke)
                    drawRect(PitchLines, Offset(w - 8f - boxW, boxTop), Size(boxW, boxH), style = stroke)

                    // Animated passing lane + moving ball along momentum curve
                    val angle = pulsePhase * 2.0 * PI
                    val baseCenter = if (status == FixtureStatus.FINISHED) 0.5f else animatedMomentum
                    val ballX = (w * (0.2f + 0.6f * baseCenter) + (cos(angle) * w * 0.14f)).toFloat()
                        .coerceIn(w * 0.12f, w * 0.88f)
                    val ballY = (h * 0.5f + sin(angle * 2.0) * h * 0.24f).toFloat()
                        .coerceIn(h * 0.15f, h * 0.85f)

                    // Trajectory arc
                    drawLine(
                        color = NeonLime.copy(alpha = 0.45f),
                        start = Offset(w * 0.5f, h * 0.5f),
                        end = Offset(ballX, ballY),
                        strokeWidth = 3f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), phase = pulsePhase * 36f)
                    )

                    // Pulsing ring + glowing ball
                    drawCircle(
                        color = NeonLime.copy(alpha = radarAlpha * 0.6f),
                        radius = 16f * radarScale,
                        center = Offset(ballX, ballY)
                    )
                    drawCircle(
                        color = NeonLime,
                        radius = 10f,
                        center = Offset(ballX, ballY)
                    )
                    drawCircle(
                        color = MidnightBlack,
                        radius = 4f,
                        center = Offset(ballX, ballY)
                    )
                }

                // Team directional badges
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(color = MidnightBlack.copy(alpha = 0.72f), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = "◀ ${homeClean.take(12)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = NeonLime,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    Surface(color = MidnightBlack.copy(alpha = 0.72f), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = "${awayClean.take(12)} ▶",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = InfoCyan,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Animated Latest Event Ticker Banner
            AnimatedContent(
                targetState = bundle.latestEventBanner,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith fadeOut() },
                label = "live_event_ticker"
            ) { eventBanner ->
                Surface(
                    color = MidnightCardAlt,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, tint = NeonLime, modifier = Modifier.size(16.dp))
                        Text(
                            text = eventBanner,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = CrispWhite,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Attack Momentum Bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$momentumHome%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = NeonLime
                    )
                    Text(
                        text = if (isSwahili) "Kasi ya Mashambulizi" else "Attack Momentum & Pressure",
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateText
                    )
                    Text(
                        text = "${100 - momentumHome}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = InfoCyan
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(InfoCyanContainer)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedMomentum)
                            .height(8.dp)
                            .background(NeonLime)
                    )
                }
            }
        }
    }
}

@Composable
fun SquadFormationVisualizerCard(
    bundle: MatchDetailBundle,
    isSwahili: Boolean,
    modifier: Modifier = Modifier
) {
    val homeClean = bundle.fixture.homeTeam.replace("[SAMPLE]", "").trim()
    val awayClean = bundle.fixture.awayTeam.replace("[SAMPLE]", "").trim()
    var squadMode by remember { mutableIntStateOf(0) } // 0=Both, 1=Home, 2=Away
    var animCycle by remember { mutableIntStateOf(1) }
    var visibleStep by remember { mutableIntStateOf(0) }
    var selectedPlayer by remember { mutableStateOf<SquadPlayerNode?>(null) }

    LaunchedEffect(animCycle, squadMode, bundle.fixture.id) {
        visibleStep = 0
        for (step in 1..12) {
            delay(45L)
            visibleStep = step
        }
    }

    val displayedPlayers = remember(bundle, squadMode) {
        when (squadMode) {
            1 -> bundle.homeSquad.map { it.copy(formationX = it.formationX * 2f) }
            2 -> bundle.awaySquad.map { it.copy(formationX = 1f - ((1f - it.formationX) * 2f)) }
            else -> bundle.homeSquad + bundle.awaySquad
        }
    }

    Surface(
        color = MidnightCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MidnightBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("squad_visualizer_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Groups, contentDescription = null, tint = InfoCyan, modifier = Modifier.size(18.dp))
                    Text(
                        text = if (isSwahili) "MPANGILIO WA KIKOSI" else "ANIMATED SQUAD FORMATION",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = CrispWhite
                    )
                }
                Surface(
                    onClick = { animCycle++ },
                    shape = RoundedCornerShape(10.dp),
                    color = NeonLimeContainer,
                    border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Replay squad animation", tint = NeonLime, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isSwahili) "Cheza" else "Animate",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = NeonLime
                        )
                    }
                }
            }

            // Formation Switcher Pills
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                val tabs = listOf(
                    0 to (if (isSwahili) "Zote (${bundle.homeFormation})" else "Both XI"),
                    1 to "${homeClean.take(10)} (${bundle.homeFormation})",
                    2 to "${awayClean.take(10)} (${bundle.awayFormation})"
                )
                tabs.forEach { (idx, label) ->
                    val active = squadMode == idx
                    Surface(
                        onClick = {
                            squadMode = idx
                            selectedPlayer = null
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (active) NeonLime else MidnightCardAlt,
                        border = BorderStroke(1.dp, if (active) NeonLime else MidnightBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (active) MidnightBlack else SlateText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 2D Tactical Pitch with Animated Squad Nodes
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.38f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, PitchLines, RoundedCornerShape(16.dp))
            ) {
                val boxW = maxWidth
                val boxH = maxHeight

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    drawRect(PitchDark)
                    val stripes = 10
                    val stripeW = w / stripes
                    for (i in 0 until stripes step 2) {
                        drawRect(PitchStripe, Offset(i * stripeW, 0f), Size(stripeW, h))
                    }
                    val stroke = Stroke(width = 2.5f)
                    drawRect(PitchLines, Offset(8f, 8f), Size(w - 16f, h - 16f), style = stroke)
                    drawLine(PitchLines, Offset(w / 2f, 8f), Offset(w / 2f, h - 8f), strokeWidth = 2.5f)
                    drawCircle(PitchLines, radius = h * 0.16f, center = Offset(w / 2f, h / 2f), style = stroke)
                    val penW = w * 0.14f
                    val penH = h * 0.54f
                    drawRect(PitchLines, Offset(8f, (h - penH) / 2f), Size(penW, penH), style = stroke)
                    drawRect(PitchLines, Offset(w - 8f - penW, (h - penH) / 2f), Size(penW, penH), style = stroke)
                }

                displayedPlayers.forEachIndexed { index, player ->
                    val revealThreshold = (index % 11) + 1
                    val revealed = visibleStep >= revealThreshold
                    val nodeScale by animateFloatAsState(
                        targetValue = if (revealed) 1f else 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "player_node_${index}"
                    )
                    val nodeSize = if (squadMode == 0) 24.dp else 30.dp
                    val xOffset = (boxW * player.formationX.coerceIn(0.06f, 0.94f)) - (nodeSize / 2)
                    val yOffset = (boxH * player.formationY.coerceIn(0.08f, 0.90f)) - (nodeSize / 2)
                    val badgeBg = if (player.isHome) NeonLime else InfoCyan
                    val isSelected = selectedPlayer == player

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .offset(x = xOffset, y = yOffset)
                            .scale(nodeScale)
                            .clickable { selectedPlayer = player }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(nodeSize)
                                    .clip(CircleShape)
                                    .background(if (isSelected) VipAmber else badgeBg)
                                    .border(
                                        width = if (isSelected || player.hasGoal) 2.dp else 1.dp,
                                        color = if (player.hasGoal) CrispWhite else MidnightBlack,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = player.jerseyNumber,
                                    fontSize = if (squadMode == 0) 9.sp else 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MidnightBlack
                                )
                            }
                            if (player.hasGoal || player.hasYellowCard || player.hasRedCard) {
                                Text(
                                    text = when {
                                        player.hasGoal -> "⚽"
                                        player.hasRedCard -> "🟥"
                                        else -> "🟨"
                                    },
                                    fontSize = 9.sp,
                                    modifier = Modifier.align(Alignment.TopEnd)
                                )
                            }
                        }
                        if (squadMode != 0 || player.hasGoal || player.isCaptain) {
                            Text(
                                text = player.name.substringAfterLast(" ").take(8),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CrispWhite,
                                maxLines = 1,
                                modifier = Modifier
                                    .background(MidnightBlack.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 3.dp)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = selectedPlayer != null) {
                selectedPlayer?.let { p ->
                    Surface(
                        color = MidnightCardAlt,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, (if (p.isHome) NeonLime else InfoCyan).copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "#${p.jerseyNumber} ${p.name} (${p.positionRole})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = CrispWhite
                            )
                            Text(
                                text = buildString {
                                    append(if (p.isHome) homeClean else awayClean)
                                    if (p.isCaptain) append(" • C")
                                    if (p.hasGoal) append(" • ⚽ Goal")
                                    if (p.hasYellowCard) append(" • 🟨 Yellow")
                                    if (p.hasRedCard) append(" • 🟥 Red")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (p.isHome) NeonLime else InfoCyan
                            )
                        }
                    }
                }
            }

            if (bundle.fixture.lineupsSummary.isNotBlank()) {
                Text(
                    text = bundle.fixture.lineupsSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateText,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun PreMatchH2HAnalysisCard(
    bundle: MatchDetailBundle,
    isSwahili: Boolean,
    modifier: Modifier = Modifier
) {
    val analysis = bundle.preMatchAnalysis ?: return
    val homeClean = bundle.fixture.homeTeam.replace("[SAMPLE]", "").trim()
    val awayClean = bundle.fixture.awayTeam.replace("[SAMPLE]", "").trim()

    Surface(
        color = MidnightCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MidnightBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("pre_match_h2h_card")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Analytics, contentDescription = null, tint = NeonLime, modifier = Modifier.size(18.dp))
                    Text(
                        text = if (isSwahili) "UCHAMBUZI WA TAKWIMU & H2H" else "PRE-MATCH STATISTICAL & H2H",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = NeonLime
                    )
                }
                Text(
                    text = analysis.dataSourceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Win / Draw / Away Probability Gauge
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${homeClean.take(10)} ${analysis.homeWinPct}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = NeonLime)
                    Text("${if (isSwahili) "Sare" else "Draw"} ${analysis.drawPct}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = VipAmber)
                    Text("${awayClean.take(10)} ${analysis.awayWinPct}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = InfoCyan)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .weight(analysis.homeWinPct.coerceAtLeast(5).toFloat())
                            .height(10.dp)
                            .background(NeonLime)
                    )
                    Box(
                        modifier = Modifier
                            .weight(analysis.drawPct.coerceAtLeast(5).toFloat())
                            .height(10.dp)
                            .background(VipAmber)
                    )
                    Box(
                        modifier = Modifier
                            .weight(analysis.awayWinPct.coerceAtLeast(5).toFloat())
                            .height(10.dp)
                            .background(InfoCyan)
                    )
                }
            }

            // Recent Form (Last 5 W/D/L) & Rank / Goals Avg
            Surface(color = MidnightCardAlt, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isSwahili) "Mwenendo wa Mechi 5 Zilizopita & Takwimu" else "Last 5 Form Guide & Season Metrics",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = CrispWhite
                    )
                    FormRow(
                        teamLabel = "${homeClean.take(12)} (${analysis.homeRankLabel})",
                        form = analysis.homeForm,
                        goalsAvg = "${analysis.homeGoalsAvg} G/M",
                        accent = NeonLime
                    )
                    FormRow(
                        teamLabel = "${awayClean.take(12)} (${analysis.awayRankLabel})",
                        form = analysis.awayForm,
                        goalsAvg = "${analysis.awayGoalsAvg} G/M",
                        accent = InfoCyan
                    )
                }
            }

            // H2H Summary Counters + Goal Trends
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                H2HMetricPill("${analysis.h2hHomeWins}", "${homeClean.take(8)} W", NeonLime, Modifier.weight(1f))
                H2HMetricPill("${analysis.h2hDraws}", if (isSwahili) "Sare" else "Draws", VipAmber, Modifier.weight(1f))
                H2HMetricPill("${analysis.h2hAwayWins}", "${awayClean.take(8)} W", InfoCyan, Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                H2HMetricPill(analysis.avgGoalsPerMatch, if (isSwahili) "Wastani Magoli" else "Avg Goals", CrispWhite, Modifier.weight(1f))
                H2HMetricPill("${analysis.bttsPct}%", "BTTS Yes", WonEmerald, Modifier.weight(1f))
                H2HMetricPill("${analysis.over25Pct}%", "Over 2.5", NeonLime, Modifier.weight(1f))
            }

            // Recent H2H Meetings List
            if (analysis.recentH2H.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.History, contentDescription = null, tint = InfoCyan, modifier = Modifier.size(16.dp))
                        Text(
                            text = if (isSwahili) "Mechi za H2H Zilizopita" else "Recent Head-to-Head Meetings",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = CrispWhite
                        )
                    }
                    analysis.recentH2H.take(5).forEach { meeting ->
                        Surface(
                            color = MidnightCardAlt,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MidnightBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = meeting.dateLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SlateText,
                                    modifier = Modifier.width(74.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${meeting.homeTeam.take(10)} vs ${meeting.awayTeam.take(10)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CrispWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                                )
                                Surface(color = MidnightCard, shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        text = "${meeting.homeScore} - ${meeting.awayScore}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = NeonLime,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Statistical Tactical Edge Banner
            Surface(
                color = NeonLimeContainer.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.SportsSoccer, contentDescription = null, tint = NeonLime, modifier = Modifier.size(16.dp))
                    Text(
                        text = analysis.tacticalEdgeNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = CrispWhite
                    )
                }
            }
        }
    }
}

@Composable
private fun FormRow(
    teamLabel: String,
    form: List<String>,
    goalsAvg: String,
    accent: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = teamLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            form.forEach { res ->
                val (bg, fg) = when (res) {
                    "W" -> WonEmeraldContainer to WonEmerald
                    "D" -> VipAmberContainer to VipAmber
                    else -> LiveRedContainer to LiveRed
                }
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(bg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(res, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = fg)
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(goalsAvg, style = MaterialTheme.typography.labelSmall, color = SlateText)
        }
    }
}

@Composable
private fun H2HMetricPill(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MidnightCardAlt,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MidnightBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = accent)
            Text(label, style = MaterialTheme.typography.labelSmall, color = SlateText, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}
