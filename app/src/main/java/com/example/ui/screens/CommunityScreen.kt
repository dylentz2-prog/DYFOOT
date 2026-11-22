package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.CommunityGroup
import com.example.data.model.CommunityStatus
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SegmentedPillSwitcher
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.LiveRed
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightCard
import com.example.ui.theme.MidnightCardAlt
import com.example.ui.theme.MidnightElevated
import com.example.ui.theme.NeonLime
import com.example.ui.theme.NeonLimeContainer
import com.example.ui.theme.SlateText
import com.example.ui.theme.VipAmber
import com.example.ui.theme.VipAmberContainer
import com.example.ui.i18n.dyfootStrings
import com.example.viewmodel.DyfootViewModel
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun CommunityScreen(
    viewModel: DyfootViewModel
) {
    val myGroups by viewModel.myGroups.collectAsStateWithLifecycle()
    val publicGroups by viewModel.publicGroups.collectAsStateWithLifecycle()
    val blockedUsers by viewModel.blockedUsers.collectAsStateWithLifecycle()
    val statuses by viewModel.activeStatuses.collectAsStateWithLifecycle()
    val language by viewModel.appLanguage.collectAsStateWithLifecycle()
    val copy = remember(language) { dyfootStrings(language) }

    var activeGroup by remember { mutableStateOf<CommunityGroup?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0=Chats, 1=Groups, 2=Status
    var showCreateGroupDialog by remember { mutableStateOf(false) }

    var usernameQuery by remember { mutableStateOf("") }
    var matchedUsers by remember { mutableStateOf<List<UserProfile>>(emptyList()) }

    LaunchedEffect(usernameQuery) {
        if (usernameQuery.trim().isNotEmpty()) {
            matchedUsers = viewModel.searchUsers(usernameQuery.trim())
                .filter { it.userId != viewModel.currentUserId }
        } else {
            matchedUsers = emptyList()
        }
    }

    if (activeGroup != null) {
        BackHandler { activeGroup = null }
        GroupChatRoom(
            group = activeGroup!!,
            viewModel = viewModel,
            blockedUids = blockedUsers.map { it.blockedUid }.toSet(),
            onBack = { activeGroup = null }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Matching Screenshots 1 & 2
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    text = copy.communityTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = CrispWhite
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = copy.communitySub,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SlateText
                )
                Spacer(modifier = Modifier.height(14.dp))

                SegmentedPillSwitcher(
                    options = listOf(
                        copy.chatsTab to null,
                        copy.groupsTab to null,
                        copy.statusTab to null
                    ),
                    selectedIndex = selectedTab,
                    onSelect = { selectedTab = it }
                )
            }

            if (selectedTab < 2) {
                // Inline "Find a fan by @username" pill input matching Screenshot 1
                OutlinedTextField(
                    value = usernameQuery,
                    onValueChange = { usernameQuery = it },
                    placeholder = { Text(copy.findFanHint, color = SlateText) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Find a fan",
                            tint = SlateText
                        )
                    },
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
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("find_fan_input")
                )
            }

            when (selectedTab) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 112.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (matchedUsers.isNotEmpty()) {
                            item {
                                Text(
                                    text = "DYFOOT MEMBERS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonLime,
                                    letterSpacing = 0.8.sp
                                )
                            }
                            items(matchedUsers, key = { "fan_${it.userId}" }) { user ->
                                Surface(
                                    color = MidnightCard,
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, MidnightBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(user.displayName, style = MaterialTheme.typography.titleSmall, color = CrispWhite)
                                            Text("@${user.username} • ${user.parsedRole().label}", style = MaterialTheme.typography.bodySmall, color = SlateText)
                                        }
                                        Button(
                                            onClick = { viewModel.openDirectChat(user) { dm -> activeGroup = dm } },
                                            colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = MidnightBlack),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Message", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        if (myGroups.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Filled.Groups,
                                    title = "No Conversations Yet",
                                    subtitle = "Search a fan by @username above, join a public group, or tap + to start a new group."
                                )
                            }
                        } else {
                            item {
                                Surface(
                                    color = MidnightCard,
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.dp, MidnightBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column {
                                        myGroups.forEachIndexed { idx, group ->
                                            CommunityGroupedChatRow(
                                                group = group,
                                                onClick = { activeGroup = group }
                                            )
                                            if (idx < myGroups.lastIndex) {
                                                HorizontalDivider(
                                                    color = MidnightBorder.copy(alpha = 0.7f),
                                                    modifier = Modifier.padding(horizontal = 16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    val roomsFiltered = publicGroups.filter {
                        usernameQuery.isBlank() ||
                            it.title.contains(usernameQuery.trim(), ignoreCase = true) ||
                            it.description.contains(usernameQuery.trim(), ignoreCase = true)
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 112.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (roomsFiltered.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Filled.Groups,
                                    title = "No Public Groups",
                                    subtitle = "Tap + to create the first public football community group."
                                )
                            }
                        } else {
                            item {
                                Surface(
                                    color = MidnightCard,
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.dp, MidnightBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column {
                                        roomsFiltered.forEachIndexed { idx, group ->
                                            val isJoined = viewModel.currentUserId in group.memberUids
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        if (isJoined) activeGroup = group
                                                        else viewModel.joinPublicGroup(group) { activeGroup = group }
                                                    }
                                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(46.dp)
                                                        .clip(CircleShape)
                                                        .background(MidnightCardAlt),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = group.title.take(2).uppercase(),
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = NeonLime
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = group.title,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = CrispWhite,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = group.description.ifBlank { "${group.memberUids.size} members" },
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = SlateText,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    color = if (isJoined) NeonLimeContainer else NeonLime,
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Text(
                                                        text = if (isJoined) "Open" else "Join",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isJoined) NeonLime else MidnightBlack,
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                    )
                                                }
                                            }
                                            if (idx < roomsFiltered.lastIndex) {
                                                HorizontalDivider(
                                                    color = MidnightBorder.copy(alpha = 0.7f),
                                                    modifier = Modifier.padding(horizontal = 16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    MatchdayStatusTab(
                        statuses = statuses,
                        currentUserId = viewModel.currentUserId,
                        isAdminOrOwner = viewModel.isAdminOrOwner(),
                        onPostStatus = { text, color -> viewModel.postStatus(text, color) },
                        onDeleteStatus = { id -> viewModel.deleteStatus(id) }
                    )
                }
            }
        }

        if (selectedTab < 2) {
            FloatingActionButton(
                onClick = { showCreateGroupDialog = true },
                containerColor = NeonLime,
                contentColor = MidnightBlack,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 104.dp)
                    .testTag("create_group_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Create group")
            }
        }
    }

    if (showCreateGroupDialog) {
        var title by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var isPublic by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            containerColor = MidnightCard,
            title = { Text("Create Community Group", color = CrispWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Group Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonLime,
                            unfocusedBorderColor = MidnightBorder,
                            focusedTextColor = CrispWhite,
                            unfocusedTextColor = CrispWhite
                        )
                    )
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Description") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonLime,
                            unfocusedBorderColor = MidnightBorder,
                            focusedTextColor = CrispWhite,
                            unfocusedTextColor = CrispWhite
                        )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isPublic, onCheckedChange = { isPublic = it })
                        Text("Public discoverable group", color = CrispWhite, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createGroup(title, desc, isPublic) { created ->
                            showCreateGroupDialog = false
                            activeGroup = created
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = MidnightBlack)
                ) {
                    Text("Create", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) {
                    Text("Cancel", color = SlateText)
                }
            }
        )
    }
}

@Composable
private fun CommunityGroupedChatRow(
    group: CommunityGroup,
    onClick: () -> Unit
) {
    val words = group.title.trim().split(" ").filter { it.isNotBlank() }
    val initials = when {
        words.size >= 2 -> "${words[0].first().uppercaseChar()}${words[1].first().uppercaseChar()}"
        group.title.length >= 2 -> group.title.take(2).uppercase()
        else -> "DY"
    }
    val timeLabel = remember(group.updatedAt) {
        val ts = group.updatedAt?.toDate()?.time ?: return@remember "Recent"
        val diff = (System.currentTimeMillis() - ts).coerceAtLeast(0L)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        when {
            diff < 60_000L -> "Just now"
            hours < 1 -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
            hours < 24 -> "${hours}h ago"
            days == 1L -> "1 day ago"
            else -> "$days days ago"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MidnightCardAlt),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = NeonLime
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CrispWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Filled.Groups,
                    contentDescription = null,
                    tint = SlateText,
                    modifier = Modifier.size(16.dp)
                )
                if (group.publicRoom && !group.directMessage) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = VipAmberContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, VipAmber.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "PUBLIC",
                            style = MaterialTheme.typography.labelSmall,
                            color = VipAmber,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = group.lastMessage.ifBlank { "No messages yet" },
                style = MaterialTheme.typography.bodySmall,
                color = SlateText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = timeLabel,
            style = MaterialTheme.typography.labelSmall,
            color = SlateText
        )
    }
}

