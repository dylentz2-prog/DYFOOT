package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SubScreenTopBar
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.LiveRed
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.NeonLime
import com.example.ui.theme.SlateText
import com.example.ui.theme.VipAmber
import com.example.ui.theme.VipAmberContainer
import com.example.viewmodel.DyfootViewModel

@Composable
fun LiveArenaScreen(
    viewModel: DyfootViewModel,
    onBack: () -> Unit,
    onUpgradeVipClick: () -> Unit
) {
    val fixtures by viewModel.combinedFixtures.collectAsStateWithLifecycle()
    val scheduled = fixtures.filter { it.streamScheduled }
    val hasVip = viewModel.hasActiveVipAccess()

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
                            Surface(color = VipAmber, shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    text = "COMING SOON",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MidnightBlack,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("DYFOOT Live Arena", style = MaterialTheme.typography.headlineSmall, color = CrispWhite)
                            Text(
                                "Licensed live match streaming requires official broadcasting rights & a connected DRM streaming provider.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateText
                            )
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
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MidnightCard),
                        border = BorderStroke(1.dp, MidnightBorder),
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
                            Surface(color = VipAmberContainer, shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    text = "COMING SOON",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VipAmber,
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
