package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SubScreenTopBar
import com.example.ui.i18n.AppLanguage
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.MidnightElevated
import com.example.ui.theme.NeonLime
import com.example.ui.theme.NeonLimeContainer
import com.example.ui.theme.SlateText
import com.example.viewmodel.DyfootViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun NotificationsScreen(
    viewModel: DyfootViewModel,
    onBack: () -> Unit
) {
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val unread by viewModel.unreadNotificationsCount.collectAsStateWithLifecycle()
    val favoriteMatches by viewModel.favoriteFixtures.collectAsStateWithLifecycle()
    val fcmToken by viewModel.fcmToken.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val isSw = appLanguage == AppLanguage.SWAHILI
    val dateFmt = SimpleDateFormat("dd MMM • HH:mm", Locale.US)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBlack)
            .navigationBarsPadding()
    ) {
        SubScreenTopBar(
            title = if (isSw) "Taarifa ($unread)" else "Notifications ($unread)",
            onBack = onBack,
            trailing = {
                if (unread > 0) {
                    TextButton(onClick = { viewModel.markAllNotificationsRead() }) {
                        Icon(Icons.Filled.DoneAll, contentDescription = null, tint = NeonLime)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isSw) "Soma zote" else "Mark all", color = NeonLime)
                    }
                }
            }
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MidnightElevated),
            border = BorderStroke(
                1.dp,
                if (favoriteMatches.isNotEmpty()) NeonLime.copy(alpha = 0.55f) else MidnightBorder
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("fcm_favorites_status_card")
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = NeonLimeContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (favoriteMatches.isNotEmpty()) Icons.Filled.NotificationsActive else Icons.Filled.Star,
                        contentDescription = null,
                        tint = NeonLime,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isSw) "FCM Mechi Pendwa" else "FCM Favourite Match Alerts",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = CrispWhite
                        )
                        Surface(
                            color = if (fcmToken.isNotBlank()) NeonLimeContainer else MidnightCard,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (fcmToken.isNotBlank()) {
                                    if (isSw) "FCM TAYARI" else "FCM ACTIVE"
                                } else {
                                    if (isSw) "INAPANGA" else "SYNCING"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (fcmToken.isNotBlank()) NeonLime else SlateText,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = if (isSw) {
                            "${favoriteMatches.size} mechi pendwa zimesajiliwa kwa taarifa za magoli na matokeo ya mwisho."
                        } else {
                            "${favoriteMatches.size} favourite match(es) subscribed for instant goal & final score push alerts."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateText,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        if (notifications.isEmpty()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                EmptyStateCard(
                    icon = Icons.Filled.Notifications,
                    title = if (isSw) "Hakuna Taarifa Bado" else "No Notifications Yet",
                    subtitle = if (isSw) {
                        "Taarifa za magoli ya mechi pendwa, matokeo ya mwisho, VIP na malipo zitaonekana hapa."
                    } else {
                        "Goal alerts, final scores for favourite matches, VIP status, payments & announcements appear here."
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(notifications, key = { it.id }) { item ->
                    Card(
                        onClick = { if (!item.read) viewModel.markNotificationRead(item.id) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.read) MidnightCard else NeonLimeContainer.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(1.dp, if (item.read) MidnightBorder else NeonLime.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(color = NeonLimeContainer, shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        text = item.category,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeonLime,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                item.createdAt?.toDate()?.let { d ->
                                    Text(dateFmt.format(d), style = MaterialTheme.typography.labelSmall, color = SlateText)
                                }
                            }
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = CrispWhite,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            Text(
                                text = item.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateText,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