@Composable
private fun MatchdayStatusTab(
    statuses: List<CommunityStatus>,
    currentUserId: String,
    isAdminOrOwner: Boolean,
    onPostStatus: (String, String) -> Unit,
    onDeleteStatus: (String) -> Unit
) {
    var draft by remember { mutableStateOf("") }
    var selectedTheme by remember { mutableStateOf("LIME") }

    val themeSwatches = listOf(
        "LIME" to NeonLime,
        "MIDNIGHT" to Color(0xFF10182A),
        "EMERALD" to Color(0xFF064E2B),
        "CRIMSON" to Color(0xFF992B1D)
    )
    val activeBg = themeSwatches.firstOrNull { it.first == selectedTheme }?.second ?: NeonLime
    val isDarkTheme = selectedTheme != "LIME"
    val composerFg = if (isDarkTheme) CrispWhite else MidnightBlack

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Composer Card Matching Screenshot 2
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = activeBg),
                border = if (isDarkTheme) BorderStroke(1.dp, MidnightBorder) else null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Surface(
                        color = if (isDarkTheme) Color.Black.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.06f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isDarkTheme) CrispWhite.copy(alpha = 0.18f) else MidnightBlack.copy(alpha = 0.14f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextField(
                            value = draft,
                            onValueChange = { if (it.length <= 240) draft = it },
                            placeholder = {
                                Text(
                                    text = "What's your matchday mood?",
                                    color = composerFg.copy(alpha = 0.6f)
                                )
                            },
                            minLines = 3,
                            maxLines = 4,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = composerFg,
                                unfocusedTextColor = composerFg,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = composerFg
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("status_mood_input")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            themeSwatches.forEach { (key, swatchColor) ->
                                val selected = selectedTheme == key
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(swatchColor)
                                        .border(
                                            width = if (selected) 2.5.dp else 1.dp,
                                            color = if (selected) CrispWhite else CrispWhite.copy(alpha = 0.3f),
                                            shape = CircleShape
                                        )
                                        .clickable { selectedTheme = key }
                                        .testTag("status_swatch_${key.lowercase()}")
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (draft.isNotBlank()) {
                                    onPostStatus(draft, selectedTheme)
                                    draft = ""
                                }
                            },
                            enabled = draft.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDarkTheme) NeonLime else MidnightBlack.copy(alpha = 0.38f),
                                contentColor = if (isDarkTheme) MidnightBlack else CrispWhite,
                                disabledContainerColor = if (isDarkTheme) NeonLime.copy(alpha = 0.35f) else MidnightBlack.copy(alpha = 0.25f),
                                disabledContentColor = if (isDarkTheme) MidnightBlack.copy(alpha = 0.6f) else CrispWhite.copy(alpha = 0.7f)
                            ),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("post_status_btn")
                        ) {
                            Text("Post status", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (statuses.isEmpty()) {
            // Exact Screenshot 2 Empty State Card
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MidnightCard),
                    border = BorderStroke(1.dp, MidnightBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 42.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(NeonLimeContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.RadioButtonChecked,
                                contentDescription = null,
                                tint = NeonLime,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No statuses right now",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = CrispWhite
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Statuses disappear after 24 hours.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateText
                        )
                    }
                }
            }
        } else {
            items(statuses, key = { it.id }) { status ->
                val cardBg = themeSwatches.firstOrNull { it.first == status.themeColor }?.second ?: MidnightCard
                val darkCard = status.themeColor != "LIME"
                val fg = if (darkCard) CrispWhite else MidnightBlack
                val hoursRemaining = ((status.expiresAtEpochMs - System.currentTimeMillis()) / 3_600_000L).coerceAtLeast(1L)

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = if (darkCard) BorderStroke(1.dp, MidnightBorder) else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = status.authorName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = fg
                                )
                                Text(
                                    text = "Disappears in ${hoursRemaining}h",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = fg.copy(alpha = 0.72f)
                                )
                            }
                            if (status.authorId == currentUserId || isAdminOrOwner) {
                                IconButton(onClick = { onDeleteStatus(status.id) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Delete status",
                                        tint = fg.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = status.text,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = fg
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupChatRoom(
    group: CommunityGroup,
    viewModel: DyfootViewModel,
    blockedUids: Set<String>,
    onBack: () -> Unit
) {
    val messages by viewModel.observeGroupMessages(group)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var draft by remember { mutableStateOf("") }
    var attachedPhotoUri by remember { mutableStateOf("") }
    var reportTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.US) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            attachedPhotoUri = uri.toString()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBlack)
            .padding(bottom = 84.dp)
    ) {
        Surface(color = MidnightElevated, border = BorderStroke(1.dp, MidnightBorder)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CrispWhite)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(group.title, style = MaterialTheme.typography.titleMedium, color = CrispWhite, maxLines = 1)
                    Text("${group.memberUids.size} participants", style = MaterialTheme.typography.bodySmall, color = NeonLime)
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val visibleMessages = messages.filter { it.senderId !in blockedUids }
            items(visibleMessages, key = { it.id }) { msg ->
                val isMine = msg.senderId == viewModel.currentUserId
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
                ) {
                    Surface(
                        color = if (isMine) NeonLimeContainer else MidnightCard,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, if (isMine) NeonLime.copy(alpha = 0.5f) else MidnightBorder)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(msg.senderName, style = MaterialTheme.typography.labelSmall, color = NeonLime)
                                msg.createdAt?.toDate()?.let { d ->
                                    Text(timeFmt.format(d), style = MaterialTheme.typography.labelSmall, color = SlateText)
                                }
                                if (!isMine) {
                                    Icon(
                                        imageVector = Icons.Filled.Flag,
                                        contentDescription = "Report message",
                                        tint = SlateText,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { reportTarget = "MESSAGE" to "${msg.id}: ${msg.text.take(80)}" }
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.Block,
                                        contentDescription = "Block user",
                                        tint = LiveRed,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { viewModel.blockUser(msg.senderId, msg.senderName) }
                                    )
                                }
                            }
                            if (msg.imageUrl.startsWith("http") || msg.imageUrl.startsWith("content://")) {
                                Spacer(modifier = Modifier.height(6.dp))
                                AsyncImage(
                                    model = msg.imageUrl,
                                    contentDescription = "Shared media",
                                    modifier = Modifier
                                        .size(160.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(msg.text, style = MaterialTheme.typography.bodyMedium, color = CrispWhite)
                        }
                    }
                }
            }
        }

        if (attachedPhotoUri.isNotBlank()) {
            Text(
                text = "Media attached (tap Send to post)",
                style = MaterialTheme.typography.labelSmall,
                color = NeonLime,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MidnightElevated)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Icon(Icons.Filled.Image, contentDescription = "Attach image", tint = SlateText)
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Message...") },
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
                if (draft.isNotBlank()) {
                    viewModel.sendMessage(group, draft, attachedPhotoUri)
                    draft = ""
                    attachedPhotoUri = ""
                }
            }) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = NeonLime)
            }
        }
    }

    reportTarget?.let { (type, id) ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reportTarget = null },
            containerColor = MidnightCard,
            title = { Text("Report Content", color = CrispWhite) },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (spam, abuse, scam)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonLime,
                        unfocusedBorderColor = MidnightBorder,
                        focusedTextColor = CrispWhite,
                        unfocusedTextColor = CrispWhite
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.submitReport(type, id, reason)
                        reportTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LiveRed, contentColor = CrispWhite)
                ) {
                    Text("Submit Report")
                }
            },
            dismissButton = {
                TextButton(onClick = { reportTarget = null }) {
                    Text("Cancel", color = SlateText)
                }
            }
        )
    }
}
