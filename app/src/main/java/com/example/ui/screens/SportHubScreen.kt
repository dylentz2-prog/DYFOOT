package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.SegmentedPillSwitcher
import com.example.ui.i18n.dyfootStrings
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.NeonLime
import com.example.ui.theme.SlateText
import com.example.viewmodel.DyfootViewModel

@Composable
fun SportHubScreen(
    viewModel: DyfootViewModel,
    initialTab: Int = 0,
    onNavigateVip: () -> Unit = {},
    onNavigateTips: () -> Unit = {},
    onNavigateLiveScores: () -> Unit = {},
    onNavigateLiveArena: () -> Unit = {}
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    val isRefreshingNews by viewModel.isRefreshingNews.collectAsStateWithLifecycle()
    val language by viewModel.appLanguage.collectAsStateWithLifecycle()
    val copy = remember(language) { dyfootStrings(language) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBlack)
    ) {
        // Header Matching Screenshots 8 & 9
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = copy.sportHubTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = CrispWhite
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = copy.sportHubSub,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SlateText
                    )
                }
                if (selectedTab == 0) {
                    IconButton(onClick = { viewModel.refreshRssNews() }) {
                        if (isRefreshingNews) {
                            CircularProgressIndicator(
                                color = NeonLime,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = copy.refresh,
                                tint = NeonLime
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            SegmentedPillSwitcher(
                options = listOf(
                    copy.newsTab to Icons.Filled.Newspaper,
                    copy.podcastsTab to Icons.Filled.Podcasts
                ),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it }
            )
        }

        when (selectedTab) {
            0 -> NewsFeedTab(
                viewModel = viewModel,
                onNavigateVip = onNavigateVip,
                onNavigateTips = onNavigateTips,
                onNavigateLiveScores = onNavigateLiveScores,
                onNavigateLiveArena = onNavigateLiveArena,
                onNavigatePodcasts = { selectedTab = 1 }
            )
            else -> PodcastsFeedTab(viewModel = viewModel)
        }
    }
}
