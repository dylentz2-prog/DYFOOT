package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.InfoCyan
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.NeonLime
import com.example.ui.theme.NeonLimeContainer
import com.example.ui.theme.SlateText
import kotlinx.coroutines.delay

@Composable
fun Dyfoot3DSplashScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    var targetProgress by remember { mutableFloatStateOf(0.08f) }
    var stageIndex by remember { mutableIntStateOf(0) }

    val progress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "splash_progress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "splash_3d_motion")
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_angle"
    )
    val tiltY by infiniteTransition.animateFloat(
        initialValue = -11f,
        targetValue = 11f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tilt_y"
    )
    val tiltX by infiniteTransition.animateFloat(
        initialValue = 5f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tilt_x"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    LaunchedEffect(Unit) {
        delay(220)
        targetProgress = 0.38f
        stageIndex = 1
        delay(550)
        targetProgress = 0.72f
        stageIndex = 2
        delay(650)
        targetProgress = 1.0f
        stageIndex = 3
        delay(580)
        onFinish()
    }

    val stageLabels = listOf(
        "INITIALIZING 3D ARENA...",
        "SYNCING WORLD LIVE SCORES...",
        "LOADING VIP & SWAHILI HUB...",
        "READY TO KICK OFF"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightBlack)
            .testTag("dyfoot_3d_splash")
    ) {
        // Atmospheric full-bleed 3D stadium backdrop with parallax depth
        Image(
            painter = painterResource(id = R.drawable.img_splash_3d),
            contentDescription = "DYFOOT 3D Stadium Arena",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = pulseScale * 1.06f
                    scaleY = pulseScale * 1.06f
                    rotationY = tiltY * 0.45f
                    rotationX = tiltX * 0.45f
                    cameraDistance = 14f * density
                }
                .alpha(0.58f)
        )

        // Vignette gradient keeping center crisp and edges deep midnight black
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MidnightBlack.copy(alpha = 0.72f),
                            MidnightBlack.copy(alpha = 0.28f),
                            MidnightBlack.copy(alpha = 0.88f),
                            MidnightBlack
                        )
                    )
                )
        )

        // Top Skip + Live Badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = NeonLimeContainer.copy(alpha = 0.85f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.6f))
            ) {
                Text(
                    text = "WORLD SPORTS 3D",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = NeonLime,
                    letterSpacing = 1.1.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
            TextButton(
                onClick = onFinish,
                modifier = Modifier.testTag("splash_skip_btn")
            ) {
                Text(
                    text = "Skip ->",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = SlateText
                )
            }
        }

        // Center 3D Tilting Sphere Emblem with Neon Orbital Rings
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(240.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)

                    // Outer glowing telemetry ring
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(
                                NeonLime,
                                Color.Transparent,
                                InfoCyan.copy(alpha = 0.8f),
                                Color.Transparent,
                                NeonLime
                            ),
                            center = center
                        ),
                        radius = radius - 4.dp.toPx(),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Rotating arc segment
                    drawArc(
                        color = NeonLime,
                        startAngle = orbitAngle,
                        sweepAngle = 75f,
                        useCenter = false,
                        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = InfoCyan,
                        startAngle = (orbitAngle + 180f) % 360f,
                        sweepAngle = 55f,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Floating 3D Perspective Sphere Card
                Surface(
                    shape = CircleShape,
                    color = MidnightCard,
                    border = BorderStroke(2.dp, NeonLime.copy(alpha = 0.85f)),
                    shadowElevation = 24.dp,
                    modifier = Modifier
                        .size(182.dp)
                        .graphicsLayer {
                            cameraDistance = 12f * density
                            rotationY = tiltY
                            rotationX = tiltX
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.img_splash_3d),
                            contentDescription = "DYFOOT 3D Emblem",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            Color.Transparent,
                                            MidnightBlack.copy(alpha = 0.45f)
                                        )
                                    )
                                )
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MidnightBlack.copy(alpha = 0.78f),
                            border = BorderStroke(1.dp, NeonLime.copy(alpha = 0.85f))
                        ) {
                            Text(
                                text = "DYFOOT",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                color = NeonLime,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = CrispWhite, fontWeight = FontWeight.Black)) {
                        append("DY")
                    }
                    withStyle(SpanStyle(color = NeonLime, fontWeight = FontWeight.Black)) {
                        append("FOOT")
                    }
                },
                fontSize = 36.sp,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "MORE THAN FOOTBALL • ZAIDI YA SOKA",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = NeonLime,
                letterSpacing = 1.4.sp
            )
        }

        // Bottom Animated Progress Telemetry
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stageLabels[stageIndex.coerceIn(0, stageLabels.lastIndex)],
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = CrispWhite,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = NeonLime
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                color = NeonLime,
                trackColor = MidnightCard,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(NeonLime)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Live Scores • VIP Tips • Watch • Swahili & English",
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateText
                )
            }
        }
    }
}
